#!/usr/bin/env python3
"""Print candidate TMDB API keys (32 hex chars) found in Nuvio's official APK, one per line.
The build tests each against TMDB and uses the one that works."""
import re, sys, zipfile

apk = zipfile.ZipFile(sys.argv[1])
blob = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))
seen = []
for m in re.finditer(rb"(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])", blob):
    k = m.group().decode()
    if k not in seen:
        seen.append(k)
print("\n".join(seen))
