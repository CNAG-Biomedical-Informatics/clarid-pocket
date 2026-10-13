import XCTest
@testable import ClarIDCore

final class ScanTargetTests: XCTestCase {
    private func candidate(_ text: String?, _ left: Double, _ top: Double, _ right: Double, _ bottom: Double) -> ScanCandidate {
        ScanCandidate(text: text, bounds: ScanBounds(left: left, top: top, right: right, bottom: bottom))
    }
    func testSingleVisibleCodeCapturesImmediatelyAwayFromCentre() {
        var target = ScanTarget()
        XCTAssertEqual(target.update([candidate("one", 5, 5, 20, 20)], width: 100, height: 100).value, "one")
        XCTAssertNil(target.update([candidate("two", 5, 5, 20, 20)], width: 100, height: 100).value)
        target.reset()
        XCTAssertEqual(target.update([candidate("two", 5, 5, 20, 20)], width: 100, height: 100).value, "two")
    }
    func testCentreSelectsExactlyOneOfMultipleCodes() {
        var target = ScanTarget()
        let result = target.update([candidate("one", 5, 5, 20, 20), candidate("two", 40, 40, 60, 60)], width: 100, height: 100)
        XCTAssertEqual(result.value, "two"); XCTAssertFalse(result.ambiguous)
    }
    func testAmbiguousCodesRemainOpenUntilOneIsTargeted() {
        var target = ScanTarget()
        let result = target.update([candidate("one", 5, 5, 20, 20), candidate("two", 75, 75, 90, 90)], width: 100, height: 100)
        XCTAssertNil(result.value); XCTAssertTrue(result.ambiguous)
        XCTAssertEqual(target.update([candidate("one", 5, 5, 20, 20)], width: 100, height: 100).value, "one")
    }
    func testOverlappingCodesAreAmbiguous() {
        var target = ScanTarget()
        XCTAssertTrue(target.update([candidate("one", 40, 40, 60, 60), candidate("two", 30, 30, 70, 70)], width: 100, height: 100).ambiguous)
    }
    func testInvalidInvisibleAndEmptyCandidatesDoNotLatch() {
        var target = ScanTarget()
        let invalid = [candidate("one", -20, -20, -10, -10), candidate("two", 60, 40, 30, 50)]
        XCTAssertNil(target.update(invalid, width: 100, height: 100).value)
        XCTAssertNil(target.update([candidate("", 40, 40, 60, 60)], width: 100, height: 100).value)
        XCTAssertNil(target.update([candidate("one", 40, 40, 60, 60)], width: 0, height: 100).value)
        XCTAssertEqual(target.update([candidate("one", 40, 40, 60, 60)], width: 100, height: 100).value, "one")
    }
}
