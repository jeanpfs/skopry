// The text focus (kind 33; TextFocus.swift, TextFocusTracker): the wire names and round trip, the
// window list's `textFocus` both ways, the stop payload, the classifier, and the tracker's state machine
// (the snapshot on subscription, sends on change only, unsubscription, no subscriber, no repeat).
import Foundation

var failures = 0
var checks = 0
func check(_ ok: Bool, _ what: @autoclosure () -> String, line: Int = #line) {
    checks += 1
    if !ok {
        failures += 1
        print("FAIL line \(line): \(what())")
    }
}
func json<T: Encodable>(_ value: T) -> String { String(decoding: Wire.encode(value), as: UTF8.self) }
/// The encoded value as a dictionary: key order in the encoder's output is not fixed, so compare content.
func object<T: Encodable>(_ value: T) -> NSDictionary {
    ((try? JSONSerialization.jsonObject(with: Wire.encode(value))) as? NSDictionary) ?? [:]
}

// MARK: Wire names and round trip

check(object(TextFocus(focused: true)) == ["focused": true], "a focused field is {\"focused\":true}: \(json(TextFocus(focused: true)))")
check(object(TextFocus(focused: true, secure: true)) == ["focused": true, "secure": true],
      "a password field carries secure: \(json(TextFocus(focused: true, secure: true)))")
check(object(TextFocus.unfocused) == ["focused": false], "no field: secure left out: \(json(TextFocus.unfocused))")
for state in [TextFocus.unfocused, TextFocus(focused: true), TextFocus(focused: true, secure: true)] {
    check(Wire.decode(TextFocus.self, from: Wire.encode(state)) == state, "round trip of \(json(state))")
}
check(Wire.decode(TextFocus.self, from: Data(#"{"focused":true}"#.utf8)) == TextFocus(focused: true),
      "a host's JSON without secure decodes, secure nil")
check(TextFocus.generation == 1, "the generation is 1")

// MARK: Kind 33 and the stop
check(StreamMessageKind(rawValue: 33) == .textFocus && StreamMessageKind(rawValue: 32) == .clipboard,
      "kind 33 is the text focus, 32 still the clipboard")
let message = StreamMessage(kind: .textFocus, timestamp: 1, isKeyframe: false, payload: Wire.encode(TextFocus(focused: true)))
check(StreamMessage.parseHeader(message.serialized())?.kind == .textFocus, "the kind 33 header parses")
check(TextFocus.isStop(TextFocus.stopping), "{\"stop\":true} stops")
check(TextFocus.stopping == Data(#"{"stop":true}"#.utf8), "the stop payload is {\"stop\":true}")
check(!TextFocus.isStop(Data()), "an empty kind 33 subscribes, it does not stop")
check(!TextFocus.isStop(Data("{}".utf8)), "{} subscribes")
check(!TextFocus.isStop(Data(#"{"stop":false}"#.utf8)), "stop false does not stop")
check(!TextFocus.isStop(Data("garbage".utf8)), "undecodable subscribes, it does not stop")

// MARK: The window list's textFocus

let shares = WindowList(macName: "Mac mini", windows: [], active: .desktop, launchID: "L", hostVersion: "0.6", protocol: 1,
                        gestures: 1, clipboard: 1, textFocus: TextFocus.generation)
check(json(shares).contains(#""textFocus":1"#), "a list says textFocus 1: \(json(shares))")
let withoutFocus = WindowList(macName: "Mac mini", windows: [], active: .desktop, launchID: "L", hostVersion: "0.6", protocol: 1,
                              gestures: 1, clipboard: 1)
check(!json(withoutFocus).contains("textFocus"), "a list without it leaves the key out")
let decodedShares = Wire.decode(WindowList.self, from: Wire.encode(shares))
check(decodedShares?.textFocus == 1 && decodedShares?.clipboard == 1 && decodedShares?.gestures == 1,
      "textFocus round-trips beside clipboard and gestures")
let decodedOlder = Wire.decode(WindowList.self, from: Wire.encode(withoutFocus))
check(decodedOlder != nil && decodedOlder?.textFocus == nil && decodedOlder?.gestures == 1,
      "an older host's list decodes, textFocus nil")

// MARK: The classifier

func focus(_ role: String?, _ subrole: String? = nil, _ editable: Bool? = nil) -> TextFocus {
    TextFocus.classify(role: role, subrole: subrole, editable: editable)
}
check(focus("AXTextField") == TextFocus(focused: true), "a text field has focus")
check(focus("AXTextArea") == TextFocus(focused: true), "a text area has focus")
check(focus("AXComboBox") == TextFocus(focused: true), "a combo box has focus")
check(focus("AXSearchField") == TextFocus(focused: true), "a search field has focus")
check(focus("AXSecureTextField") == TextFocus(focused: true, secure: true), "a secure field by role is secure")
check(focus("AXTextField", "AXSecureTextField") == TextFocus(focused: true, secure: true), "a secure field by subrole is secure")
check(focus("AXButton") == .unfocused, "a button has no focus")
check(focus(nil) == .unfocused, "a nil role has no focus")
check(focus(nil, nil, nil) == .unfocused, "nil role, subrole and editable has no focus")
check(focus("AXGroup", nil, true) == TextFocus(focused: true), "an editable web element has focus")
check(focus("AXGroup", nil, false) == .unfocused, "a non-editable group has no focus")
check(focus("AXStaticText", nil, nil) == .unfocused, "static text has no focus")
check(focus("AXButton", "AXSecureTextField") == TextFocus(focused: true, secure: true), "a secure subrole counts whatever the role")

// MARK: The tracker's state machine

typealias Tracker = TextFocusTracker<Int>
let unfocused = TextFocus.unfocused
let typing = TextFocus(focused: true)
let password = TextFocus(focused: true, secure: true)

do {
    var t = Tracker()
    check(t.observe(unfocused).isEmpty, "no subscriber: nothing is sent")
    check(t.subscribe(1), "first subscription is new")
    check(!t.subscribe(1), "a second kind 33 from the same connection is not new")
    let snapshot = t.observe(typing)
    check(snapshot.map(\.id) == [1] && snapshot.first?.state == typing, "a subscriber is sent the state at once")
    check(t.observe(typing).isEmpty, "an unchanged state is never repeated")
    let changed = t.observe(unfocused)
    check(changed.map(\.id) == [1] && changed.first?.state == unfocused, "a change is sent")
    check(t.observe(unfocused).isEmpty, "the same state again is not sent")
    check(t.observe(password).map(\.state) == [password], "a password field is a change too")
}

do {
    var t = Tracker()
    _ = t.subscribe(1)
    _ = t.observe(typing)
    check(t.subscribe(2), "a second device subscribes")
    let late = t.observe(typing)
    check(late.map(\.id) == [2] && late.first?.state == typing, "the new device is sent the current state only, once")
    check(t.observe(typing).isEmpty, "nothing is repeated to anyone")
    let moved = t.observe(unfocused)
    check(Set(moved.map(\.id)) == [1, 2] && moved.allSatisfy { $0.state == unfocused }, "a change goes to every subscriber")
    check(t.subscribers == [1, 2], "subscribers in the order they subscribed")
}

do {
    var t = Tracker()
    _ = t.subscribe(1)
    _ = t.subscribe(2)
    _ = t.observe(typing)
    check(t.unsubscribe(1), "a subscribed connection unsubscribes")
    check(!t.unsubscribe(1), "an unsubscribed one is not again")
    check(t.observe(unfocused).map(\.id) == [2], "a stopped connection gets no more sends")
    check(t.unsubscribe(2), "the last one unsubscribes")
    check(t.subscribers.isEmpty && t.observe(typing).isEmpty, "with no subscriber nothing is sent")
    check(t.current == nil, "the state is forgotten once nobody subscribes")
    check(t.subscribe(3), "a new session starts")
    let fresh = t.observe(typing)
    check(fresh.map(\.id) == [3] && fresh.first?.state == typing, "a new session sends its own snapshot, not a stale one")
}

do {
    var t = Tracker()
    _ = t.subscribe(1)
    _ = t.observe(typing)
    _ = t.unsubscribe(1)
    _ = t.subscribe(1)
    check(t.observe(typing).map(\.id) == [1], "a stopped device that subscribes again is sent the state")
}

// MARK: whose focus a device hears (TextFocus.scoped)

do {
    let typing = TextFocus(focused: true)
    let secure = TextFocus(focused: true, secure: true)
    let slack: Int32 = 101, warp: Int32 = 202
    check(TextFocus.scoped(typing, frontmost: warp, scope: .app(slack)) == .unfocused,
          "a text field in the terminal in front says nothing to a device that is shown Slack")
    check(TextFocus.scoped(typing, frontmost: slack, scope: .app(slack)) == typing,
          "a text field in the app the device is shown is heard")
    check(TextFocus.scoped(secure, frontmost: slack, scope: .app(slack)) == secure, "and a password field keeps its secure flag")
    check(TextFocus.scoped(.unfocused, frontmost: slack, scope: .app(slack)) == .unfocused, "no text field in the shown app is no focus")
    check(TextFocus.scoped(typing, frontmost: nil, scope: .app(slack)) == .unfocused, "an app in front that could not be told is not the shown one")
    check(TextFocus.scoped(typing, frontmost: warp, scope: .desktop) == typing, "the Desktop shows whatever is in front, so its text field counts")
    check(TextFocus.scoped(typing, frontmost: nil, scope: .desktop) == typing, "the Desktop does not need the app's process id")
    check(TextFocus.scoped(typing, frontmost: slack, scope: .nothing) == .unfocused, "nothing streaming has no text focus")
}

do {
    var c = TitleSwitchCounter()
    check(c.observe(title: "π ⠼ 🤖 SKOPRY: Analisar projeto") == 0, "the first title is the starting point")
    check(c.observe(title: "π ⠴ 🤖 SKOPRY: Analisar projeto") == 0, "a spinner turning is not a switch")
    check(c.observe(title: "π > ⭐ SKOPRY: Analisar projeto") == 0, "a status symbol changing is not a switch")
    check(c.observe(title: nil) == 0, "an unreadable title changes nothing")
    check(c.observe(title: "π > ⭐ ASO: Plan ASO") == 1, "another tab's title is a switch")
    check(c.observe(title: "π ⠋ ⭐ ASO: Plan ASO") == 1, "and its spinner is not another")
    check(c.observe(title: "UTM") == 2 && c.observe(title: "UTM") == 2, "each switch counts once")
    check(object(TextFocus(focused: true, switches: 2)) == ["focused": true, "switches": 2], "switches is in the JSON: \(json(TextFocus(focused: true, switches: 2)))")
    check(Wire.decode(TextFocus.self, from: Data(#"{"focused":true}"#.utf8))?.switches == nil, "a focus without it (an older host) reads")
    var tracker = TextFocusTracker<Int>()
    _ = tracker.subscribe(1)
    _ = tracker.observe(TextFocus(focused: true))
    check(tracker.observe(TextFocus(focused: true, switches: 1)).count == 1, "a switch with the focus unchanged is sent")
}

print("\(checks) checks, \(failures) failed")
exit(failures == 0 ? 0 : 1)
