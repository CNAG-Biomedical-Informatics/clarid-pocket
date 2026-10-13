import Foundation
import XCTest
@testable import ClarIDCore

final class AppUpdatesTests: XCTestCase {
    private func json(_ tag: String = "v0.1.10", draft: Bool = false, prerelease: Bool = false,
                      assets: [[String: String]]? = nil) throws -> Data {
        let display = tag.hasPrefix("v") ? String(tag.dropFirst()) : tag
        return try JSONSerialization.data(withJSONObject: [
            "tag_name": tag, "draft": draft, "prerelease": prerelease,
            "html_url": "https://example.org/untrusted",
            "assets": assets ?? [["name": "clarid-pocket-\(display)-ios.ipa", "state": "uploaded"]]
        ])
    }
    private func check(_ data: Data, installed: String = "0.1.9", status: Int = 200) async -> AppUpdate {
        await AppUpdates { UpdateResponse(status: status, body: data) }.check(installed: installed)
    }
    func testNewerVersionsCompareNumericallyAndUseOfficialPage() async throws {
        for tag in ["v0.1.10", "v0.10.0", "v1.0.0", "0.1.10"] {
            let result = await check(try json(tag))
            XCTAssertTrue(result.message.contains("is available"))
            XCTAssertEqual(result.releasePage?.absoluteString, "https://github.com/CNAG-Biomedical-Informatics/clarid-pocket/releases/tag/" + tag)
        }
    }
    func testEqualAndOlderReleasesDoNotOfferDowngrades() async throws {
        let equal = await check(try json("v0.1.9"))
        XCTAssertEqual(equal.message, "Your app is up to date."); XCTAssertNil(equal.releasePage)
        let older = await check(try json("v0.1.8"))
        XCTAssertTrue(older.message.contains("newer")); XCTAssertNil(older.releasePage)
    }
    func testAndroidPackagesAreNeverOfferedAsIOSUpdates() async throws {
        for tag in ["v0.1.9", "v0.1.10"] {
            let result = await check(try json(tag, assets: [["name": "clarid-pocket-\(tag.dropFirst()).apk", "state": "uploaded"]]))
            XCTAssertTrue(result.message.contains("No iOS package"))
            XCTAssertFalse(result.message.contains("up to date"))
            XCTAssertNotNil(result.releasePage)
        }
        let empty = await check(try json(assets: []))
        XCTAssertTrue(empty.message.contains("No iOS package"))
        let uploading = await check(try json(assets: [["name": "clarid-pocket-0.1.10-ios.ipa", "state": "new"]]))
        XCTAssertTrue(uploading.message.contains("No iOS package"))
        let legacyName = await check(try json(assets: [["name": "clarid-pocket-0.1.10.ipa", "state": "uploaded"]]))
        XCTAssertTrue(legacyName.message.contains("is available"))
    }
    func testDraftsAndPrereleasesAreExcluded() async throws {
        for data in [try json(draft: true), try json(prerelease: true)] {
            let result = await check(data)
            XCTAssertTrue(result.message.contains("No published stable")); XCTAssertNil(result.releasePage)
        }
    }
    func testMalformedResponsesAndVersionsFailClearly() async throws {
        var invalid = [Data("{}".utf8), Data("not json".utf8), Data(repeating: 32, count: 1_048_577)]
        for tag in ["v0.1.10-dev", "v0.1.10/evil", "v999999999999999999999.0.0", "v01.1.10", "v0.1.10\n"] {
            invalid.append(try json(tag))
        }
        for data in invalid {
            let result = await check(data)
            XCTAssertTrue(result.message.contains("Could not read")); XCTAssertNil(result.releasePage)
        }
        let installed = await check(try json(), installed: "dev")
        XCTAssertTrue(installed.message.contains("Could not read"))
    }
    func testHTTPFailuresAreReadable() async {
        for (status, phrase) in [(404, "No published"), (403, "limiting"), (429, "limiting"), (500, "Could not check"), (302, "Could not check")] {
            let result = await check(Data(), status: status)
            XCTAssertTrue(result.message.contains(phrase)); XCTAssertNil(result.releasePage)
        }
    }
    func testOfflineCheckCanBeRetried() async throws {
        let fetcher = RetryFetcher(data: try json())
        let checker = AppUpdates { try await fetcher.fetch() }
        let offline = await checker.check(installed: "0.1.9")
        XCTAssertTrue(offline.message.contains("connection")); XCTAssertNil(offline.releasePage)
        let retried = await checker.check(installed: "0.1.9")
        XCTAssertTrue(retried.message.contains("is available"))
    }
    func testLivePublicEndpointWhenRequested() async throws {
        guard ProcessInfo.processInfo.environment["CLARID_LIVE_UPDATE_TEST"] == "1" else { throw XCTSkip("Live check is opt-in") }
        let response = try await AppUpdates.fetchLatestRelease()
        XCTAssertEqual(response.status, 200)
        let result = await check(response.body, installed: "0.0.0")
        XCTAssertNotNil(result.releasePage, result.message)
        XCTAssertFalse(result.message.contains("Could not"))
    }
}

private actor RetryFetcher {
    private var first = true
    private let data: Data
    init(data: Data) { self.data = data }
    func fetch() throws -> UpdateResponse {
        if first { first = false; throw URLError(.notConnectedToInternet) }
        return UpdateResponse(status: 200, body: data)
    }
}
