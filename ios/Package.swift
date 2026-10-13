// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ClarIDCore",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [.library(name: "ClarIDCore", targets: ["ClarIDCore"])],
    targets: [
        .target(name: "ClarIDCore", path: "Core"),
        .testTarget(name: "ClarIDCoreTests", dependencies: ["ClarIDCore"], path: "Tests")
    ]
)
