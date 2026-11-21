#!/usr/bin/env python3
"""Read Nuvio's account-server address and public (anon) key out of Nuvio's official APK
and print them as local.properties lines, so login and nuvio.tv addon sync keep working.
Nothing is stored in this repository."""
import base64, json, re, sys, zipfile

apk = zipfile.ZipFile(sys.argv[1])
blob = b"".join(apk.read(n) for n in apk.namelist() if n.endswith(".dex"))

def anon_key():
    for m in re.finditer(rb"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}", blob):
        tok = m.group().decode()
        part = tok.split(".")[1]
        try:
            payload = json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))
        except Exception:
            continue
        if payload.get("role") == "anon":
            return tok
    return None

avatars = re.search(rb"(https://[A-Za-z0-9.\-]+)/storage/v1/object/public/avatars", blob)
fallback = re.search(rb"https://api-[a-z0-9\-]+\.nuvioapp\.space", blob)
key = anon_key()
if not (avatars and key):
    sys.exit("Couldn't find Nuvio's account server details in the APK")
url = avatars.group(1).decode()
print(f"NUVIO_SUPABASE_URL={url}")
print(f"NUVIO_SUPABASE_ANON_KEY={key}")
print(f"NUVIO_SUPABASE_FALLBACK_URL={fallback.group().decode() if fallback else ''}")
print(f"AVATAR_PUBLIC_BASE_URL={url}/storage/v1/object/public/avatars")
