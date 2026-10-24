#!/bin/bash
# adb over Tailscale: install and debug the app with no cable, from anywhere on the tailnet.
#
#   scripts/adb-tailscale.sh status             the phone on the tailnet, and what adb sees
#   scripts/adb-tailscale.sh pair PORT CODE     once: pair with the phone (Wireless debugging > Pair device with pairing code)
#   scripts/adb-tailscale.sh connect [PORT]     connect by the phone's tailnet address; without PORT, find it
#   scripts/adb-tailscale.sh scan               the TCP ports open on the phone in 30000-50000 (the adb ones are among them)
#   scripts/adb-tailscale.sh off                disconnect
#
# The phone is the tailnet's Android peer; SKOPRY_PHONE=100.x.y.z (or a MagicDNS name) picks another.
# Wireless debugging's port changes each time it is switched on, so `connect` finds it by trying what is open.
set -euo pipefail

phone() {
    if [ -n "${SKOPRY_PHONE:-}" ]; then echo "$SKOPRY_PHONE"; return; fi
    tailscale status --json | python3 -c '
import json, sys
peers = json.load(sys.stdin).get("Peer", {}).values()
android = [p for p in peers if p.get("OS") == "android"]
online = [p for p in android if p.get("Online")] or android
if not online: sys.exit("no Android peer on this tailnet (is Tailscale on in the phone?)")
print(online[0]["TailscaleIPs"][0])'
}

open_ports() {
    python3 - "$1" <<'PY'
import socket, sys, concurrent.futures as cf
ip = sys.argv[1]
def probe(p):
    s = socket.socket(); s.settimeout(0.5)
    try: s.connect((ip, p)); return p
    except OSError: return None
    finally: s.close()
with cf.ThreadPoolExecutor(400) as ex:
    print(" ".join(str(p) for p in ex.map(probe, range(30000, 50001)) if p))
PY
}

cmd=${1:-status}
ip=$(phone)
case "$cmd" in
    status)
        echo "phone: $ip"
        tailscale ping -c 1 --timeout 5s "$ip" | tail -1 || true
        adb devices -l | tail -n +2
        ;;
    pair)
        [ $# -ge 3 ] || { echo "usage: $0 pair PORT CODE" >&2; exit 2; }
        adb pair "$ip:$2" "$3"
        ;;
    scan)
        echo "open on $ip: $(open_ports "$ip")"
        ;;
    connect)
        adb disconnect >/dev/null 2>&1 || true
        sleep 1   # adb refuses a connect that follows its disconnect at once
        ports=${2:-$(open_ports "$ip")}
        [ -n "$ports" ] || { echo "nothing open on $ip in 30000-50000: switch Wireless debugging on in the phone's Developer options" >&2; exit 1; }
        for p in $ports; do
            out=$(adb connect "$ip:$p" 2>&1 || true)   # a port that is not the connect one can take its time to refuse
            case "$out" in
                connected*|already*) echo "$out"; adb devices -l | tail -n +2; exit 0 ;;
            esac
            adb disconnect "$ip:$p" >/dev/null 2>&1 || true
        done
        echo "found $ports on $ip but adb did not take any: pair first (\`$0 pair PORT CODE\`)" >&2
        exit 1
        ;;
    off)
        adb disconnect
        ;;
    *)
        sed -n '2,12p' "$0"; exit 2
        ;;
esac
