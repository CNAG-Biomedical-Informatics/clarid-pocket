import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct UpdateResponse {
    public let status: Int
    public let body: Data
    public init(status: Int, body: Data = Data()) { self.status = status; self.body = body }
}

public struct AppUpdate: Equatable {
    public let message: String
    public let releasePage: URL?
    public init(message: String, releasePage: URL? = nil) { self.message = message; self.releasePage = releasePage }
}

/// A manual public metadata request. Scanned identifiers, settings, and credentials are never sent.
public struct AppUpdates {
    public static let endpoint = URL(string: "https://api.github.com/repos/CNAG-Biomedical-Informatics/clarid-pocket/releases/latest")!
    private static let releases = "https://github.com/CNAG-Biomedical-Informatics/clarid-pocket/releases"
    private let fetch: () async throws -> UpdateResponse

    public init(fetch: @escaping () async throws -> UpdateResponse = AppUpdates.fetchLatestRelease) { self.fetch = fetch }

    public func check(installed: String) async -> AppUpdate {
        let response: UpdateResponse
        do { response = try await fetch() }
        catch { return AppUpdate(message: "Could not connect to GitHub. Check your connection and try again.") }
        switch response.status {
        case 200:
            do { return try evaluate(installed: installed, body: response.body) }
            catch { return AppUpdate(message: "Could not read the release information. Please try again later.") }
        case 404: return AppUpdate(message: "No published release is available yet.")
        case 403, 429: return AppUpdate(message: "GitHub is limiting update checks. Please try again later.")
        default: return AppUpdate(message: "Could not check for updates. Please try again later.")
        }
    }

    private func evaluate(installed: String, body: Data) throws -> AppUpdate {
        guard body.count <= 1_048_576 else { throw InvalidRelease.data }
        let release = try JSONDecoder().decode(Release.self, from: body)
        guard !release.draft, !release.prerelease else {
            return AppUpdate(message: "No published stable release is available yet.")
        }
        let current = try version(installed), latest = try version(release.tag)
        let comparison = zip(latest, current).first { $0 != $1 }.map { $0 > $1 ? 1 : -1 } ?? 0
        if comparison < 0 {
            return AppUpdate(message: "Your installed version is newer than the latest published release.")
        }
        let display = release.tag.hasPrefix("v") ? String(release.tag.dropFirst()) : release.tag
        let page = URL(string: Self.releases + "/tag/" + release.tag)!
        let names = ["clarid-pocket-\(display).ipa", "clarid-pocket-\(display)-ios.ipa"]
        let hasIOSPackage = release.assets.contains { names.contains($0.name) && $0.state == "uploaded" }
        if !hasIOSPackage {
            // The repository currently publishes Android APKs. Never describe one as an iOS update.
            return AppUpdate(message: "Latest release: \(display). No iOS package yet.", releasePage: page)
        }
        return comparison == 0 ? AppUpdate(message: "Your app is up to date.")
            : AppUpdate(message: "Version \(display) is available.", releasePage: page)
    }

    private func version(_ value: String) throws -> [Int64] {
        guard value.range(of: #"\Av?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\z"#, options: .regularExpression) != nil else {
            throw InvalidRelease.data
        }
        let clean = value.hasPrefix("v") ? String(value.dropFirst()) : value
        return try clean.split(separator: ".").map {
            guard let number = Int64($0) else { throw InvalidRelease.data }; return number
        }
    }

    public static func fetchLatestRelease() async throws -> UpdateResponse {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 20
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        let session = URLSession(configuration: configuration, delegate: NoRedirects(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
        request.httpShouldHandleCookies = false
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("2022-11-28", forHTTPHeaderField: "X-GitHub-Api-Version")
        request.setValue("ClarID-Pocket", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return UpdateResponse(status: response.statusCode, body: data)
    }
}

private struct Release: Decodable {
    struct Asset: Decodable { let name: String, state: String }
    let tag: String
    let draft: Bool, prerelease: Bool
    let assets: [Asset]
    enum CodingKeys: String, CodingKey { case tag = "tag_name", draft, prerelease, assets }
}
private enum InvalidRelease: Error { case data }
private final class NoRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
