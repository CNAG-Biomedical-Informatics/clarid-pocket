import Foundation

public enum IdentifierFormat: String, CaseIterable, Codable {
    case auto = "Auto", human = "Human", stub = "Stub"
}

public enum IdentifierEntity: String, CaseIterable, Codable {
    case auto = "Auto", biosample = "Biosample", subject = "Subject"
}

public struct DecodeSettings: Equatable {
    public var entity: IdentifierEntity = .auto
    public var format: IdentifierFormat = .auto
    public var humanSubjectWidth: Int = 5
    public var stubSubjectWidth: Int = 3
    public init(entity: IdentifierEntity = .auto, format: IdentifierFormat = .auto,
                humanSubjectWidth: Int = 5, stubSubjectWidth: Int = 3) {
        self.entity = entity; self.format = format
        self.humanSubjectWidth = humanSubjectWidth; self.stubSubjectWidth = stubSubjectWidth
    }
    public func validate() throws {
        guard (1...18).contains(humanSubjectWidth), (1...10).contains(stubSubjectWidth) else {
            throw ClarIDError(.invalidSettings, "Human subject width must be 1–18; stub subject width must be 1–10.")
        }
    }
}

public struct ClarIDError: Error, LocalizedError, Equatable {
    public enum Code { case invalidSettings, invalidIdentifier, unknownCode, unsupportedCodebook, invalidCodebook }
    public let code: Code
    public let message: String
    public var errorDescription: String? { message }
    public init(_ code: Code, _ message: String) { self.code = code; self.message = message }
}

public struct Term: Equatable {
    public let key: String
    public let code: String
    public let stub: String
    public let label: String
}

public struct Disease: Equatable {
    public let code: String
    public let label: String?
}

public struct Biosample: Equatable {
    public let project: Term
    public let species: Term
    public let subjectID: Int64
    public let tissue: Term
    public let sampleType: Term
    public let assay: Term
    public let conditions: [Disease]
    public let timepoint: Term
    public let duration: String
    public let batch: Int?
    public let replicate: Int?
}

public struct Subject: Equatable {
    public let study: String
    public let studyLabel: String?
    public let subjectID: Int64
    public let type: Term
    public let conditions: [Disease]
    public let sex: Term
    public let ageGroup: Term
}

public enum IdentifierRecord: Equatable {
    case biosample(Biosample), subject(Subject)
    public var entity: IdentifierEntity {
        switch self { case .biosample: return .biosample; case .subject: return .subject }
    }
    public var conditions: [Disease] {
        switch self { case .biosample(let value): return value.conditions; case .subject(let value): return value.conditions }
    }
}

public struct DecodedIdentifier: Equatable {
    public let record: IdentifierRecord
    public let format: IdentifierFormat
}
