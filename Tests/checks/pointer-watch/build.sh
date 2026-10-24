#!/bin/bash
# usage: build.sh WT OUT [PointerWatch.swift override]
# Compiles PointerWatch.swift with PointerControl.swift, Stats.swift and StreamProtocol's sources as
# one module (each `import StreamProtocol` stripped), with -package-name skopry for Stats' `package`
# access.
WT=$1; OUT=$2; T=$(mktemp -d)
cp "$WT"/Sources/StreamProtocol/*.swift "$T"/
sed '/^import StreamProtocol$/d' "${3:-$WT/Sources/SkopryHost/PointerWatch.swift}" > "$T/PointerWatch.swift"
sed '/^import StreamProtocol$/d' "$WT/Sources/SkopryHost/PointerControl.swift" > "$T/PointerControl.swift"
cp "$WT/Sources/SkopryHost/Stats.swift" "$T/Stats.swift"
cp "$(dirname "$0")/main.swift" "$T/main.swift"
swiftc -O -package-name skopry "$T"/*.swift -o "$OUT" 2>&1 | grep -E "error" ; rm -rf "$T"
