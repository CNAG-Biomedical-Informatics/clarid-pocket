import Foundation

public struct ScanBounds {
    public let left: Double, top: Double, right: Double, bottom: Double
    public init(left: Double, top: Double, right: Double, bottom: Double) {
        self.left = left; self.top = top; self.right = right; self.bottom = bottom
    }
    var centreX: Double { (left + right) / 2 }
    var centreY: Double { (top + bottom) / 2 }
    func contains(_ x: Double, _ y: Double) -> Bool { (left...right).contains(x) && (top...bottom).contains(y) }
}
public struct ScanCandidate {
    public let text: String?
    public let bounds: ScanBounds
    public init(text: String?, bounds: ScanBounds) { self.text = text; self.bounds = bounds }
}
public struct ScanFeedback {
    public let value: String?
    public let ambiguous: Bool
}
public struct ScanTarget {
    private var delivered = false
    public init() {}
    public mutating func reset() { delivered = false }
    /// Capture immediately; the centre only disambiguates multiple visible codes.
    public mutating func update(_ candidates: [ScanCandidate], width: Double, height: Double) -> ScanFeedback {
        guard !delivered, width > 0, height > 0 else { return ScanFeedback(value: nil, ambiguous: false) }
        let visible = candidates.filter {
            let b = $0.bounds
            return b.right > b.left && b.bottom > b.top && (0...width).contains(b.centreX) && (0...height).contains(b.centreY)
        }
        let centred = visible.filter { $0.bounds.contains(width / 2, height / 2) }
        let target = visible.count == 1 ? visible.first : (centred.count == 1 ? centred.first : nil)
        let value = target?.text.flatMap { $0.isEmpty ? nil : $0 }
        if value != nil { delivered = true }
        return ScanFeedback(value: value, ambiguous: visible.count > 1 && value == nil)
    }
}
