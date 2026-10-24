import Foundation

/// Kind 33, both ways: whether a text field has keyboard focus on the Mac, so a device can raise its
/// soft keyboard. Generation 1 (`WindowList.textFocus`).
///
/// - Device → host, only to a host whose window list says `textFocus` 1 or more: any kind 33 subscribes
///   the connection (an empty payload is fine); `{"stop":true}` ends the subscription.
/// - Host → device, only to a connection that subscribed: this struct as JSON, once on subscription
///   (the state as it stands) and then on every change, never repeated unchanged.
///
/// Pure (Foundation only): the host's TextFocusWatcher does only what reads Accessibility, and
/// Tests/checks/text-focus checks this file with StreamProtocol's sources.
public struct TextFocus: Codable, Hashable, Sendable {
    /// `WindowList.textFocus` from a host that sends this.
    public static let generation = 1

    /// A text field, text area, combo box, search field or other editable element has focus.
    public var focused: Bool
    /// True for a password field. Omitted from the JSON otherwise.
    public var secure: Bool?
    /// How many times the window in front changed what it shows (a terminal's tab switched) since the host began
    /// reading; omitted while 0. A terminal keeps one text area focused whatever is clicked (Warp reports a
    /// single one for the whole window, tabs included), so the device cannot tell a click on a tab from a click
    /// on the prompt by the focus: a click after which this moved was a tab, one after which it did not was text.
    public var switches: Int?

    public init(focused: Bool, secure: Bool? = nil, switches: Int? = nil) {
        self.focused = focused
        self.secure = secure
        self.switches = switches
    }

    /// No text field has focus.
    public static let unfocused = TextFocus(focused: false)

    /// The roles of elements that take typed text.
    public static let textRoles: Set<String> = ["AXTextField", "AXTextArea", "AXComboBox", "AXSearchField", "AXSecureTextField"]
    /// The role and subrole of a password field.
    public static let secureRole = "AXSecureTextField"

    /// What the focused element's Accessibility role, subrole and editable flag say. A password field is
    /// `secure` whether it says so as role or as subrole. Any other element counts when its role is a
    /// text role or its editable flag is true (web content text inputs).
    public static func classify(role: String?, subrole: String?, editable: Bool?) -> TextFocus {
        if role == secureRole || subrole == secureRole { return TextFocus(focused: true, secure: true) }
        if let role, textRoles.contains(role) { return TextFocus(focused: true) }
        if editable == true { return TextFocus(focused: true) }
        return .unfocused
    }

    /// Whether a device's kind 33 is `{"stop":true}`. Any other payload, empty or not, subscribes.
    public static func isStop(_ payload: Data) -> Bool {
        struct Stop: Decodable { var stop: Bool? }
        return Wire.decode(Stop.self, from: payload)?.stop == true
    }

    /// A device's switch-off: `{"stop":true}`.
    public static var stopping: Data { Data(#"{"stop":true}"#.utf8) }
}

/// Counts the times the front window's title changed to another one, for `TextFocus.switches`. A terminal's
/// title carries a spinner and a status symbol that change as it works (`π ⠼ 🤖 SKOPRY: Analisar…`, then
/// `π > ⭐ SKOPRY: Analisar…`), so only its letters and digits are compared: the same tab keeps them, another
/// tab has other ones. Pure, so the check can drive it.
public struct TitleSwitchCounter: Sendable {
    public private(set) var count = 0
    private var key: String?

    public init() {}

    /// The letters and digits of `title`: what stays when a spinner or a status symbol changes.
    public static func key(of title: String) -> String {
        String(title.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) && !(0x2800...0x28FF).contains($0.value) })
    }

    /// A read of the front window's title (nil: it could not be read, which changes nothing). The first
    /// reading is the starting point, not a switch. Returns the count.
    @discardableResult
    public mutating func observe(title: String?) -> Int {
        guard let title else { return count }
        let now = Self.key(of: title)
        if let key, key != now { count += 1 }
        key = now
        return count
    }
}

/// What a device is shown, which decides whose text focus it may hear.
public enum TextFocusScope: Equatable, Sendable {
    /// Nothing streams.
    case nothing
    /// The whole Desktop: the app in front is what the device sees.
    case desktop
    /// One app's window (the process id of the app that owns it).
    case app(Int32)
}

extension TextFocus {
    /// The focus a device is told: the text focus of the app it is shown. A text field in another app (a
    /// terminal in front on the Mac while the device watches Slack) says nothing about what the device
    /// shows, so the keyboard must not rise for it. `frontmost` is the process id of the app the focus was
    /// read from, nil when it could not be told.
    public static func scoped(_ focus: TextFocus, frontmost: Int32?, scope: TextFocusScope) -> TextFocus {
        switch scope {
        case .nothing: return .unfocused
        case .desktop: return focus
        case .app(let pid): return frontmost == pid ? focus : .unfocused
        }
    }
}

/// The subscriptions and what each subscriber was last sent: the state machine behind the host's
/// TextFocusWatcher, pure so the check can drive it. `ID` is a connection's identity.
public struct TextFocusTracker<ID: Hashable> {
    /// The subscribers, in the order they subscribed.
    public private(set) var subscribers: [ID] = []
    /// The state last read while someone subscribed; nil until the first read of a session.
    public private(set) var current: TextFocus?
    /// Subscribed, and not yet sent the state.
    private var unsent: Set<ID> = []

    public init() {}

    /// A subscription: true when `id` is new.
    public mutating func subscribe(_ id: ID) -> Bool {
        guard !subscribers.contains(id) else { return false }
        subscribers.append(id)
        unsent.insert(id)
        return true
    }

    /// A stop or a disconnect: true when `id` was subscribed.
    @discardableResult
    public mutating func unsubscribe(_ id: ID) -> Bool {
        guard let index = subscribers.firstIndex(of: id) else { return false }
        subscribers.remove(at: index)
        unsent.remove(id)
        if subscribers.isEmpty { current = nil }
        return true
    }

    /// A read of the Mac's state: what to send and to whom. On a change, the state goes to every
    /// subscriber; otherwise only to those not yet sent it (a new subscriber's state). Nothing without
    /// subscribers.
    public mutating func observe(_ state: TextFocus) -> [(id: ID, state: TextFocus)] {
        guard !subscribers.isEmpty else { return [] }
        let changed = state != current
        current = state
        let recipients = changed ? subscribers : subscribers.filter { unsent.contains($0) }
        unsent = []
        return recipients.map { (id: $0, state: state) }
    }
}
