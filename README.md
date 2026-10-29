# Skopry

Any window from your Mac, on your Android, iPhone and iPad.

Skopry shows a window from your Mac, or the whole desktop, on your iPhone or
iPad. You use it there with touch, a trackpad, a keyboard or Apple Pencil.
Skopry for Mac captures the window, encodes it as HEVC video and sends it to the
Skopry app on your device. Your taps, clicks, scrolls and keys go back to your
Mac. Your Mac and your device connect on the same network, over a USB cable,
with Direct Wireless Connection when they share no network, or through your
own VPN, such as Tailscale, with Remote Access. There is no account and no
server in between.

Skopry started as a fork of [Sill](https://github.com/Saffsanity/sill) by Noah
Saffer and is now its own project (see [License and origin](#license-and-origin)).
It has three parts: **Skopry for Mac** (the menu bar app that captures and
streams), **Skopry for Android** (`android/`) and **Skopry for iPhone and iPad**
(`iOSClient/`). There are no published downloads yet: build it from source
(below). The site, [skopry.app](https://skopry.app), is being set up.

[![CI](https://github.com/jeanpfs/skopry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jeanpfs/skopry/actions/workflows/ci.yml)

## Requirements

- A Mac with Apple silicon, on macOS 14 or later.
- An Android phone or tablet on Android 10 (API 29) or later, or an iPhone or iPad with iOS 17 or iPadOS 17 or later.
- On your Mac, Skopry asks for Screen Recording, to show your windows, and
  Accessibility, to click, scroll and type for you and to move and size the
  window it shows. On macOS 15 or later, also allow Local Network. On your
  iPhone or iPad, Skopry asks for Local Network, to find your Mac, and for the
  camera when you scan your Mac's pairing code (you can type the code
  instead).
- Your Mac and your device on the same network, joined by a USB cable, or near
  each other with Direct Wireless Connection on. Away from home, your own VPN,
  such as Tailscale, with Remote Access on.

## How it works

- **Skopry for Mac** lives in the menu bar. When your device picks a window, or
  the whole desktop, Skopry captures it with ScreenCaptureKit and encodes it as
  HEVC with VideoToolbox, on the hardware video encoder in your Mac (or, while
  that is busy, in software). It captures nothing while no device is connected.
- **The picture** goes over an encrypted connection (TLS 1.3) straight to the
  Skopry app on your phone or tablet, which decodes and shows it. When the
  encoder or the link falls behind, Skopry drops frames rather than queue them,
  so the picture stays current.
- **Your input** goes back the same way: taps and clicks, scrolls with
  momentum, the on-screen trackpad, keys and typed text, and Apple Pencil as
  the pointer. Skopry for Mac turns it into mouse and keyboard events, which
  needs Accessibility. A keyboard attached to your device types on your Mac as
  soon as the picture shows, Esc included, and a trackpad or a mouse scrolls
  the window under its pointer. Three fingers swipe and pinch as on a Mac's
  trackpad: Mission Control, App Exposé, the Spaces, Apps and Show Desktop,
  through the keyboard shortcuts your Mac has for them.
- **On iPhone**, held upright, the picture is on top with buttons, keys and a
  trackpad below. Larger Picture, in the settings panel, gives a window's
  picture the room of the window thumbnails, down to where the keyboard comes
  up; the Desktop keeps the usual layout. A window keeps its own shape until
  you set its text size with Aa, or turn on the virtual display; then your Mac
  makes it fill the taller picture.
- **The clipboard** goes both ways while Share Clipboard is on, in both apps:
  text you copy on your Mac is ready to paste on your device, and Paste in
  Skopry sends what you copied on your device to your Mac's clipboard, where you
  press ⌘V. Skopry never reads your device's clipboard by itself, and copies a
  password manager marks as concealed stay on your Mac.
- **On iPhone Duo**, Skopry follows the hinge: open it flat and your Mac fills
  the inner display; half open, the picture is above the fold with a trackpad
  and keys below; closed, Skopry runs on the cover display. Nothing you touch
  sits on the fold.
- **The menus** of the app you use reach your device too: in the iPad's own
  menu bar on iPadOS 26 or later, and behind the Menus button in Skopry's bar.
  Skopry for Mac reads each menu from the app as you open it and chooses the
  item you pick, through Accessibility. When someone moves the mouse at your
  Mac, your device shows the pointer where it is.
- **Nearby**, your device finds your Mac with Bonjour on your local network.
  Over a USB cable, your Mac shows as Wired and the connection uses the cable.
  With no shared network, Direct Wireless Connection (off by default) connects
  over peer-to-peer Wi-Fi, the way AirDrop does.
- **Pairing**: you pair each device with your Mac once. Tap your Mac on
  the device, and your Mac shows a code: scan it, or type its 12 digits. Over
  a USB cable the device pairs by itself. From then on each end knows the
  other's key, and every connection, nearby or away, is pinned to those keys.
  In Skopry's Settings on your Mac, Devices lists the devices you paired, and
  you can remove any of them there.
- **Away from home**, Remote Access (off by default) lets the devices you
  paired reach your Mac through your own VPN or a port you forward on your
  router; a device learns how the next time it connects at home with Remote
  Access on. A device away from home gets a quality of its own, Low at Standard
  resolution until you pick another there or in Skopry's settings, so a slow
  connection never starts at your home quality; your home quality comes back
  as soon as a device at home connects. When your device comes home and your
  network lists your Mac, the session moves to your network by itself.
- **When the link can't keep up**, Skopry for Mac notices that it is holding
  frames back and tells your device, which says so over the picture and, in
  its Settings, offers a quality the link can carry.
- **Text size**: Aa on your device resizes the window you stream on your Mac,
  through Accessibility, so its text shows at the size you pick, and keeps it
  at that size while it streams. The window goes back to its own size and
  place when Skopry stops showing it: you pick another window or the Desktop,
  your last device disconnects, or you quit Skopry. If you moved it on your Mac
  meanwhile, it keeps its new place.
- **The virtual display** (off by default) moves a window you stream onto a
  display of its own on your Mac, so it keeps updating when other windows
  cover it. It uses a private macOS API, which is one reason Skopry for Mac comes
  from the website and not from the Mac App Store.

Good to know: Skopry shows one window, or the whole desktop, at a time, and it
does not play sound from your Mac. The clipboard carries text only, up to 256
KB at a time, and only between apps that both have it. Your Mac needs to be
awake, with Skopry open. Skopry may not be able to wake it. After a restart Skopry
opens only once someone logs in, with Launch at Login on. Only the iPhones and
iPads you paired can connect, unless you turn off Require pairing in Settings
› Devices; then any device on your network can, still encrypted. Pairing at
home came with Skopry for Mac 0.4.0 and Skopry for iPhone and iPad 0.5 (2), and
each needs the other: the iPhone and iPad app shows an older Skopry for Mac as
"Update Skopry", and an older iPhone and iPad app can't connect to Skopry for Mac
0.4.0 or later at home. Since 0.5.1 the two apps share one version number for
each release. With Skopry for Mac 0.3.1 or earlier, connections on your local
network, over the cable and over Direct Wireless Connection are not encrypted,
and anything that reaches your Mac there can connect without pairing: update
it.

## Building from source

You need a Mac with Xcode (Skopry is built with Xcode 27; the iPhone and iPad
app needs Xcode 27.1 or later, for the iPhone Duo's hinge and fold), selected
as the active developer directory, with its license accepted.

Skopry for Mac, from the repository's folder:

```
Scripts/make-app.sh --install --open
```

This builds Skopry.app with SwiftPM, signs it with your Apple Development
identity, replaces /Applications/Skopry.app and opens it. If that was the Skopry
for Mac you downloaded, grant Screen Recording and Accessibility again and
pair each device again: the download keeps its key and paired devices in the
data-protection keychain, which only a Developer ID build with the project's
provisioning profile reads, and a build of your own keeps them in your login
keychain. Without `--install` it only builds `.build/Skopry.app`. With no Apple
Development identity it signs ad hoc, and then Screen Recording and
Accessibility have to be granted again after every build.

For work on the host itself, `swift run -c release SkopryHost` runs the same
host on the command line, with one difference: unless you add `--pairing`, its
home door is plain and open, with no pairing and no encryption, and any device
on your network can connect to it, so run it only while developing. The App
Store's Skopry for iPhone and iPad never dials such a door ("Update Skopry"); a
Debug build from this repository can ([docs/DEVELOPMENT.md](docs/DEVELOPMENT.md),
Pairing at home). With `--synthetic` it streams a test pattern with no Screen
Recording needed.

The iPhone and iPad app:

1. Open `iOSClient/Skopry.xcodeproj`.
2. In the Skopry target's Signing & Capabilities, pick your team, and change the
   bundle identifier `app.skopry` to one of your own.
3. Run it on an iPhone or iPad on the same network as your Mac, and tap your
   Mac. The first time, a Skopry for Mac built from this repository asks you to
   pair: scan the code it shows, or connect the device with a USB cable.

[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has the rest: every command-line
flag, the test tools and the simulator harness, permissions and how to reset
them, Remote Access, troubleshooting and releases.

## Contributing

Issues are welcome: bug reports, questions and ideas. For a pull request, keep
to the conventions and decisions in [CLAUDE.md](CLAUDE.md): Swift and Apple
frameworks only, no third-party dependencies, nothing that needs a server, and
latency before picture quality. Open an issue before starting anything large.
Before a pull request, `Tests/checks/run-all.sh` runs the same pure checks as
CI, in about five minutes and with no device.
Contributions are accepted under the project's license. Please report a
security problem privately, as [SECURITY.md](SECURITY.md) says, not in a
public issue.

## License and origin

Skopry is licensed under the [Apache License 2.0](LICENSE).

Skopry is derived from **Sill**, which is Copyright 2026 Noah Saffer
(<https://github.com/Saffsanity/sill>, Apache License 2.0), and carries its
history. The modifications, including the Android client and the rename to
Skopry, are Copyright 2026 Jean Pierre. See [NOTICE](NOTICE).
