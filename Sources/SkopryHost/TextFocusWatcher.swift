import ApplicationServices
import Network
import StreamProtocol

/// The text focus, the host's side (kind 33; TextFocus.swift). A device that subscribes is sent whether a
/// text field has focus on the Mac: the state as it stands at once, then every change. The rules are
/// TextFocusTracker's; this reads Accessibility and keeps which connections subscribed.
///
/// While at least one connection subscribes, the Mac's focus is read four times a second, each read
/// awaited before the next is scheduled, so reads never overlap. With no subscriber nothing is read.
@MainActor
final class TextFocusWatcher {
    /// How often the Mac's focus is read while a device subscribes.
    static let interval = 0.25

    private let server: StreamServer
    /// The Accessibility reader; nil on a synthetic host, which never reads the Mac's focus and reports
    /// it unfocused.
    private let focus: AccessibilityFocus?
    /// What the device is shown, which decides whose focus it hears (`TextFocus.scoped`): set by the
    /// coordinator, which knows the streamed source. A host that has not set it treats it as the Desktop.
    var scope: @MainActor () -> TextFocusScope = { .desktop }
    private var tracker = TextFocusTracker<ObjectIdentifier>()
    private var switchCounter = TitleSwitchCounter()
    private var connections: [ObjectIdentifier: NWConnection] = [:]
    private var look: Task<Void, Never>?
    private var nudged: Task<Void, Never>?

    init(server: StreamServer, focus: AccessibilityFocus?) {
        self.server = server
        self.focus = focus
    }

    /// A kind 33 from `connection`: `{"stop":true}` ends its subscription, anything else subscribes.
    func received(_ payload: Data, from connection: NWConnection) {
        let id = ObjectIdentifier(connection)
        if TextFocus.isStop(payload) {
            drop(id)
            return
        }
        guard tracker.subscribe(id) else { return }
        connections[id] = connection
        startLooking()
    }

    /// The device left: forgotten without a word.
    func clientLeft(_ connection: NWConnection) {
        drop(ObjectIdentifier(connection))
    }

    private func drop(_ id: ObjectIdentifier) {
        guard tracker.unsubscribe(id) else { return }
        connections[id] = nil
        if tracker.subscribers.isEmpty { stopLooking() }
    }

    private func startLooking() {
        guard look == nil else { return }
        look = Task { @MainActor [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                let state = await self.readState()
                guard !Task.isCancelled else { return }
                self.observed(state)
                try? await Task.sleep(for: .seconds(TextFocusWatcher.interval))
            }
        }
    }

    /// A click was just delivered: what it did to the focus and to the front window's title (a terminal's tab
    /// switched) shows within a few tens of ms, and the device is waiting for it to decide about the keyboard,
    /// so look once more after `delay` instead of at the next quarter second.
    func nudge(after delay: TimeInterval = 0.12) {
        guard !tracker.subscribers.isEmpty else { return }
        nudged?.cancel()
        nudged = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled, let self, !self.tracker.subscribers.isEmpty else { return }
            let state = await self.readState()
            guard !Task.isCancelled else { return }
            self.observed(state)
        }
    }

    private func stopLooking() {
        nudged?.cancel(); nudged = nil
        look?.cancel()
        look = nil
        focus?.release()
    }

    /// The Mac's focus, or nil when it could not be read (an app that does not answer): then nothing is
    /// sent and the last state stands.
    private func readState() async -> TextFocus? {
        guard let focus else { return .unfocused }
        guard let reading = await focus.read() else { return nil }
        var state = TextFocus.scoped(reading.focus, frontmost: reading.pid, scope: scope())
        let switches = switchCounter.observe(title: reading.title)
        if switches > 0 { state.switches = switches }
        return state
    }

    private func observed(_ state: TextFocus?) {
        guard let state else { return }
        let sends = tracker.observe(state)
        guard !sends.isEmpty else { return }
        let payload = Wire.encode(state)
        let message = StreamMessage(kind: .textFocus, timestamp: Date().timeIntervalSince1970, isKeyframe: false, payload: payload)
        server.send(each: sends.compactMap { send in connections[send.id].map { ($0, message) } })
    }
}

/// Reads the Mac's focused element through Accessibility, on its own serial queue, so a slow app never
/// stalls the host. It turns on `AXManualAccessibility` for the frontmost app only (Chromium and Electron
/// expose their web content only when an assistive client asks), and turns it off again on `release`.
final class AccessibilityFocus: @unchecked Sendable {
    /// Each AX call waits at most this long for an app: a hung app reads as unreadable, not as a stall.
    private static let timeout: Float = 0.2

    private let queue = DispatchQueue(label: "skopry.text-focus", qos: .userInitiated)
    /// The apps this reader turned accessibility on for; on `queue`.
    private var enabled: Set<pid_t> = []
    /// The failures already put in the log; on `queue`.
    private var noted: Set<String> = []

    /// What one read found: the focus, and the process id of the app in front it was read from.
    struct Reading: Sendable {
        var focus: TextFocus
        var pid: Int32
        /// The title of the app's front window, when it could be read.
        var title: String? = nil
    }

    /// The Mac's focus now, or nil when it could not be read. On `queue`, one read at a time.
    func read() async -> Reading? {
        await withCheckedContinuation { done in
            queue.async { done.resume(returning: self.readNow()) }
        }
    }

    /// Turns accessibility off again for every app it was turned on for. Nothing is read meanwhile.
    func release() {
        queue.async {
            for pid in self.enabled {
                AXUIElementSetAttributeValue(AXUIElementCreateApplication(pid), "AXManualAccessibility" as CFString, kCFBooleanFalse)
            }
            self.enabled = []
        }
    }

    private func readNow() -> Reading? {
        let system = AXUIElementCreateSystemWide()
        AXUIElementSetMessagingTimeout(system, Self.timeout)
        let app = attribute(system, "AXFocusedApplication")
        guard app.error == .success, let value = app.value else {
            note("the frontmost app: AX error \(app.error.rawValue)")
            return nil
        }
        let application = value as! AXUIElement
        AXUIElementSetMessagingTimeout(application, Self.timeout)
        var pid: pid_t = 0
        guard AXUIElementGetPid(application, &pid) == .success else { return nil }
        if !enabled.contains(pid) {
            AXUIElementSetAttributeValue(application, "AXManualAccessibility" as CFString, kCFBooleanTrue)
            enabled.insert(pid)
        }
        let title = frontTitle(of: application)
        let focused = attribute(system, "AXFocusedUIElement")
        switch focused.error {
        case .success:
            guard let value = focused.value else { return Reading(focus: .unfocused, pid: pid, title: title) }
            let element = value as! AXUIElement
            AXUIElementSetMessagingTimeout(element, Self.timeout)
            return Reading(focus: TextFocus.classify(role: attribute(element, "AXRole").value as? String,
                                                     subrole: attribute(element, "AXSubrole").value as? String,
                                                     editable: attribute(element, "AXEditable").value as? Bool), pid: pid, title: title)
        case .noValue:
            return Reading(focus: .unfocused, pid: pid, title: title)
        default:
            note("the focused element: AX error \(focused.error.rawValue)")
            return nil
        }
    }

    private func frontTitle(of application: AXUIElement) -> String? {
        guard let window = attribute(application, "AXFocusedWindow").value, CFGetTypeID(window) == AXUIElementGetTypeID() else { return nil }
        return attribute(window as! AXUIElement, "AXTitle").value as? String
    }

    /// One line in the log the first time a kind of read fails, so a host that cannot see the Mac's focus
    /// (Accessibility off for this build, an app that does not answer) says so instead of staying silent.
    private func note(_ what: String) {
        guard noted.insert(what).inserted else { return }
        print("Text focus: could not read \(what) (Accessibility granted to Skopry?).")
    }

    private func attribute(_ element: AXUIElement, _ name: String) -> (value: CFTypeRef?, error: AXError) {
        var value: CFTypeRef?
        let error = AXUIElementCopyAttributeValue(element, name as CFString, &value)
        return (value, error)
    }
}
