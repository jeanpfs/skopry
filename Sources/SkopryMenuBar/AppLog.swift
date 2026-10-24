import SkopryHostCore

/// The app's own lines go where the host's go: stdout, the Log window and the log file. Shadows
/// `Swift.print` in this module only (SkopryHostCore has its own, see HostLog.swift).
func print(_ items: Any..., separator: String = " ", terminator: String = "\n") {
    HostLog.shared.line(items.map { String(describing: $0) }.joined(separator: separator))
}
