// swift-tools-version:5.9
// C2 round-trip harness: a Swift executable that links the prebuilt
// libanki_ios.a and calls the C ABI on the iOS simulator.
//
// Build (from this dir), for arm64 iphonesimulator:
//   swift build --triple arm64-apple-ios18.0-simulator \
//     -Xcc -isysroot -Xcc "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
//     -Xswiftc -sdk -Xswiftc "$(xcrun --sdk iphonesimulator --show-sdk-path)" \
//     -Xlinker -L -Xlinker <repo>/target/aarch64-apple-ios-sim/debug \
//     -Xlinker -lanki_ios
// See ios/RoundTrip/run-on-sim.sh for the full invocation + simctl spawn.
import PackageDescription

let package = Package(
    name: "RoundTrip",
    targets: [
        .target(
            name: "CAnkiIOS"
        ),
        .executableTarget(
            name: "RoundTrip",
            dependencies: ["CAnkiIOS"],
            linkerSettings: [
                .linkedFramework("CoreFoundation"),
                .linkedFramework("Security"),
                .linkedFramework("SystemConfiguration"),
                .linkedLibrary("c++"),
            ]
        ),
    ]
)
