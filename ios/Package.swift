// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "JIA",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "JIAClient", targets: ["JIAClient"])],
    targets: [
        .target(name: "JIAClient"),
        .testTarget(name: "JIAClientTests", dependencies: ["JIAClient"])
    ]
)
