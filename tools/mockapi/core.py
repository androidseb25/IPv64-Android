"""Router, helpers and HTTP handler of the mock API. Route modules call route()/data()/page()."""
import json
import re
import sys
from datetime import datetime, timedelta, timezone

ROUTES = []  # (method, compiled regex, pattern, handler)
API = "/api/v2"  # cloud API base path (the app appends it to -PmockApi)

# Shared ids so cloud fixtures and the node mocks agree (node 1 is cloud-linked, node 2 is direct-only)
NODE1_ID = "nd_homelab01"
NODE2_ID = "nd_cluster02"


def route(method, pattern, value):
    """Register a route. pattern uses {name} placeholders (one path segment each; {name:path} = rest).
    value: JSON-serialisable dict/list (sent as-is) or callable(ctx) -> body | (status, body)."""
    rx = re.sub(r"\{(\w+):path\}", r"(?P<\1>.+)", pattern)
    rx = re.sub(r"\{(\w+)\}", r"(?P<\1>[^/]+)", rx)
    ROUTES.append((method.upper(), re.compile("^" + rx + "/?$"), pattern, value))


class Ctx:
    def __init__(self, method, path, params, query, body):
        self.method, self.path, self.params, self.query, self.body = method, path, params, query, body or {}


def data(x):
    return {"data": x}


def page(items, **meta):
    """List envelope {"data": [...], "meta": {...}}."""
    return {"data": items, "meta": {"total": len(items), "limit": 100, "offset": 0, **meta}}


def now():
    return datetime.now(timezone.utc).replace(microsecond=0)


def iso(minutes_ago=0, days_ago=0, hours_ago=0, seconds_ago=0):
    """RFC 3339 UTC timestamp relative to now (computed per request, so 'x min ago' stays fresh)."""
    t = now() - timedelta(minutes=minutes_ago, days=days_ago, hours=hours_ago, seconds=seconds_ago)
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def epoch(minutes_ago=0, days_ago=0, hours_ago=0, seconds_ago=0):
    return int((now() - timedelta(minutes=minutes_ago, days=days_ago, hours=hours_ago, seconds=seconds_ago)).timestamp())


def echo(ctx, **extra):
    """Plausible write response: the request body plus defaults."""
    return data({**extra, **ctx.body})


def resolve(method, path):
    for m, rx, pattern, value in ROUTES:
        if m == method:
            mt = rx.match(path)
            if mt:
                return pattern, value, mt.groupdict()
    return None, None, None


def make_handler():
    from http.server import BaseHTTPRequestHandler
    from urllib.parse import urlsplit, parse_qs, unquote

    class H(BaseHTTPRequestHandler):
        server_version = "MockAPI/1"

        def log_message(self, fmt, *args):  # quiet access log
            pass

        def _send(self, status, body):
            text = isinstance(body, str)  # str bodies (e.g. password range) go out as text/plain
            raw = b"" if body is None else body.encode() if text else json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "text/plain" if text else "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def _handle(self):
            u = urlsplit(self.path)
            path = unquote(u.path)
            n = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(n) if n else b""
            try:
                body = json.loads(raw) if raw and "json" in (self.headers.get("Content-Type") or "") else {}
            except ValueError:
                body = {}
            if path.startswith("/secntfy"):  # push registration of the SecNtfy library: accept everything
                # SecNtfy-Android 2.x erwartet ApiResponse {Status, Token, Message}; Token = Geräte-Token
                print(f"{self.command} {path} -> 200 (secntfy)", file=sys.stderr, flush=True)
                return self._send(200, {"Status": 200, "Token": "NTFY-DEVICE-mock-0001", "Message": "OK"})
            pattern, value, params = resolve(self.command, path)
            if pattern is None:
                print(f"UNMAPPED {self.command} {path}", file=sys.stderr, flush=True)
                return self._send(404, {"type": "about:blank", "title": "Not Found", "status": 404,
                                        "detail": f"mock has no route for {self.command} {path}"})
            ctx = Ctx(self.command, path, params, {k: v[-1] for k, v in parse_qs(u.query).items()},
                      body if isinstance(body, dict) else {})
            res = value(ctx) if callable(value) else value
            status, out = res if isinstance(res, tuple) else (200, res)
            print(f"{self.command} {path} -> {status}", file=sys.stderr, flush=True)
            self._send(status, out)

        do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = _handle

    return H
