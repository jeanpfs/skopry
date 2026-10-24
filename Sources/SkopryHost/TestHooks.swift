import Foundation

/// The TEST ONLY variables that bear on who gets in or what pairing needs
/// (docs/home-pairing-plan.md §4.3). Each is read where it acts, and honoured only on a test host
/// (DoorPolicy.isTestHost: one that does not advertise and is not a .app's executable): any process
/// of this user can start Skopry.app's own executable with an environment, and it then runs with
/// Skopry's Screen Recording and Accessibility grants. Anywhere else each one set is ignored with one
/// line, here. The encoder's variables let nobody in and are not among them;
/// SKOPRY_TEST_REMOTE_DIR is read, and refused, where the identity store is chosen (the CLI and the
/// app).
enum TestHooks {
    static let doorAndPairing = [
        "SKOPRY_TEST_SERVICE_TYPE", "SKOPRY_TEST_SWAP_FAIL", "SKOPRY_TEST_PEER_TO_PEER_INTERFACE", "SKOPRY_TEST_ORIGIN",
        "SKOPRY_TEST_REMOTE_ORIGIN",
        "SKOPRY_TEST_LOOPBACK", "SKOPRY_TEST_BACKOFF_SECONDS", "SKOPRY_TEST_PAIRING_TTL", "SKOPRY_TEST_NO_ROUTER",
        "SKOPRY_TEST_CABLE_INTERFACE", "SKOPRY_TEST_LOCKED", "SKOPRY_TEST_ASK_FROM_THIS_MAC", "SKOPRY_TEST_ASK_QUIET",
        // The device gate's (DeviceGate): a floor above "0" refuses devices.
        "SKOPRY_TEST_MIN_DEVICE_VERSION", "SKOPRY_TEST_GOODBYE",
    ]

    /// One line for each such variable set on a host that does not honour them; nothing on a test
    /// host, and nothing when none is set (so every existing output stays as it was).
    static func reportIgnored(testHost: Bool, environment: [String: String] = ProcessInfo.processInfo.environment) {
        guard !testHost else { return }
        for name in doorAndPairing where !(environment[name] ?? "").isEmpty {
            print("\(name) ignored: only a test host takes it (one that does not advertise and is not Skopry.app itself).")
        }
    }
}
