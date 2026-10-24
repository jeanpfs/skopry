# Security

Skopry lets an iPhone or iPad see and control a Mac, so a flaw in it matters.
If you find one, please report it privately.

## Reporting a problem

Use "Report a vulnerability" on this repository's Security tab
(https://github.com/jeanpfs/skopry/security/advisories/new). Say what you found,
how to reproduce it, and which versions of Skopry for Mac and of the Android,
iPhone and iPad apps you tried (Skopry for Mac's is at the foot of its Settings ›
General). Please don't open a public issue or pull request for it, and give a fix
time to ship before you publish anything.

Fixes ship in the newest release: Skopry for Mac from
[skopry.app](https://skopry.app/download), whose update check tells everyone
who has it, and the newest iPhone and iPad app.

## What is in scope

- The connection on the Mac's own networks, over a USB cable and with Direct
  Wireless Connection: what Skopry for Mac accepts, and what a device can make
  it do.
- Remote Access's connection, and pairing, at home and away: the QR code, the
  typed code, `skopry://pair` links, a USB cable's automatic pairing, Require
  pairing, and the paired devices and keys Skopry for Mac keeps in the keychain.
- The update check, which asks GitHub's releases feed about once a day and
  downloads nothing.
- The website, skopry.app.

Known, and by design: with Require pairing off (Settings › Devices), any
device on the Mac's own networks, one nearby while Direct Wireless Connection
is on, and any app on the Mac itself connect without pairing, still
encrypted. `SkopryHost`, the command-line host for development, keeps a plain
home door, with no pairing and no encryption, unless it runs with
`--pairing`. Skopry for Mac 0.3.1 and earlier had no pairing and no encryption
at home: update to the newest release.
