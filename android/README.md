# Skopry for Android

The client half of Skopry for Android phones, tablets and foldables: a Mac window, or the whole
Desktop, on the device, used with touch, a trackpad, a keyboard, a mouse or the S Pen. It speaks
the same wire protocol as the iPhone and iPad app (`Sources/StreamProtocol`), so **Skopry for Mac
needs no change to serve it**: any Skopry for Mac from 0.4.0 on (TLS doors with pairing).

## Using it over Tailscale

1. On the Mac: Skopry for Mac with **Remote Access** on (Skopry › Settings › Remote Access), and
   Tailscale running.
2. On the Android device: the Tailscale app, signed in to the same tailnet.
3. On the Mac, choose **Pair iPhone or iPad…** in Skopry's menu. Its window shows a QR code, a
   12-digit code and the address to type (the Mac's MagicDNS name, `mac.tailXXXX.ts.net`, with
   its `100.x` address under it).
4. In Skopry on Android, **Add a Mac**: tap **Scan Code**, or type the address and the code, then
   **Pair**. The device pairs once and connects; from then on a tap on the Mac connects, from
   anywhere on the tailnet.

At home, on the Mac's own Wi‑Fi, the paired Mac's row reads **Wi‑Fi** and the session goes
through the Mac's home door (home quality, the LAN's latency), Tailscale only when that does not
answer. A Mac on the same Wi‑Fi that this device has not paired with is listed under **On this
network**: a tap asks the Mac to show its code (as on iOS), then type it.

Away from home the Mac streams at its **away quality** (Low · Standard unless changed); Settings on
the device changes it like the Mac's own menu does.

## Sharp text

Text looked soft because the Mac streams a window at Standard (1 pixel per Mac point) unless told
otherwise, away from home included, and a phone or tablet then stretches 740 pixels over 2 400 of
its own. Two things fix it, and neither raises the bitrate:

- The viewport (kind 9) carries the panel's size in **pixels** (`pixelWidth`, `pixelHeight`) next to
  its size in dp. A Mac that knows it captures each source at the panel's own pixels per Mac point
  (`Viewport.captureScale`), never above the configured scale and never below 1×: a Desktop of 1 512
  points on a 2 418 px panel goes out at 2 418 × 1 570, not at 1 512 or at a 3 024 Retina frame the
  panel could not show. An older Mac ignores the two fields; an older client leaves them out.
- The device asks for **Retina** once per session (a person's own Standard in Settings is kept),
  since the pixel size now keeps Retina as cheap as the panel allows. The picture is drawn exactly
  1:1 when it fits within half a percent, so it is not resampled on the way to the screen.
- **Sound.** The Mac's sound plays on the device (AAC-ELD, decoded by MediaCodec, played on an AudioTrack with a small jitter buffer), with a Mute that is this device's own. "Send Audio" in Settings turns it on or off on the Mac; the device lowers other apps' volume while it plays and never pauses them.

Measured on a Z Fold8 against a text window (Mac side 740 × 460 points): the stream went from
740 × 460 to 1 480 × 920 and the 99th-percentile gradient of the text's edges from 45 to 111. Latency
did not rise: runs of 15 s with the window repainting ten times a second gave a frame age of 0 ms
median and 38–44 ms worst case after, against 15 and 99 ms before (the Tailscale round trip itself
moves from run to run). At rest the text is as sharp at 4 Mbps as at 150: a still frame converges,
so the bitrate only matters for motion.

## Building

Needs a JDK 17 or later and the Android SDK with platform 37 (`sdkmanager "platforms;android-37.0"`).

```
cd android
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # the pairing vectors, framing, JSON shapes, ledger, menus
./gradlew assembleRelease               # minified; signed with the debug key for local installs
```

Android 10 (API 29) or later: TLS 1.3 and ALPN on `SSLSocket` are what the Mac's doors require.

## Text sharpness

Sharpness is not a smooth function of the Text Size (Aa): any fractional enlargement of the picture costs most of
it, and only a 1:1 picture is exact. Measured on the same static text (edge width, lower is crisper; 1.41 is the
source's own):

| | edge width |
|---|---|
| Aa 1×, the panel enlarges the 2× capture by 1.31 (bilinear) | 1.88 |
| Aa 1× with Settings > Sharper Enlargement (bicubic) | 1.73 |
| Aa ¾×, the window sized to exactly half the panel's pixels, shown 1:1 | 1.52 |

So ¾× is the crisp setting on a Z Fold8 (the Mac captures the window at exactly 2.0× and nothing resamples it),
and the host snaps the window to that size when ¾× or 4/5× is chosen (`Viewport.crispWindowSize`). Sharper
Enlargement is for people who want 1×: a GL view with a Catmull-Rom filter in place of the compositor's bilinear
one. It is off by default because it costs about 1.5 points of CPU, ~115 MB of memory and a frame of display
delay (not measured), and it changes nothing at ¾× (identity at 1:1, measured).

## Coming back to the app

A decoder that is made again (its screen went away, a codec error) used to wait for the Mac's next keyframe,
up to 4 s: after one second away the picture took 2.7 s to come back, against 230 ms (the window's own
animation) after four or twenty. The decoder now keeps the frames since the last keyframe (`GopCache`, at most
32 MB, dropped whole when over) and decodes them again, without showing them, when it is made again; the last one
is shown. Measured after 1, 2 and 4 s away: 231 ms each.

Text Size (Aa) is remembered per app (`TextScales`): a choice made while an app streams is that app's, the
Desktop sets what the apps without a choice use. The app's own size goes to the Mac right after the pick, so the
window comes up already at it and the window on screen is not resized and put back.

## Typing

Typed text, accents and ç from the keyboard's long-press popup reach the Mac as Unicode text (checked with
Samsung Keyboard, PT·EN). A held backspace follows the keyboard's own repeat: Samsung Keyboard ramps from 400 ms
to 40 ms between deletes for about a second, then drops to a steady 222 ms and expects the field to delete a
word each time. The Mac's field is not in the keyboard's editor, so those slow events used to delete one
character and the deletion seemed to slow down (10, then 5 characters a second); a run of 8 gaps of 70 ms or
less marks the keyboard's repeat (a hand cannot tap that fast), and the slow events after it send ⌥⌫ (a word),
which holds about 38 characters a second (`BackspaceRepeat`). Taps, at any speed, delete one character each.

## Switching to another Android app

While a session is open, Skopry runs a foreground service (the ongoing "Skopry is connected to your Mac"
notification) so the connection survives another app being in front: Samsung's freezer otherwise aborted the
socket about 5 s after the app left the screen and the stream reconnected from the start. The decoder keeps
decoding without showing, so the picture is there at once on return and the Mac's resolution is untouched. A
session left in the background for 90 s is closed (the Mac stops streaming to nobody) and reconnects on return.
On Android 13+ allow notifications for Skopry if you want the notification shown; the service runs either way.

## Install and debug without a cable (Tailscale)

`adb` can reach the phone through the tailnet, so the app installs and debugs from anywhere both are on
it, and the Wi-Fi's client isolation (which hides the phone from the Mac on the LAN) does not matter.
Once, on the phone: Developer options > **Wireless debugging** on (Wi-Fi needed to switch it on), then
**Pair device with pairing code**; it shows a code and an address with a port. On the Mac:

```
scripts/adb-tailscale.sh pair PORT CODE     # PORT and CODE from that dialog, once per Mac
scripts/adb-tailscale.sh connect            # finds the connect port, which changes whenever Wireless debugging is switched on
scripts/install.sh                          # build, install the release, compile it, launch
scripts/install.sh --debug                  # the debuggable build, for adb logcat and profilers
```

Then `adb logcat`, `adb shell`, `simpleperf` and `adb install` work as with the cable (`scripts/adb-tailscale.sh
status` shows what adb sees; `off` disconnects). The connect port changes each time Wireless debugging is
switched off and on or the phone restarts: run `connect` again, no new pairing. For a port that stays,
plug the cable once after each restart and run `adb tcpip 5555`, then `adb connect <tailnet address>:5555`.

## Installing for daily use (performance)

Install the **release** build and have ART compile it at once. A debug build is 63 MB, runs with a
debuggable runtime and holds about 220 MB; the release build is 3 MB and holds about 130 MB. And a freshly
installed app interprets its code until ART has compiled it in the background (days, on a phone that is
used), which is where the first opening of a rail or the deck drops frames:

```
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package compile -m speed -f app.skopry.android
```

Measured on a Z Fold8 opening and closing the side rails 8 times over a stream, three runs each:

| | frames janky | 99th percentile | frames over the UI thread's budget |
|---|---|---|---|
| installed, not compiled (`verify`) | 5.0–8.5% | 20–27 ms | 11–19 |
| after the compile above (`speed`) | 3.3–5.0% | 10–16 ms | 0–4 |

(The panel is 120 Hz, so a frame past 8.3 ms counts as janky.) Streaming costs about 10% of one core
either way, and 60% of that is the system's MediaCodec; the app's own reader and decoder threads take
about 1% each. The frame rate (47–50 fps on a repainting window) and the text's sharpness do not change
with any of this.

## How it maps to the iOS app

| Android | iOS / shared |
|---|---|
| `protocol/Wire.kt`, `Payloads.kt`, `Blobs.kt` | `StreamMessage.swift` and the JSON of `Switcher`, `Input`, `Viewport`, `HostSettings`, `Remote`, `Compatibility`, `Pointer`, `MacMenu`, `Gesture` |
| `crypto/Pairing.kt` | `Pairing.swift`, `RemoteIdentity.swift` (Mac ID, SPKI pins, Damm code, PBKDF2 and HMAC proofs, the recognition tag, `skopry://pair`) |
| `crypto/Identity.kt` | `DeviceIdentity`, `RemoteTLS.swift`: a P-256 key in the Android Keystore, TLS 1.3, one ALPN, SNI `skopry`, the Mac's key pinned inside the handshake |
| `net/Pairer.kt`, `Session.kt`, `SavedMacs.kt`, `HomeDiscovery.kt` | `StreamClient`, `StreamClient+Remote`, `StreamClient+Home`, `RemoteConnector`, `RemoteDialPolicy`, `SavedMacs`, `MessageReader` |
| `client/SkopryClient.kt` | `StreamClient`: the session's life, the catalog, kinds 0–28, `GoodbyePolicy`, the reconnect |
| `client/SettingsLedger.kt`, `MenuState.kt`, `Keys.kt` | `HostSettingsLedger`, `MacMenuState`, `KeyChords` |
| `video/VideoDecoder.kt` | `HEVCDisplayView`: MediaCodec HEVC, Annex B, rendered as decoded |
| `ui/input/*` | `InputOverlay`, `TrackpadView`, `TrackpadGestures`, `ScrollMomentum` (the same thresholds) |
| `ui/StreamScreen.kt`, `Panels.kt`, `VideoPanel.kt`, `ConnectScreen.kt` | `StreamScreen`, `PortraitStreamScreen`, `HostSettingsPanel`, `MacMenuButton`, `ContentView` |

## Not like iOS (yet)

- **Decoder size.** Android phones' HEVC decoders often stop below a Retina capture (the Galaxy A15:
  2560×1440). When the Mac's stream is larger than this device decodes, Skopry asks the Mac for
  Standard resolution by itself and says so.
- No USB-cable pairing, Direct Wireless (AWDL is Apple's) or moving a live session between paths:
  a session that loses its path reconnects (home door first, then Tailscale).
- The Aa text size is a list, not a slider; no first-run tour; no window reordering in the strip.
- Typing goes through a "visible password" field so keyboards commit letters as typed; an IME that
  still composes is followed by its difference.

## Testing without a Mac app (DEBUG builds)

A synthetic host on loopback, reached through `adb reverse` (no firewall prompt, nothing posted):

```
mkdir -m 700 /tmp/skopryhost
SKOPRY_TEST_LOOPBACK=1 SKOPRY_TEST_SOFTWARE_ENCODER=1 SKOPRY_TEST_NO_ROUTER=1 SKOPRY_TEST_ORIGIN=vpn \
SKOPRY_TEST_REMOTE_DIR=/tmp/skopryhost SKOPRY_TEST_INPUT_LOG=1 .build/release/SkopryHost --synthetic --remote=47455
adb reverse tcp:47455 tcp:47455
adb shell am start -n app.skopry.android/.MainActivity --es pairAddress 127.0.0.1:47455 --es pairCode <the code it printed>
```

DEBUG-only extras: `pairUrl` (a `skopry://pair` link, no confirmation), `pairAddress` with `pairCode`,
`connect` (the first saved Mac), `forget`, `hud`, and for the home door `serviceType`
(`_skoprytest._tcp`), `homeOverride` (`127.0.0.1:PORT`, what the browser lists is reached there),
`tapHome` and `homeCode`. `SKOPRY_TEST_MENU_PID` (with `Scripts/menufixture.swift`) and
`SKOPRY_TEST_POINTER_PATH` exercise the menus and the Mac's pointer as on iOS.
