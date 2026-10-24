// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Skopry",
    platforms: [.macOS(.v14), .iOS(.v17)],
    products: [
        .library(name: "StreamProtocol", targets: ["StreamProtocol"]),
        // The CLI keeps the product name SkopryHost, so `swift run -c release SkopryHost …` and
        // .build/release/SkopryHost are what they always were.
        .executable(name: "SkopryHost", targets: ["SkopryHostCLI"]),
        // Skopry.app's executable. Scripts/make-app.sh wraps it into the bundle and signs it.
        .executable(name: "SkopryMenuBar", targets: ["SkopryMenuBar"]),
        // No product for SkopryHostCore: the iOS project links this package and must never see it.
    ],
    targets: [
        .target(name: "StreamProtocol"),
        // The Mac host itself (capture, encode, network, input, virtual display), shared by the CLI
        // and the menu bar app; they reach it through `package` access. The folder keeps its old
        // name (Sources/SkopryHost) so the paths in the docs and the VirtualDisplayProbe symlink to
        // ../SkopryHost/VirtualDisplay.swift stay valid.
        .target(name: "SkopryHostCore", dependencies: ["StreamProtocol"], path: "Sources/SkopryHost"),
        // The command-line host: flags, the run loop, the Terminal permission hint.
        .executableTarget(name: "SkopryHostCLI", dependencies: ["SkopryHostCore", "StreamProtocol"]),
        // The menu bar app: status item, Settings and Log windows (AppKit lifecycle, SwiftUI panes).
        .executableTarget(name: "SkopryMenuBar", dependencies: ["SkopryHostCore", "StreamProtocol"]),
        // Milestone 3 spike: virtual display + window move + capture. Its VirtualDisplay.swift
        // is a symlink to the host's copy. Not shipped.
        .executableTarget(name: "CaptureProbe"),
        .executableTarget(name: "VirtualDisplayProbe"),
    ]
)
