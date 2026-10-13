import Foundation
import XCTest
@testable import ClarIDCore

final class DecoderTests: XCTestCase {
    private static let ios = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
    private static let resources = ios.deletingLastPathComponent().appendingPathComponent("core/src/main/resources/clarid/0.04")
    private static let reference = try! ReferenceData.load(
        codebook: Data(contentsOf: ios.appendingPathComponent("Resources/clarid-codebook.json")),
        labels: Data(contentsOf: resources.appendingPathComponent("icd10.json")),
        ordinals: Data(contentsOf: resources.appendingPathComponent("icd10_order.json")))
    private let human = "TCGA_AML-HomSap-00001-LIV-TUM-RNA-I25.110-CHL-P1M"
    private let stub = "AML01001LTR2to01C1M"

    private struct Fixtures: Decodable { let fixtures: [Fixture] }
    private struct Fixture: Decodable {
        let name: String, format: String, identifier: String
        let human_width: Int, stub_width: Int
        let expected: [String: String]
    }

    func testAll76ReferenceFixturesInExplicitAndAutoModes() throws {
        let decoder = IdentifierDecoder(reference: Self.reference)
        for entity in [IdentifierEntity.biosample, .subject] {
            let url = Self.ios.deletingLastPathComponent().appendingPathComponent("core/src/test/resources/\(entity.rawValue.lowercased())-fixtures.json")
            let fixtures = try JSONDecoder().decode(Fixtures.self, from: Data(contentsOf: url)).fixtures
            XCTAssertEqual(fixtures.count, entity == .biosample ? 31 : 45)
            for fixture in fixtures {
                let format: IdentifierFormat = fixture.format == "human" ? .human : .stub
                let explicit = DecodeSettings(entity: entity, format: format, humanSubjectWidth: fixture.human_width, stubSubjectWidth: fixture.stub_width)
                let result = try decoder.decode(fixture.identifier, settings: explicit)
                XCTAssertEqual(result.format, format, fixture.name)
                var actual: [String: String]
                switch result.record {
                case .biosample(let value):
                    actual = ["project": value.project.key, "species": value.species.key, "subject_id": String(value.subjectID),
                              "tissue": value.tissue.key, "sample_type": value.sampleType.key, "assay": value.assay.key,
                              "timepoint": value.timepoint.key, "duration": value.duration,
                              "batch": value.batch.map(String.init) ?? "", "replicate": value.replicate.map(String.init) ?? "",
                              "condition": value.conditions.map(\.code).joined(separator: ";")]
                case .subject(let value):
                    actual = ["study": value.study, "subject_id": String(value.subjectID),
                              "type": format == .human ? value.type.code : value.type.key,
                              "sex": format == .human ? value.sex.code : value.sex.key, "age_group": value.ageGroup.key,
                              "condition": value.conditions.map(\.code).joined(separator: format == .human ? "+" : ";")]
                }
                actual["condition_name"] = result.record.conditions.map { $0.label ?? "" }.joined(separator: ";")
                XCTAssertEqual(actual, fixture.expected, fixture.name)
                var auto = explicit; auto.entity = .auto; auto.format = .auto
                XCTAssertEqual(try decoder.decode(fixture.identifier, settings: auto), result, fixture.name)
            }
        }
    }

    func testRejectsMalformedBiosamplesAndSubjects() {
        let decoder = IdentifierDecoder(reference: Self.reference)
        let subject = "TCGA_AML-00001-Case-I25.110-Male-A40_49"
        let subjectStub = "AML001C2to01MA4"
        let invalid: [String] = [human + "-EXTRA", human + "-R01-B02", human + "-B1", human + "-B01-B02",
                       human.replacingOccurrences(of: "00001", with: "abcde"),
                       human.replacingOccurrences(of: "P1M", with: "P10D"), human.replacingOccurrences(of: "P1M", with: "P7N"),
                       human.replacingOccurrences(of: "I25.110", with: "I25..110"),
                       human.replacingOccurrences(of: "I25.110", with: "I25.110+"),
                       human.replacingOccurrences(of: "I25.110", with: Array(repeating: "I25.110", count: 11).joined(separator: "+")),
                       human.replacingOccurrences(of: "LIV", with: "XYZ"),
                       stub + "B1", stub + "R01B02", stub.replacingOccurrences(of: "1M", with: "10D"),
                       stub.replacingOccurrences(of: "2to01", with: "2to00"), stub.replacingOccurrences(of: "2to01", with: "2to02"),
                       stub.replacingOccurrences(of: "2to", with: "000"), stub.replacingOccurrences(of: "2to", with: "zzz"),
                       stub.replacingOccurrences(of: "001", with: "0?1"),
                       subject + "-EXTRA", subject.replacingOccurrences(of: "Case", with: "UnknownType"),
                       subject.replacingOccurrences(of: "Male", with: "UnknownSex"), subject.replacingOccurrences(of: "A40_49", with: "A400"),
                       subjectStub.replacingOccurrences(of: "01MA4", with: "XXMA4"), subjectStub.replacingOccurrences(of: "MA4", with: "MXX"),
                       String(subjectStub.dropFirst(3)), "", stub + "\n", stub + "\u{0000}", String(repeating: "x", count: 1025),
                       "https://example.org", " " + stub]
        for text in invalid {
            XCTAssertThrowsError(try decoder.decode(text), text) { XCTAssertTrue($0 is ClarIDError) }
        }
    }

    func testSettingsOverridesAndWidthBoundaries() throws {
        let decoder = IdentifierDecoder(reference: Self.reference)
        for settings in [DecodeSettings(format: .stub), DecodeSettings(humanSubjectWidth: 4),
                         DecodeSettings(humanSubjectWidth: 0), DecodeSettings(humanSubjectWidth: 19),
                         DecodeSettings(stubSubjectWidth: 0), DecodeSettings(stubSubjectWidth: 11)] {
            XCTAssertThrowsError(try decoder.decode(human, settings: settings))
        }
        XCTAssertThrowsError(try decoder.decode(stub, settings: DecodeSettings(stubSubjectWidth: 4)))
        XCTAssertThrowsError(try decoder.decode(stub, settings: DecodeSettings(format: .human)))
        XCTAssertThrowsError(try decoder.decode("TCGA_AML-00001-Case-I25.110-Male-A40_49", settings: DecodeSettings(entity: .biosample)))
        let largestStub = stub.replacingOccurrences(of: "001", with: "zzzzzzzzzz")
        guard case .biosample(let largest) = try decoder.decode(largestStub, settings: DecodeSettings(stubSubjectWidth: 10)).record else { return XCTFail() }
        XCTAssertEqual(largest.subjectID, 839299365868340223)
        XCTAssertThrowsError(try decoder.decode(largestStub + "z", settings: DecodeSettings(stubSubjectWidth: 11)))
        let max = human.replacingOccurrences(of: "00001", with: "999999999999999999")
        guard case .biosample(let value) = try decoder.decode(max, settings: DecodeSettings(humanSubjectWidth: 18)).record else { return XCTFail() }
        XCTAssertEqual(value.subjectID, 999999999999999999)
    }

    func testLabelsDotFreeCodesAndFreeStudies() throws {
        let decoder = IdentifierDecoder(reference: Self.reference)
        let text = "CUSTOM_2026-00001-Control-C220+I25.110-Female-A0_9"
        guard case .subject(let value) = try decoder.decode(text).record else { return XCTFail() }
        XCTAssertEqual(value.study, "CUSTOM_2026"); XCTAssertNil(value.studyLabel)
        XCTAssertEqual(value.conditions.map(\.code), ["C22.0", "I25.110"])
        XCTAssertEqual(value.conditions.first?.label, "Liver cell carcinoma")
        var sparse = Self.reference
        sparse.diseases = DiseaseDictionary(labels: [:], ordinals: [:])
        let result = try IdentifierDecoder(reference: sparse).decode(human)
        XCTAssertEqual(result.record.conditions.first?.code, "I25.110")
        XCTAssertNil(result.record.conditions.first?.label)
    }

    func testAmbiguityRequiresExplicitEntity() throws {
        var reference = Self.reference
        func only(_ vocabulary: Vocabulary, _ stub: String) throws -> Vocabulary {
            let term = vocabulary.terms[0]
            return try Vocabulary(name: vocabulary.name, terms: [Term(key: term.key, code: term.code, stub: stub, label: term.label)])
        }
        reference.codebook.subject.type = try only(reference.codebook.subject.type, "R")
        reference.codebook.subject.sex = try only(reference.codebook.subject.sex, "C")
        reference.codebook.subject.ageGroup = try only(reference.codebook.subject.ageGroup, "1M")
        let decoder = IdentifierDecoder(reference: reference)
        XCTAssertThrowsError(try decoder.decode(stub))
        XCTAssertEqual(try decoder.decode(stub, settings: DecodeSettings(entity: .biosample)).record.entity, .biosample)
        XCTAssertEqual(try decoder.decode(stub, settings: DecodeSettings(entity: .subject)).record.entity, .subject)
    }

    func testCodebookValidationRejectsLegacyAndUnsupportedProfiles() throws {
        let data = try Data(contentsOf: Self.ios.appendingPathComponent("Resources/clarid-codebook.json"))
        let text = String(decoding: data, as: UTF8.self)
        XCTAssertEqual(Self.reference.codebook.version, "0.04")
        XCTAssertEqual(Self.reference.codebook.localVersion, "CNAG-2026.07.12")
        XCTAssertEqual(try Self.reference.codebook.species.stub("01").key, "Human")
        XCTAssertEqual(try Self.reference.codebook.tissue.stub("U").key, "Unknown")
        for version in ["0.02", "0.03", "0.05"] {
            XCTAssertThrowsError(try Codebook.load(Data(text.replacingOccurrences(of: "\"0.04\"", with: "\"\(version)\"").utf8))) {
                XCTAssertEqual(($0 as? ClarIDError)?.code, .unsupportedCodebook)
            }
        }
        XCTAssertThrowsError(try Codebook.load(Data(text.replacingOccurrences(of: "P%d%s", with: "D%d%s").utf8)))
        XCTAssertThrowsError(try Codebook.load(Data(text.replacingOccurrences(of: "\"stub_code\": \"01\"", with: "\"stub_code\": \"001\"").utf8)))
        XCTAssertThrowsError(try Vocabulary(name: "duplicate", terms: [Self.reference.codebook.project.terms[0], Self.reference.codebook.project.terms[0]]))
    }
}
