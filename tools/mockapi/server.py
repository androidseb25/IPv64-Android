#!/usr/bin/env python3
"""Mock of the IPv64 cloud API (v2), Healthchecks 2.0 and two Node64 nodes, dummy data only.
Usage: python3 tools/mockapi/server.py --port 8064"""
import argparse
import os
import sys
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import core  # noqa: E402

for mod in ("cloud_a", "cloud_b", "hc2", "node"):
    __import__(mod).register()

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8064)
    ap.add_argument("--host", default="0.0.0.0")
    a = ap.parse_args()
    print(f"mock api on http://{a.host}:{a.port} ({len(core.ROUTES)} routes)", file=sys.stderr, flush=True)
    ThreadingHTTPServer((a.host, a.port), core.make_handler()).serve_forever()
