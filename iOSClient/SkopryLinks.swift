import Foundation

/// Where Skopry sends people outside the app: Skopry's addresses on the web, written once, and its App
/// Store page. The site is skopry.app (to be set up): change `site` and the
/// download, support and privacy links and the connect screen's "Get it at …" all follow.
enum SkopryLinks {
    /// The site. Its host is what the connect screen prints.
    static let site = URL(string: "https://skopry.app")!
    /// Where the free Skopry for Mac is downloaded (the connect screen's footer).
    static let download = site.appending(path: "download")
    /// Help, and how to reach the developer, which guideline 1.5 wants inside the app as well as
    /// behind App Store Connect's Support URL (the connect screen's footer).
    static let support = site.appending(path: "support")
    /// The privacy policy, which guideline 5.1.1(i) wants linked inside the app as well as in
    /// App Store Connect (the connect screen's footer).
    static let privacy = site.appending(path: "privacy")
    /// The site as a person types it: its host alone, "skopry.app".
    static let siteName = site.host() ?? site.absoluteString

    /// Skopry's App Store page, https://apps.apple.com/app/id‹Apple ID›: the link under a Mac's update
    /// notice (a goodbye "update"). The Apple ID is the App Store Connect record's, known once the
    /// record exists. While the text is empty, no link shows (the update notice's own words say what to do).
    static let appStoreText = ""   // no App Store page yet: no link shows

    /// An https URL from `appStoreText`, else nil. DEBUG: `-SkopryAppStoreURL <url>` replaces it.
    static var appStore: URL? {
        #if DEBUG
        if let override = UserDefaults.standard.string(forKey: "SkopryAppStoreURL"), let url = https(override) { return url }
        #endif
        return https(appStoreText)
    }

    private static func https(_ text: String) -> URL? {
        guard let url = URL(string: text), url.scheme == "https", let host = url.host, !host.isEmpty else { return nil }
        return url
    }
}
