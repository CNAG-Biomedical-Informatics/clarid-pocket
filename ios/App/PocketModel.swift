import Foundation
import SwiftUI

@MainActor
final class PocketModel: ObservableObject {
    @Published var settings: DecodeSettings { didSet { saveSettings(); clearResult() } }
    @Published private(set) var reference: ReferenceData?
    @Published private(set) var loading = true
    @Published private(set) var startupError: String?
    @Published private(set) var result: DecodedIdentifier?
    @Published private(set) var identifier: String?
    @Published private(set) var decodeError: String?
    @Published private(set) var checkingUpdates = false
    @Published private(set) var appUpdate: AppUpdate?
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let saved = DecodeSettings(
            entity: IdentifierEntity(rawValue: defaults.string(forKey: "entity") ?? "") ?? .auto,
            format: IdentifierFormat(rawValue: defaults.string(forKey: "format") ?? "") ?? .auto,
            humanSubjectWidth: defaults.object(forKey: "human-width") as? Int ?? 5,
            stubSubjectWidth: defaults.object(forKey: "stub-width") as? Int ?? 3)
        settings = (try? saved.validate()) != nil ? saved : DecodeSettings()
        loadReference()
    }

    private func loadReference() {
        DispatchQueue.global(qos: .userInitiated).async {
            let loaded: Result<ReferenceData, Error> = Result {
                func data(_ name: String) throws -> Data {
                    guard let url = Bundle.main.url(forResource: name, withExtension: "json") else {
                        throw ClarIDError(.invalidCodebook, "Bundled reference data is missing. Reinstall the app.")
                    }
                    return try Data(contentsOf: url)
                }
                return try ReferenceData.load(codebook: data("clarid-codebook"), labels: data("icd10"), ordinals: data("icd10_order"))
            }
            DispatchQueue.main.async {
                self.loading = false
                switch loaded {
                case .success(let reference): self.reference = reference
                case .failure: self.startupError = "The bundled codebook could not be loaded. Reinstall the app."
                }
            }
        }
    }

    func clearResult() { result = nil; identifier = nil; decodeError = nil }

    func checkForUpdates() {
        guard !checkingUpdates else { return }
        checkingUpdates = true
        appUpdate = nil
        Task {
            defer { checkingUpdates = false }
            appUpdate = await AppUpdates().check(installed: appVersion)
        }
    }

    @discardableResult
    func decode(_ text: String) -> Bool {
        clearResult()
        guard let reference = reference else { return false }
        do {
            result = try IdentifierDecoder(reference: reference).decode(text, settings: settings)
            identifier = text
            return true
        } catch let error as ClarIDError { decodeError = error.message }
        catch { decodeError = "This QR code could not be decoded as a ClarID identifier." }
        return false
    }

    private func saveSettings() {
        defaults.set(settings.entity.rawValue, forKey: "entity")
        defaults.set(settings.format.rawValue, forKey: "format")
        defaults.set(settings.humanSubjectWidth, forKey: "human-width")
        defaults.set(settings.stubSubjectWidth, forKey: "stub-width")
    }
    var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }
}
