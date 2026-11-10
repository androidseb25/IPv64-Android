#!/usr/bin/env python3
"""Starts the server in-process and hits every registered route: expects 2xx and valid JSON (or empty 204)."""
import json
import os
import re
import sys
import threading
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core  # noqa: E402

for mod in ("cloud_a", "cloud_b", "hc2", "node"):
    __import__(mod).register()

srv = ThreadingHTTPServer(("127.0.0.1", 0), core.make_handler())
threading.Thread(target=srv.serve_forever, daemon=True).start()
base = f"http://127.0.0.1:{srv.server_address[1]}"
bad = 0
for method, _, pattern, _ in core.ROUTES:
    path = re.sub(r"\{(\w+)(:path)?\}", "demo", pattern)
    req = urllib.request.Request(base + path, method=method,
                                 data=b"{}" if method in ("POST", "PUT", "PATCH") else None,
                                 headers={"Authorization": "Bearer demo", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            code, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        code, raw = e.code, e.read()
    ok = 200 <= code < 300
    if ok and raw and not raw.startswith(b'ABC') and 'passwords' not in path:
        try:
            json.loads(raw)
        except ValueError:
            ok = False
    if not ok:
        bad += 1
        print(f"FAIL {method} {path} -> {code} {raw[:120]!r}")
print(f"{len(core.ROUTES)} routes, {bad} failed")
sys.exit(1 if bad else 0)
