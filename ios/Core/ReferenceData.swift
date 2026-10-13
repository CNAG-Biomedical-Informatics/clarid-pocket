import Foundation

public struct Vocabulary {
    public let name: String
    public let terms: [Term]
    private let codes: [String: Term]
    private let stubs: [String: Term]
    private let longestFirst: [Term]

    public init(name: String, terms: [Term]) throws {
        guard !terms.isEmpty, Set(terms.map(\.code)).count == terms.count,
              Set(terms.map(\.stub)).count == terms.count else {
            throw ClarIDError(.invalidCodebook, "Empty or ambiguous \(name) vocabulary.")
        }
        self.name = name; self.terms = terms
        codes = Dictionary(uniqueKeysWithValues: terms.map { ($0.code, $0) })
        stubs = Dictionary(uniqueKeysWithValues: terms.map { ($0.stub, $0) })
        longestFirst = terms.sorted { $0.stub.count > $1.stub.count }
    }
    public func code(_ value: String) throws -> Term {
        guard let term = codes[value] else { throw unknown("code") }; return term
    }
    public func stub(_ value: String) throws -> Term {
        guard let term = stubs[value] else { throw unknown("stub") }; return term
    }
    func prefix(_ value: String) throws -> Term {
        guard let term = longestFirst.first(where: { value.hasPrefix($0.stub) }) else { throw unknown("stub") }; return term
    }
    func suffix(_ value: String) throws -> Term {
        guard let term = longestFirst.first(where: { value.hasSuffix($0.stub) }) else { throw unknown("stub") }; return term
    }
    private func unknown(_ kind: String) -> ClarIDError {
        ClarIDError(.unknownCode, "Unknown \(name) \(kind). Check the active codebook and settings.")
    }
}

public struct SubjectCodebook {
    public var study: Vocabulary
    public var type: Vocabulary
    public var sex: Vocabulary
    public var ageGroup: Vocabulary
}

public struct Codebook {
    public let version: String
    public let localVersion: String?
    public var project: Vocabulary
    public var species: Vocabulary
    public var tissue: Vocabulary
    public var sampleType: Vocabulary
    public var assay: Vocabulary
    public var timepoint: Vocabulary
    public var subject: SubjectCodebook
    public let speciesWidth: Int

    public static func load(_ data: Data) throws -> Codebook {
        do {
            let root = try JSONDecoder().decode(BookDocument.self, from: data)
            guard root.metadata.version == "0.04" else {
                throw ClarIDError(.unsupportedCodebook, "This app supports ClarID codebook 0.04 only.")
            }
            let biosample = root.entities.biosample, subject = root.entities.subject
            try pattern(biosample, "condition_pattern", #"^([A-Z]\d{2}(?:\.\d+)?)$"#, "%s", "%s")
            try pattern(subject, "condition_pattern", #"^([A-Z]\d{2}(?:\.\d+)?)$"#, "%s", "%s")
            try pattern(biosample, "duration_pattern", #"^(?:P?(\d)([DWMY])|P?(0)(N))$"#, "P%d%s", "%d%s")
            try pattern(biosample, "batch_pattern", #"^(\d{1,2})$"#, "B%02d", "B%02d")
            try pattern(biosample, "replicate_pattern", #"^(\d{1,2})$"#, "R%02d", "R%02d")
            func vocabulary(_ name: String, _ entity: [String: [String: StringOrTerm]]) throws -> Vocabulary {
                guard let entries = entity[name] else { throw invalidBook() }
                var merged = root.entities.defaults
                for (key, raw) in entries {
                    guard case .term(let term) = raw else { throw invalidBook() }
                    merged[key] = term
                }
                let terms = try merged.sorted { $0.key < $1.key }.map { key, raw -> Term in
                    guard !raw.code.isEmpty, !raw.stubCode.isEmpty, !raw.label.isEmpty else { throw invalidBook() }
                    return Term(key: key, code: raw.code, stub: raw.stubCode, label: raw.label)
                }
                return try Vocabulary(name: name, terms: terms)
            }
            let species = try vocabulary("species", biosample)
            let widths = Set(species.terms.filter { $0.key != "Not Available" }.map { $0.stub.count })
            guard widths.count == 1, let speciesWidth = widths.first, speciesWidth > 0 else {
                throw ClarIDError(.invalidCodebook, "Species stubs must have a consistent width.")
            }
            let subjectBook = try SubjectCodebook(study: vocabulary("study", subject), type: vocabulary("type", subject),
                                                 sex: vocabulary("sex", subject), ageGroup: vocabulary("age_group", subject))
            guard subjectBook.type.terms.allSatisfy({ $0.stub.count == 1 }),
                  subjectBook.sex.terms.allSatisfy({ $0.stub.count == 1 }),
                  subjectBook.ageGroup.terms.allSatisfy({ $0.stub.count == 2 }) else {
                throw ClarIDError(.invalidCodebook, "Subject type/sex/age stubs must use widths 1/1/2.")
            }
            return try Codebook(version: root.metadata.version, localVersion: root.metadata.localVersion,
                                project: vocabulary("project", biosample), species: species, tissue: vocabulary("tissue", biosample),
                                sampleType: vocabulary("sample_type", biosample), assay: vocabulary("assay", biosample),
                                timepoint: vocabulary("timepoint", biosample), subject: subjectBook, speciesWidth: speciesWidth)
        } catch let error as ClarIDError { throw error }
        catch { throw invalidBook() }
    }
}

private struct RawTerm: Decodable {
    let code: String, stubCode: String, label: String
    enum CodingKeys: String, CodingKey { case code, stubCode = "stub_code", label }
}
private enum StringOrTerm: Decodable {
    case string(String), term(RawTerm)
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let string = try? container.decode(String.self) { self = .string(string) }
        else { self = .term(try container.decode(RawTerm.self)) }
    }
}
private struct BookDocument: Decodable {
    struct Metadata: Decodable {
        let version: String, localVersion: String?
        enum CodingKeys: String, CodingKey { case version, localVersion = "local_version" }
    }
    struct Entities: Decodable {
        let defaults: [String: RawTerm]
        let biosample: [String: [String: StringOrTerm]]
        let subject: [String: [String: StringOrTerm]]
        enum CodingKeys: String, CodingKey { case defaults = "_defaults", biosample, subject }
    }
    let metadata: Metadata
    let entities: Entities
}
private func invalidBook() -> ClarIDError { ClarIDError(.invalidCodebook, "The codebook could not be loaded.") }
private func pattern(_ entity: [String: [String: StringOrTerm]], _ field: String,
                     _ regex: String, _ human: String, _ stub: String) throws {
    guard let value = entity[field], case .string(let actualRegex)? = value["regex"],
          case .string(let actualHuman)? = value["code_format"], case .string(let actualStub)? = value["stub_format"],
          actualRegex == regex, actualHuman == human, actualStub == stub else {
        throw ClarIDError(.invalidCodebook, "Unsupported \(field) configuration.")
    }
}

public struct DiseaseDictionary {
    private let labels: [String: String]
    private let ordinals: [Int64: String]
    public init(labels: [String: String], ordinals: [Int64: String]) {
        self.labels = labels; self.ordinals = ordinals
    }
    public static func load(labels: Data, ordinals: Data) throws -> DiseaseDictionary {
        let names = try JSONDecoder().decode([String: String].self, from: labels)
        let codes = try JSONDecoder().decode([String: Int64].self, from: ordinals)
        guard Set(codes.values).count == codes.count else { throw invalidBook() }
        return DiseaseDictionary(labels: names, ordinals: Dictionary(uniqueKeysWithValues: codes.map { ($0.value, $0.key) }))
    }
    public func disease(_ code: String) -> Disease {
        let key = code.replacingOccurrences(of: ".", with: "")
        let display = key.count > 3 ? String(key.prefix(3)) + "." + key.dropFirst(3) : key
        return Disease(code: display, label: labels[key])
    }
    public func ordinal(_ value: Int64) throws -> Disease {
        guard let code = ordinals[value] else { throw ClarIDError(.unknownCode, "Unknown condition ordinal in the stub.") }
        return disease(code)
    }
}

public struct ReferenceData {
    public var codebook: Codebook
    public var diseases: DiseaseDictionary
    public init(codebook: Codebook, diseases: DiseaseDictionary) { self.codebook = codebook; self.diseases = diseases }
    public static func load(codebook: Data, labels: Data, ordinals: Data) throws -> ReferenceData {
        try ReferenceData(codebook: Codebook.load(codebook), diseases: DiseaseDictionary.load(labels: labels, ordinals: ordinals))
    }
}
