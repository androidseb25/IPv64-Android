"""Mock for Hc2Api (Healthchecks 2.0): account, settings, events, incidents, testruns, checks, channels, maintenance."""
import hashlib
import math

from core import API, NODE1_ID, Ctx, data, echo, epoch, iso, page, route  # noqa: F401

H = API + "/hc2"


def tok(name):
    return hashlib.md5(name.encode()).hexdigest()  # 32 chars like the real tokens


# (key, name, type, target, state, latency ms, interval s, group, tags, extra params, flags)
CHECK_DEFS = [
    ("web", "Website", "http", "https://demo.home64.de", "up", 84, 60, "Web", ["web", "prod"],
     {"method": "GET", "accept_status": "200-299", "follow_redirects": True, "max_redirects": 5}, {}),
    ("nas", "NAS Ping", "icmp", "nas.home64.de", "up", 12, 60, "Home", ["home"],
     {"count": 3, "max_loss_percent": 0, "max_latency_ms": 200}, {"execution": "local", "node": True}),
    ("vpn", "VPN Gateway", "tcp", "vpn.home64.de:51820", "down", None, 60, "Home", ["vpn"],
     {"port": 51820}, {"critical": True}),
    ("shop", "Shop Checkout", "keyword", "https://shop.example.com/cart", "degraded", 1840, 120, "Web", ["shop", "prod"],
     {"method": "GET", "accept_status": "200", "keyword": "Checkout", "keyword_absent": False}, {}),
    ("dns", "DNS demo.home64.de", "dns", "demo.home64.de", "up", 23, 300, "Web", ["dns"],
     {"record_type": "A", "resolver": "authoritative", "values": ["192.0.2.10"]}, {}),
    ("cam", "Camera Port", "port", "cam.home64.de:554", "paused", None, 120, "Home", ["home", "camera"],
     {"port": 554}, {"paused": True}),
    ("backup", "Nightly Backup", "push", None, "up", None, 86400, "Jobs", ["backup", "cron"],
     {"period_seconds": 86400, "grace_seconds": 3600}, {"push": True}),
    ("cert", "TLS Certificate", "cert", "demo.home64.de:443", "up", 140, 3600, "Web", ["tls"],
     {"min_days": 14, "cert_min_days": 14}, {}),
]
STATE_MSG = {
    "up": ("ok", "HTTP 200 OK"), "down": ("connect_timeout", "Connection timed out after 10 s"),
    "degraded": ("slow_response", "Response time above threshold"), "paused": ("paused", "Check paused"),
}


def _ok_msg(typ, lat):
    return {"icmp": "3/3 replies, %s ms" % lat, "tcp": "Connected", "port": "Port open", "dns": "Record resolved",
            "push": "Ping received", "cert": "Certificate valid"}.get(typ, "HTTP 200 OK")


def check(d, detail=False):
    key, name, typ, target, state, lat, interval, group, tags, params, fl = d
    t = tok(key)
    paused = fl.get("paused", False)
    local = fl.get("execution") == "local"
    reason, msg = STATE_MSG[state]
    msg = _ok_msg(typ, lat) if state == "up" else msg
    last = 40 + len(key)
    st = {"state": state, "since": iso(minutes_ago={"down": 47, "degraded": 22}.get(state, 60 * 24 * 6)),
          "last_check_at": iso(seconds_ago=last), "last_status": "ok" if state == "up" else "fail",
          "last_latency_ms": lat, "last_reason": reason, "last_message": msg}
    if state == "down":
        st["down_since"] = iso(minutes_ago=47)
    if fl.get("push"):
        st["last_push_at"] = iso(hours_ago=5)
    c = {"name": name, "type": typ, "target": target, "params": params, "interval_seconds": interval,
         "retry_interval_seconds": 30, "retries": 2, "connect_timeout_seconds": 5.0, "timeout_seconds": 10.0,
         "invert": False, "stabilize": 1, "ip_mode": "auto", "tags": tags, "group": group,
         "description": f"{name} (demo)", "paused": paused, "parent": None, "critical": fl.get("critical", False),
         "execution": fl.get("execution", "online"), "node_id": NODE1_ID if local else None, "nodes": [],
         "locations": [] if local else ["nbg", "fsn"], "policy": "any" if not local else "all",
         "notify_degraded": True, "notify_down": True, "notify_up": True, "channels": [1, 2, 3][: 3 if key != "cam" else 1],
         "repeat_minutes": 60, "token": t, "node_name": "homelab-01" if local else None, "revision": 3,
         "created_at": iso(days_ago=90), "updated_at": iso(days_ago=3), "state": st,
         "push": ({"url": f"https://ping.ipv64.net/hc2/push/{t}", "hub_url": f"https://ipv64.net/hc2/push/{t}",
                   "node_path": f"/push/{t}"} if fl.get("push") else None)}
    if detail:
        srcs = ([("node:" + NODE1_ID, "homelab-01")] if local else [("loc:nbg", "Nuremberg"), ("loc:fsn", "Falkenstein")])
        c["sources"] = [{"source": s, "label": lb, "state": state if i == 0 or state != "degraded" else "up",
                         "since": st["since"], "last_check_at": st["last_check_at"], "last_status": st["last_status"],
                         "last_latency_ms": lat, "last_reason": reason, "last_message": msg,
                         "consecutive_fail": 5 if state == "down" else 0, "consecutive_ok": 0 if state == "down" else 120}
                        for i, (s, lb) in enumerate(srcs)]
        c["availability_percent"] = {"24h": {"down": 96.7, "degraded": 99.2}.get(state, 100.0),
                                     "30d": {"down": 99.1}.get(state, 99.95), "1y": 99.87}
    return c


def find_def(token):
    return next((d for d in CHECK_DEFS if tok(d[0]) == token), CHECK_DEFS[0])


def measurements(ctx):
    d = find_def(ctx.params["token"])
    key, state, lat, interval = d[0], d[4], d[5], d[6]
    typ = d[2]
    local = d[10].get("execution") == "local"
    srcs = [("node:" + NODE1_ID, "homelab-01")] if local else [("loc:nbg", "Nuremberg"), ("loc:fsn", "Falkenstein")]
    limit = min(int(ctx.query.get("limit") or 300), 300)
    out, step = [], 5 if interval < 3600 else 60  # minutes
    n = min(limit, 24 * 60 // step + 40)  # ~24 h
    for i in range(n):
        for j, (s, lb) in enumerate(srcs):
            base = lat or 50
            ok = state in ("up", "degraded") or (state == "down" and i > 9)
            if d[10].get("paused") and i < 20:
                continue
            v = None if not ok or lat is None else round(base * (1 + 0.25 * math.sin(i / 4 + j) + 0.05 * ((i * 7 + j) % 5)), 1)
            m = {"time": iso(minutes_ago=i * step + j), "source": s, "source_label": lb,
                 "status": "up" if ok else "down", "latency_ms": v, "family": "ipv4"}
            if not ok:
                m.update(reason="connect_timeout", message="Connection timed out after 10 s")
            elif state == "degraded" and i < 12:
                m.update(reason="slow_response", message="Response time above threshold")
            if ok and not m.get("message"):
                m["message"] = _ok_msg(typ, v)
            out.append(m)
    return data(out[:limit])


def stats(ctx):
    d = find_def(ctx.params["token"])
    rng = ctx.query.get("range") or "24h"
    lat = d[5] or 60
    hours, bucket, unit = {"24h": (24, "1h", 60), "7d": (7 * 24, "1h", 60), "30d": (30 * 24, "1d", 1440),
                           "1y": (365, "1d", 1440)}.get(rng, (24, "1h", 60))
    pts = hours if unit == 60 else hours // 24 if hours > 365 else hours
    pts = min(pts, 168 if unit == 60 else 365)
    series = [{"time": iso(minutes_ago=i * unit), "source": "loc:nbg", "measurements": 12 if unit == 60 else 288,
               "availability_percent": 100.0 if not (d[4] == "down" and i < 1) else 21.0,
               "latency_avg_ms": round(lat * (1 + 0.2 * math.sin(i / 3)), 1),
               "latency_max_ms": round(lat * (1.6 + 0.2 * math.sin(i / 3)), 1)} for i in reversed(range(pts))]
    av = {"down": 96.7, "degraded": 99.2}.get(d[4], 100.0)
    return data({"range": rng, "bucket": bucket, "measurements": sum(p["measurements"] for p in series),
                 "availability_percent": av,
                 "sources": [{"source": s, "label": lb, "measurements": 1440, "availability_percent": av,
                              "latency_ms": {"min": round(lat * .6, 1), "avg": float(lat), "max": round(lat * 2.1, 1)}}
                             for s, lb in (("loc:nbg", "Nuremberg"), ("loc:fsn", "Falkenstein"))],
                 "series": series})


# (id, token key, kind, state transition, reason, message, minutes ago, duration s, incident id)
EVENT_DEFS = [
    (9021, "vpn", "down", "up", "down", "connect_timeout", "Connection timed out after 10 s", 47, None, 501),
    (9020, "shop", "degraded", "up", "degraded", "slow_response", "Response time 1840 ms above 1500 ms", 22, None, 502),
    (9019, "web", "up", "down", "up", "ok", "HTTP 200 OK", 60 * 5, 312, 500),
    (9018, "web", "down", "up", "down", "http_status", "HTTP 503 Service Unavailable", 60 * 5 + 5, None, 500),
    (9017, "backup", "up", "down", "up", "ok", "Ping received", 60 * 5 + 1, 5400, 499),
    (9016, "nas", "flapping_start", "up", "degraded", "flapping", "State changed 4 times in 10 minutes", 60 * 9, None, None),
    (9015, "nas", "flapping_end", "degraded", "up", "ok", "Stable again", 60 * 8, 3600, None),
    (9014, "cert", "up", "down", "up", "ok", "Certificate valid for 61 days", 60 * 30, 900, 498),
    (9013, "cam", "down", "up", "down", "refused", "Connection refused", 60 * 52, None, 497),
]


def event(e):
    id_, key, kind, frm, to, reason, msg, mins, dur, inc = e
    d = find_def(tok(key))
    return {"id": id_, "event_id": f"evt_{id_}", "token": tok(key), "source": "loc:nbg", "source_label": "Nuremberg",
            "kind": kind, "time": iso(minutes_ago=mins), "from_state": frm, "to_state": to, "reason": reason,
            "message": msg, "duration_seconds": dur, "flaps": 4 if kind == "flapping_start" else None,
            "silent": False, "incident_id": inc, "name": d[1]}


INCIDENT_DEFS = [
    (501, "vpn", "open", 47, None, "connect_timeout", "Connection timed out after 10 s"),
    (502, "shop", "open", 22, None, "slow_response", "Response time 1840 ms above 1500 ms"),
    (500, "web", "resolved", 60 * 5 + 5, 312, "http_status", "HTTP 503 Service Unavailable"),
    (499, "backup", "resolved", 60 * 30, 5400, "missed_ping", "No ping received within grace period"),
    (498, "cert", "resolved", 60 * 24 * 2, 900, "cert_expiry", "Certificate expired in 3 days"),
    (497, "cam", "resolved", 60 * 24 * 4, 1800, "refused", "Connection refused"),
]


def incident(i):
    id_, key, status, mins, dur, reason, msg = i
    return {"id": id_, "token": tok(key), "name": find_def(tok(key))[1], "status": status,
            "started_at": iso(minutes_ago=mins), "ended_at": iso(minutes_ago=mins - dur // 60) if dur else None,
            "duration_seconds": dur if dur else mins * 60, "reason": reason, "message": msg,
            "sources": ["loc:nbg", "loc:fsn"]}


CHANNEL_TYPES = [
    ("ipv64_mail", "E-Mail", True, [("address", "E-mail address", "email", True, False, [], None)]),
    ("ipv64_sms", "SMS", True, [("number", "Phone number", "text", True, False, [], None)]),
    ("app", "App push", False, [("device_name", "Device name", "text", False, False, [], None),
                                ("device_token", "Device token", "password", True, True, [], None)]),
    ("webhook", "Webhook", False, [("url", "URL", "url", True, False, [], None),
                                   ("method", "Method", "select", False, False, ["POST", "GET"], "POST"),
                                   ("secret", "Secret", "password", False, True, [], None),
                                   ("verify_tls", "Verify TLS", "bool", False, False, [], "true")]),
    ("telegram", "Telegram", False, [("bot_token", "Bot token", "password", True, True, [], None),
                                     ("chat_id", "Chat ID", "text", True, False, [], None)]),
    ("ntfy", "ntfy", False, [("server", "Server", "url", False, False, [], "https://ntfy.sh"),
                              ("topic", "Topic", "text", True, False, [], None)]),
]

CHANNELS = [
    {"id": 1, "type": "ipv64_mail", "type_label": "E-Mail", "name": "Mail demo@example.com",
     "config": {"address": "demo@example.com"}, "secrets_set": [], "enabled": True, "default": True,
     "verified": True, "lang": "en", "notify_down": True, "notify_up": True, "execution": "online",
     "node_id": None, "last_used": lambda: iso(minutes_ago=47), "last_error": None, "revision": 2,
     "created_at": lambda: iso(days_ago=88), "sendable_via_api": True},
    {"id": 2, "type": "app", "type_label": "App push", "name": "Pixel Demo Phone",
     "config": {"device_name": "Pixel Demo Phone"}, "secrets_set": ["device_token"], "enabled": True,
     "default": False, "verified": True, "lang": "en", "notify_down": True, "notify_up": True,
     "execution": "online", "node_id": None, "last_used": lambda: iso(minutes_ago=22), "last_error": None,
     "revision": 1, "created_at": lambda: iso(days_ago=30), "sendable_via_api": True},
    {"id": 3, "type": "ipv64_sms", "type_label": "SMS", "name": "SMS +49 170 0000000",
     "config": {"number": "+491700000000"}, "secrets_set": [], "enabled": True, "default": False,
     "verified": False, "lang": "en", "notify_down": True, "notify_up": False, "execution": "online",
     "node_id": None, "last_used": None, "last_error": None, "revision": 1,
     "created_at": lambda: iso(days_ago=2), "sendable_via_api": False},
]


def channel(i=0):
    return {k: (v() if callable(v) else v) for k, v in CHANNELS[i].items()}


def chan_for(ctx):
    cid = ctx.params["id"]
    c = next((channel(i) for i, x in enumerate(CHANNELS) if str(x["id"]) == cid), channel(0))
    if cid.isdigit():
        c["id"] = int(cid)
    return c


def maintenance(i=0, **over):
    m = {"id": 1, "name": "Weekly NAS maintenance", "start_at": iso(minutes_ago=15),
         "end_at": iso(minutes_ago=-45), "repeat": "weekly", "repeat_until": None,
         "tokens": [tok("nas"), tok("vpn")], "active": True, "node_id": None, "revision": 1}
    return {**m, **over}


def mutate_check(ctx, **extra):
    base = check(find_def(ctx.params.get("token", "")), detail=True)
    body = {k: v for k, v in ctx.body.items() if k != "force_move"}
    return {**base, **body, **extra}


def testrun(ctx):
    return data({"id": int(ctx.params["id"]) if ctx.params["id"].isdigit() else 7001, "status": "ok",
                 "target": "https://demo.home64.de", "applied": True,
                 "results": [{"source": "loc:nbg", "ok": True, "message": "HTTP 200 in 84 ms"},
                             {"source": "loc:fsn", "ok": True, "message": "HTTP 200 in 91 ms"}]})


def register():
    route("GET", H + "/account", lambda c: data({
        "plan": "Pro", "online_measuring_points": {"used": 14, "limit": 50},
        "checks_per_day": {"used": 18340, "limit": 100000}, "locations_per_check": 3, "sms_per_day": 10,
        "locations": [{"code": "nbg", "name": "Nuremberg"}, {"code": "fsn", "name": "Falkenstein"},
                      {"code": "hel", "name": "Helsinki"}],
        "nodes": {"total": 2, "online": 2}, "checks": len(CHECK_DEFS)}))
    settings = {"notify.quiet_enabled": True, "notify.quiet_from": "22:00", "notify.quiet_to": "07:00",
                "notify.quiet_days": "1,2,3,4,5,6,7", "notify.rate_per_hour": 30, "notify.batch_threshold": 5,
                "notify.batch_window_seconds": 60}
    route("GET", H + "/settings", data(settings))
    route("PATCH", H + "/settings", lambda c: data({**settings, **c.body}))

    def events(c):
        items = [event(e) for e in EVENT_DEFS]
        if c.query.get("before"):
            items = [e for e in items if e["id"] < int(c.query["before"])]
        if c.query.get("after"):
            items = sorted((e for e in items if e["id"] > int(c.query["after"])), key=lambda e: e["id"])
        return page(items[: int(c.query.get("limit") or 100)], has_more=False)
    route("GET", H + "/events", events)

    def incidents(c):
        items = [incident(i) for i in INCIDENT_DEFS]
        if c.query.get("open") == "true":
            items = [i for i in items if i["status"] == "open"]
        return data(items)
    route("GET", H + "/incidents", incidents)
    route("GET", H + "/testruns/{id}", testrun)
    route("GET", H + "/channel-types", lambda c: data([
        {"type": t, "label": lb, "needs_verification": nv,
         "fields": [{"key": k, "label": l, "kind": kd, "required": r, "secret": s, "options": o, "default": df}
                    for k, l, kd, r, s, o, df in f]} for t, lb, nv, f in CHANNEL_TYPES]))

    # checks
    def clist(c):
        items = [check(d) for d in CHECK_DEFS]
        q = c.query
        if q.get("type"):
            items = [x for x in items if x["type"] == q["type"]]
        if q.get("state"):
            items = [x for x in items if x["state"]["state"] == q["state"] or (q["state"] == "paused" and x["paused"])]
        if q.get("execution"):
            items = [x for x in items if x["execution"] == q["execution"]]
        if q.get("tag"):
            items = [x for x in items if q["tag"] in x["tags"]]
        if q.get("group"):
            items = [x for x in items if x["group"] == q["group"]]
        if q.get("q"):
            items = [x for x in items if q["q"].lower() in (x["name"] + (x["target"] or "")).lower()]
        return data(items)
    route("GET", H + "/checks", clist)
    route("POST", H + "/checks", lambda c: (201, data({**check(CHECK_DEFS[0], True), "token": tok("new-" + str(c.body.get("name"))),
                                                       "paused": False, **{k: v for k, v in c.body.items()}})))
    T = H + "/checks/{token}"
    route("GET", T, lambda c: data(check(find_def(c.params["token"]), True)))
    route("PATCH", T, lambda c: data(mutate_check(c)))
    route("DELETE", T, lambda c: (204, None))
    route("POST", T + "/pause", lambda c: data({**check(find_def(c.params["token"])), "paused": True}))
    route("POST", T + "/resume", lambda c: data({**check(find_def(c.params["token"])), "paused": False}))
    route("POST", T + "/run", lambda c: data({"queued": True, "command_id": 4711, "node_id": NODE1_ID,
                                              "detail": "Command queued at node"}))
    route("POST", T + "/move", lambda c: data({"id": 7001, "status": "running", "target": find_def(c.params["token"])[3],
                                               "applied": False, "results": []}))
    route("GET", T + "/measurements", measurements)
    route("GET", T + "/events", lambda c: page([event(e) for e in EVENT_DEFS if tok(e[1]) == c.params["token"]]
                                               or [event(EVENT_DEFS[0])], has_more=False))
    route("GET", T + "/incidents", lambda c: data([incident(i) for i in INCIDENT_DEFS if tok(i[1]) == c.params["token"]]
                                                  or [incident(INCIDENT_DEFS[2])]))
    route("GET", T + "/stats", stats)

    # channels
    route("GET", H + "/channels", lambda c: data([channel(i) for i in range(len(CHANNELS))]))
    route("POST", H + "/channels", lambda c: (201, data({**channel(0), "id": 4, "verified": c.body.get("type") not in ("ipv64_mail", "ipv64_sms"),
                                                         "secrets_set": [], **c.body})))
    C = H + "/channels/{id}"
    route("GET", C, lambda c: data(chan_for(c)))
    route("PATCH", C, lambda c: data({**chan_for(c), **c.body}))
    route("DELETE", C, lambda c: (204, None))
    route("POST", C + "/test", lambda c: data({"sent": True, "queued_at_node": False}))
    route("POST", C + "/send", lambda c: data({"sent": True, "duplicate": False, "sms_left": 9}))
    route("POST", C + "/verify/send", lambda c: (204, None))
    route("POST", C + "/verify", lambda c: (204, None))

    # maintenance
    route("GET", H + "/maintenance", lambda c: data([maintenance()]))
    route("POST", H + "/maintenance", lambda c: (201, data(maintenance(id=2, **c.body))))
    route("GET", H + "/maintenance/{id}", lambda c: data(maintenance(id=int(c.params["id"]) if c.params["id"].isdigit() else 1)))
    route("PATCH", H + "/maintenance/{id}", lambda c: data(maintenance(id=int(c.params["id"]) if c.params["id"].isdigit() else 1, **c.body)))
    route("DELETE", H + "/maintenance/{id}", lambda c: (204, None))
