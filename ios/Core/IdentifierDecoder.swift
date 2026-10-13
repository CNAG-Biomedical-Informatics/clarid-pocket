import Foundation

/// Auto mode accepts exactly one strict interpretation and never searches subject widths.
public struct IdentifierDecoder {
    private let reference: ReferenceData
    public init(reference: ReferenceData) { self.reference = reference }

    public func decode(_ text: String, settings: DecodeSettings = DecodeSettings()) throws -> DecodedIdentifier {
        try settings.validate()
        guard !text.isEmpty, text.utf16.count <= 1024,
              !text.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) || CharacterSet.controlCharacters.contains($0) }) else {
            throw invalid("Expected one ClarID identifier without whitespace, up to 1024 characters.")
        }
        let entities: [IdentifierEntity] = settings.entity == .auto ? [.biosample, .subject] : [settings.entity]
        let formats: [IdentifierFormat] = settings.format == .auto ? [.human, .stub] : [settings.format]
        var successes: [DecodedIdentifier] = []
        var failures: [(IdentifierEntity, IdentifierFormat, ClarIDError)] = []
        for entity in entities {
            for format in formats {
                do {
                    let record: IdentifierRecord
                    if entity == .biosample {
                        record = .biosample(try format == .human ? humanBiosample(text, settings.humanSubjectWidth) : stubBiosample(text, settings.stubSubjectWidth))
                    } else {
                        record = .subject(try format == .human ? humanSubject(text, settings.humanSubjectWidth) : stubSubject(text, settings.stubSubjectWidth))
                    }
                    successes.append(DecodedIdentifier(record: record, format: format))
                } catch let error as ClarIDError { failures.append((entity, format, error)) }
            }
        }
        if successes.count == 1 { return successes[0] }
        if successes.count > 1 { throw invalid("This identifier has multiple interpretations. Choose its entity and format in Settings.") }
        let likelyFormat: IdentifierFormat = text.contains("-") ? .human : .stub
        let likelyEntity: IdentifierEntity = text.filter { $0 == "-" }.count == 5 ? .subject : .biosample
        let detail = failures.first { $0.0 == likelyEntity && $0.1 == likelyFormat } ?? failures[0]
        throw invalid("No supported identifier matched. " + detail.2.message)
    }

    private func humanBiosample(_ text: String, _ width: Int) throws -> Biosample {
        let fields = text.components(separatedBy: "-")
        guard (9...11).contains(fields.count) else { throw invalid("A human biosample identifier requires 9 fields and optional B/R suffixes.") }
        let sid = try decimalSubject(fields[2], width)
        var tail = Array(fields.dropFirst(9))
        var batch: Int?, replicate: Int?
        if let last = tail.last, matches(last, #"R[0-9]{2}"#) { replicate = Int(last.dropFirst()); tail.removeLast() }
        if let last = tail.last, matches(last, #"B[0-9]{2}"#) { batch = Int(last.dropFirst()); tail.removeLast() }
        guard tail.isEmpty else { throw invalid("Optional suffixes must be B followed by two digits, then R followed by two digits.") }
        let conditions = try humanConditions(fields[6])
        try checkDuration(fields[8])
        let book = reference.codebook
        return try Biosample(project: book.project.code(fields[0]), species: book.species.code(fields[1]), subjectID: sid,
                             tissue: book.tissue.code(fields[3]), sampleType: book.sampleType.code(fields[4]), assay: book.assay.code(fields[5]),
                             conditions: conditions, timepoint: book.timepoint.code(fields[7]), duration: fields[8], batch: batch, replicate: replicate)
    }

    private func stubBiosample(_ text: String, _ width: Int) throws -> Biosample {
        var cursor = Cursor(text)
        let replicate = cursor.optionalSuffix(#"R([0-9]{2})"#)
        let batch = cursor.optionalSuffix(#"B([0-9]{2})"#)
        guard let suffix = suffixMatch(cursor.rest, #"[0-9]+[DWMYN]"#) else { throw invalid("Missing or invalid stub duration.") }
        let duration = "P" + suffix
        try checkDuration(duration)
        _ = try cursor.peel(suffix.count, "duration")
        let book = reference.codebook
        let timepoint = try book.timepoint.suffix(cursor.rest)
        _ = try cursor.peel(timepoint.stub.count, "timepoint")
        let countText = try cursor.peel(2, "condition count")
        guard asciiDigits(countText), let count = Int(countText) else { throw invalid("Missing condition count.") }
        try checkCount(count)
        let project = try cursor.prefix(book.project)
        let species = try book.species.stub(cursor.take(book.speciesWidth, "species"))
        let sid = try base62(cursor.take(width, "subject ID"))
        let tissue = try cursor.prefix(book.tissue)
        let sampleType = try cursor.prefix(book.sampleType)
        let assay = try cursor.prefix(book.assay)
        guard cursor.rest.count == count * 3 else { throw invalid("Condition count or stub length does not match. Check the stub width.") }
        var conditions: [Disease] = []
        for _ in 0..<count { conditions.append(try reference.diseases.ordinal(base62(cursor.take(3, "condition")))) }
        return Biosample(project: project, species: species, subjectID: sid, tissue: tissue, sampleType: sampleType,
                         assay: assay, conditions: conditions, timepoint: timepoint, duration: duration, batch: batch, replicate: replicate)
    }

    private func humanSubject(_ text: String, _ width: Int) throws -> Subject {
        let fields = text.components(separatedBy: "-")
        guard fields.count == 6 else { throw invalid("A human subject identifier requires exactly 6 fields.") }
        guard !fields[0].isEmpty else { throw invalid("Missing study.") }
        let book = reference.codebook.subject
        return try Subject(study: fields[0], studyLabel: book.study.terms.first { $0.key.replacingOccurrences(of: "-", with: "_") == fields[0] }?.label,
                           subjectID: decimalSubject(fields[1], width), type: book.type.code(fields[2]),
                           conditions: humanConditions(fields[3]), sex: book.sex.code(fields[4]), ageGroup: book.ageGroup.code(fields[5]))
    }

    private func stubSubject(_ text: String, _ width: Int) throws -> Subject {
        var cursor = Cursor(text)
        let book = reference.codebook.subject
        let age = try book.ageGroup.stub(cursor.peel(2, "age group"))
        let sex = try book.sex.stub(cursor.peel(1, "sex"))
        let countText = try cursor.peel(2, "condition count")
        guard asciiDigits(countText), let count = Int(countText) else { throw invalid("Invalid subject condition count.") }
        try checkCount(count)
        var conditionCursor = Cursor(try cursor.peel(count * 3, "conditions"))
        var conditions: [Disease] = []
        for _ in 0..<count { conditions.append(try reference.diseases.ordinal(base62(conditionCursor.take(3, "condition")))) }
        let type = try book.type.stub(cursor.peel(1, "type"))
        let sid = try base62(cursor.peel(width, "subject ID"))
        guard !cursor.rest.isEmpty else { throw invalid("Missing study in subject stub.") }
        return Subject(study: cursor.rest, studyLabel: book.study.terms.first { $0.stub == cursor.rest }?.label,
                       subjectID: sid, type: type, conditions: conditions, sex: sex, ageGroup: age)
    }

    private func humanConditions(_ text: String) throws -> [Disease] {
        let codes = text.components(separatedBy: "+")
        try checkCount(codes.count)
        return try codes.map {
            guard matches($0, #"[A-Z][0-9]{2}(?:\.?[0-9]+)?"#) else { throw invalid("A condition is not in the supported ICD-10 format.") }
            return reference.diseases.disease($0)
        }
    }
}

private func invalid(_ message: String) -> ClarIDError { ClarIDError(.invalidIdentifier, message) }
private func asciiDigits(_ value: String) -> Bool { !value.isEmpty && value.utf8.allSatisfy { (48...57).contains($0) } }
private func decimalSubject(_ value: String, _ width: Int) throws -> Int64 {
    guard value.count == width, asciiDigits(value), let number = Int64(value) else {
        throw invalid("Subject ID must contain \(width) decimal digits. Check Advanced settings.")
    }
    return number
}
private func base62(_ text: String) throws -> Int64 {
    let alphabet = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".utf8)
    guard !text.isEmpty else { throw invalid("Empty Base62 field.") }
    var value: Int64 = 0
    for byte in text.utf8 {
        guard let index = alphabet.firstIndex(of: byte) else { throw invalid("Invalid Base62 field.") }
        let digit = Int64(index)
        guard value <= (Int64.max - digit) / 62 else { throw invalid("Overflowing Base62 field.") }
        value = value * 62 + digit
    }
    return value
}
private func checkCount(_ count: Int) throws {
    guard (1...10).contains(count) else { throw invalid("Expected 1–10 conditions.") }
}
private func checkDuration(_ value: String) throws {
    guard matches(value, #"P(?:[0-9][DWMY]|0N)"#) else { throw invalid("Duration must be P plus one digit and D/W/M/Y, or P0N.") }
}
private func matches(_ value: String, _ pattern: String) -> Bool {
    value.range(of: "\\A(?:" + pattern + ")\\z", options: .regularExpression) != nil
}
private func suffixMatch(_ value: String, _ pattern: String) -> String? {
    guard let range = value.range(of: "(?:" + pattern + ")\\z", options: .regularExpression) else { return nil }
    return String(value[range])
}
private struct Cursor {
    var rest: String
    init(_ text: String) { rest = text }
    mutating func take(_ count: Int, _ field: String) throws -> String {
        guard rest.count >= count else { throw invalid("Stub is too short for \(field). Check the active settings.") }
        let value = String(rest.prefix(count)); rest = String(rest.dropFirst(count)); return value
    }
    mutating func peel(_ count: Int, _ field: String) throws -> String {
        guard rest.count >= count else { throw invalid("Stub is too short for \(field). Check the active settings.") }
        let value = String(rest.suffix(count)); rest = String(rest.dropLast(count)); return value
    }
    mutating func prefix(_ vocabulary: Vocabulary) throws -> Term {
        let term = try vocabulary.prefix(rest); _ = try take(term.stub.count, vocabulary.name); return term
    }
    mutating func optionalSuffix(_ pattern: String) -> Int? {
        guard let value = suffixMatch(rest, pattern) else { return nil }
        rest = String(rest.dropLast(value.count)); return Int(value.dropFirst())
    }
}
