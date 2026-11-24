#!/usr/bin/env python3
"""Print hex strings of the given length (default 64) found in Nuvio's official APK, one per line."""
import re, sys, zipfile

apk = zipfile.ZipFile(sys.argv[1])
length = int(sys.argv[2]) if len(sys.argv) > 2 else 64
blob = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
seen = []
pattern = rb"(?<![0-9a-f])[0-9a-f]{%d}(?![0-9a-f])" % length
for m in re.finditer(pattern, blob):
    k = m.group().decode()
    if k not in seen:
        seen.append(k)
print("\n".join(seen))
