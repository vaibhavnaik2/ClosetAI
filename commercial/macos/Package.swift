// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "ClosetAICommercial",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "ClosetAICommercial", targets: ["ClosetAICommercial"])
    ],
    targets: [
        .executableTarget(
            name: "ClosetAICommercial",
            path: "Sources/ClosetAICommercial"
        )
    ]
)
