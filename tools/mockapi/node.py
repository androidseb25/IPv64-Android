"""Node64 local API mock (no {"data":...} envelope: the node client decodes bodies with transport.json).
Two nodes: /node/api (Homelab, cloud-linked) and /node2/api (Cluster-Node-2, direct only)."""
from core import route, iso, epoch, now, NODE1_ID, NODE2_ID

# Documentation ranges only for attacker / public IPs.
ATTACKERS = [("198.51.100.23", "CN"), ("203.0.113.77", "RU"), ("192.0.2.145", "US"), ("198.51.100.201", "BR"),
             ("203.0.113.9", "IN"), ("192.0.2.58", "DE"), ("2001:db8::bad:1", "VN"), ("198.51.100.77", "NL")]
PORTS = [("22", 41), ("23", 27), ("80", 19), ("443", 14), ("3389", 11), ("445", 8), ("3306", 6), ("5900", 4)]
SERVICES = [("ssh", 48), ("telnet", 27), ("http", 24), ("rdp", 11), ("mysql", 6), ("smb", 8)]
KINDS = [("login", 52), ("scan", 33), ("exploit", 9), ("probe", 14)]
USERS = [("root", 38), ("admin", 21), ("ubuntu", 12), ("pi", 9), ("test", 7), ("postgres", 5)]
PASSWORDS = [("123456", 22), ("admin", 17), ("password", 13), ("root", 9), ("qwerty", 6)]
PATHS = [("/wp-login.php", 14), ("/.env", 9), ("/phpmyadmin/", 7), ("/admin", 5)]
AGENTS = [("Mozilla/5.0 zgrab/0.x", 11), ("curl/8.5.0", 8), ("Go-http-client/1.1", 6)]
COUNTRIES = [("CN", 34, 12), ("RU", 22, 8), ("US", 17, 9), ("BR", 11, 5), ("IN", 9, 4), ("DE", 5, 3)]

NODES = {
    "/node": dict(
        id=NODE1_ID, name="Homelab", location="Home Office", version="1.14.2", hostname="homelab.home64.de",
        uptime=14 * 86400 + 5 * 3600, db=18_874_368, scale=1.0, linked=True, plan="Pro",
        modules=[("dns64", "2.3.1", True), ("honeypot", "1.6.0", True), ("cdn64", "1.9.4", True),
                 ("dyndns", "1.4.2", True), ("checks", "3.2.0", True), ("docker", "1.2.0", False)],
        boards=[("checks", 5, 1, 1, 1), ("dns64", 1, 0, 0, 0), ("honeypot", 1, 0, 0, 0), ("cdn64", 4, 0, 1, 0)],
        queries=48_213, blocked=9_876, clients=14,
        checks=[("nas.home64.de HTTPS", "http", "https://nas.home64.de", "up", 42.0),
                ("vpn.home64.de Port 51820", "port", "vpn.home64.de:51820", "up", 18.5),
                ("Router", "icmp", "192.168.1.1", "up", 1.2),
                ("cam.home64.de", "http", "https://cam.home64.de", "down", None),
                ("Backup Job (push)", "push", "", "degraded", None),
                ("Certificate demo.home64.de", "cert", "demo.home64.de", "up", 120.0),
                ("Pi-hole DNS", "dns", "192.168.1.2", "paused", None)],
        hosts=[(1, ["nas.home64.de"], "proxy", "192.168.1.20", 5001, "https", "up", 24, 18234),
               (2, ["cam.home64.de"], "proxy", "192.168.1.31", 8080, "http", "down", None, 412),
               (3, ["demo.home64.de", "www.demo.home64.de"], "proxy", "192.168.1.10", 3000, "http", "up", 9, 9120),
               (4, ["shop.example.com"], "proxy", "192.168.1.40", 8443, "https", "slow", 380, 2210),
               (5, ["old.home64.de"], "redirect", None, None, "http", "unknown", None, 31)],
        targets=[(1, "demo.home64.de", "@", "ok", "203.0.113.10", "2001:db8:10::1"),
                 (2, "home64.de", "nas", "ok", "203.0.113.10", "2001:db8:10::20"),
                 (3, "home64.de", "vpn", "ok", "203.0.113.10", None),
                 (4, "home64.de", "cam", "error", "203.0.113.5", None),
                 (5, "home64.de", "new", "new", None, None)],
        devices=[("192.168.1.23", "Laptop", "aa:bb:cc:00:11:23", 12_040), ("192.168.1.31", "Camera", "aa:bb:cc:00:11:31", 6_320),
                 ("192.168.1.40", "Smart TV", "aa:bb:cc:00:11:40", 9_870), ("192.168.1.52", "Phone", "aa:bb:cc:00:11:52", 8_410),
                 ("192.168.1.60", "Printer", "aa:bb:cc:00:11:60", 540)],
    ),
    "/node2": dict(
        id=NODE2_ID, name="Cluster-Node-2", location="Rack B", version="1.13.5", hostname="cluster2.home64.de",
        uptime=3 * 86400 + 11 * 3600, db=7_340_032, scale=0.4, linked=False, plan=None,
        modules=[("checks", "3.1.2", True), ("dns64", "2.2.0", True), ("dyndns", "1.4.0", False), ("honeypot", "1.5.3", True)],
        boards=[("checks", 3, 0, 1, 0), ("dns64", 1, 0, 0, 0), ("honeypot", 1, 0, 0, 0)],
        queries=12_480, blocked=2_140, clients=6,
        checks=[("Gateway", "icmp", "192.168.2.1", "up", 0.9),
                ("Storage HTTPS", "http", "https://storage.home64.de", "up", 55.0),
                ("Mail Port 587", "tcp", "mail.home64.de:587", "up", 31.0),
                ("Printer", "icmp", "192.168.2.50", "paused", None)],
        hosts=[(1, ["storage.home64.de"], "proxy", "192.168.2.20", 9000, "http", "up", 14, 4120)],
        targets=[(1, "home64.de", "storage", "ok", "203.0.113.10", None)],
        devices=[("192.168.2.11", "Server", "aa:bb:cc:00:22:11", 4_100), ("192.168.2.12", "Workstation", "aa:bb:cc:00:22:12", 3_020)],
    ),
}


def _n(spec, v):
    return max(1, int(v * spec["scale"]))


def _counts(spec, items):
    return [{"key": k, "count": _n(spec, c), "last": iso(minutes_ago=3 + i * 7)} for i, (k, c) in enumerate(items)]


def _lines(spec):
    msgs = [("INFO", "core", "node started"), ("INFO", "checks", "scheduler running, %d checks" % len(spec["checks"])),
            ("INFO", "dns64", "blocklists loaded"), ("WARN", "checks", "check cam.home64.de is down: connection refused"),
            ("INFO", "dyndns", "update ok for nas.home64.de"), ("INFO", "link", "sync ok"),
            ("WARN", "dyndns", "update failed for cam.home64.de: token rejected"), ("INFO", "dns64", "cache flushed"),
            ("INFO", "honeypot", "new source 198.51.100.23 (ssh)"), ("ERROR", "cdn64", "upstream 192.168.1.31:8080 unreachable")]
    out = []
    for i, (lvl, mod, msg) in enumerate(msgs * 3):
        t = iso(minutes_ago=(30 - i) * 2)
        out.append('time="%s" level=%s module=%s msg="%s"' % (t, lvl, mod, msg))
    return out


def _check(spec, i, c):
    name, typ, target, st, ms = c
    down = st == "down"
    return {
        "token": "chk_%s_%02d" % (spec["id"][-4:], i + 1), "name": name, "type": typ, "target": target,
        "interval_seconds": 60 if typ != "cert" else 3600, "retries": 2, "timeout_seconds": 10.0, "invert": False,
        "ip_mode": "auto", "tags": ["home"] if i % 2 == 0 else ["infra"], "description": "", "paused": st == "paused",
        "critical": i == 0, "execution": "local", "policy": "all", "locations": [], "notify_down": True, "notify_up": True,
        "notify_degraded": True, "channels": [1], "repeat_minutes": 0, "revision": 3 + i,
        "created_at": iso(days_ago=40), "updated_at": iso(days_ago=2),
        "push_url": ("http://192.168.1.10:8080/api/push/chk_%02d" % (i + 1)) if typ == "push" else None,
        "state": {"state": st, "since": iso(minutes_ago=90 if down else 5000), "last_check_at": iso(seconds_ago=40),
                  "next_check_at": iso(seconds_ago=-20), "last_status": "down" if down else "up", "last_latency_ms": ms,
                  "last_reason": "connection_refused" if down else "", "last_message": "Connection refused" if down else "OK",
                  "consecutive_fail": 3 if down else 0, "consecutive_ok": 0 if down else 120,
                  "down_since": iso(minutes_ago=90) if down else None, "maintenance": False},
        "nodes": [spec["id"]], "owner": "self",
    }


def _host(spec, h):
    hid, names, kind, th, tp, scheme, reach, ms, reqs = h
    return {
        "id": hid, "kind": kind, "names": names, "enabled": True, "scheme": scheme, "target_host": th, "target_port": tp,
        "websockets": True, "block_exploits": True, "cache_assets": False, "cert_id": 1, "force_ssl": True, "http2": True,
        "http3": False, "hsts": True, "hsts_subdomains": False, "min_tls": "1.2", "verify_target": False,
        "created_at": iso(days_ago=60), "updated_at": iso(days_ago=3),
        "reach": {"state": reach, "ms": ms, "error": "connection refused" if reach == "down" else "",
                  "checked_at": iso(seconds_ago=30), "since": iso(hours_ago=6)},
        "stats": {"requests": reqs, "errors": reqs // 40, "blocked": reqs // 100},
        "certificate": {"state": "ok", "name": names[0], "days_left": 61, "missing": ""},
        "route_error": "", "certificate_error": "",
        "redirect_target": "https://demo.home64.de" if kind == "redirect" else "", "redirect_scheme": "https",
        "redirect_code": 301, "preserve_path": True, "access_list_id": 0,
    }


def _target(spec, t):
    tid, dom, host, st, v4, v6 = t
    return {
        "id": tid, "domain": dom, "host": host, "fqdn": dom if host == "@" else "%s.%s" % (host, dom), "record_id": 1000 + tid,
        "token_kind": "record", "token_hint": "demo_xxxx", "mode4": "auto", "fixed4": "", "mode6": "host" if v6 else "off",
        "fixed6": "", "suffix6": "", "iface": "", "enabled": True, "paused": None, "status": st,
        "last_ipv4": v4 or "", "last_ipv6": v6 or "", "last_prefix": "2001:db8:10::/64" if v6 else "",
        "last_ok_at": iso(minutes_ago=4 + tid) if st == "ok" else None, "last_error": "token rejected" if st == "error" else "",
        "last_error_at": iso(minutes_ago=12) if st == "error" else None, "dns_state": "ok" if st == "ok" else "unknown",
    }


def _event(spec, i):
    ip, cc = ATTACKERS[i % len(ATTACKERS)]
    svc, port = [("ssh", 22), ("telnet", 23), ("http", 80), ("rdp", 3389), ("mysql", 3306)][i % 5]
    login = svc in ("ssh", "telnet", "rdp", "mysql")
    return {"time": iso(minutes_ago=2 + i * 9), "service": svc, "port": port, "kind": "login" if login else "scan",
            "src": ip, "src_port": 40000 + i * 137, "country": cc,
            "username": USERS[i % len(USERS)][0] if login else "", "password": PASSWORDS[i % len(PASSWORDS)][0] if login else "",
            "method": "" if login else "GET", "path": "" if login else PATHS[i % len(PATHS)][0], "host": "",
            "user_agent": "" if login else AGENTS[i % len(AGENTS)][0], "client": "SSH-2.0-libssh_0.9" if svc == "ssh" else "",
            "page": "", "detail": "", "source": "honeypot", "rule": "", "fingerprint": "", "sni": ""}


def _stats(spec):
    tl = [{"time": iso(hours_ago=23 - h), "events": _n(spec, 4 + (h * 7) % 13 + (8 if 2 <= h % 24 <= 5 else 0))} for h in range(24)]
    return {
        "since": iso(hours_ago=24), "events": _n(spec, 342), "sources": _n(spec, 57), "logins": _n(spec, 189),
        "by_service": _counts(spec, SERVICES), "by_port": _counts(spec, PORTS), "by_kind": _counts(spec, KINDS),
        "top_sources": [{**c, "country": ATTACKERS[i][1]} for i, c in enumerate(_counts(spec, [(a[0], 60 - i * 6) for i, a in enumerate(ATTACKERS)]))],
        "top_users": _counts(spec, USERS), "top_passwords": _counts(spec, PASSWORDS), "top_credentials": _counts(spec, [("root:123456", 18), ("admin:admin", 14)]),
        "top_paths": _counts(spec, PATHS), "top_user_agents": _counts(spec, AGENTS), "top_fingerprints": [],
        "timeline": tl, "by_source": [], "by_country": [{"key": k, "count": _n(spec, c), "sources": max(1, _n(spec, s)), "last": iso(minutes_ago=6)} for k, c, s in COUNTRIES],
        "country_unknown": _n(spec, 3), "by_decoy": [],
    }


def _honeypot_status(spec):
    return {
        "version": [m for m in spec["modules"] if m[0] == "honeypot"][0][1], "running": True, "started_at": iso(days_ago=9),
        "profile": "standard", "scan": True,
        "services": [{"service": s, "port": p, "listen": True, "error": "", "conns": _n(spec, c)}
                     for s, p, c in [("ssh", 22, 410), ("telnet", 23, 230), ("http", 80, 190), ("rdp", 3389, 95), ("mysql", 3306, 52)]],
        "warnings": [], "counters": {"connections": _n(spec, 12480), "logins": _n(spec, 5210), "events": _n(spec, 9830), "dropped": 0},
        "buffered": 0, "dropped": 0,
        "report": {"enabled": True, "key_set": True, "active": True, "sensor_id": "sens_demo01", "last_success": iso(minutes_ago=14),
                   "last_end": iso(minutes_ago=14), "last_error": "", "last_error_at": None, "next_attempt": iso(minutes_ago=-46),
                   "errors": 0, "sources_today": _n(spec, 57), "sources_total": _n(spec, 1320), "last_rejected": 0, "last_rejected_reasons": {}},
        "logs": {"files": [], "syslog": None, "lines": 0, "hits": 0, "events": 0},
    }


def _hp_config(spec):
    return {"profile": "standard", "services": [{"service": "ssh", "port": 22}, {"service": "telnet", "port": 23},
                                                {"service": "http", "port": 80, "page": "generic"}],
            "scan": True, "max_conns_per_ip": 20, "max_conns_total": 500, "retention_days": 30, "max_events": 50000,
            "notify_new": True, "report": True, "no_local_block": False, "country": "DE",
            "log": {"files": [], "syslog_port": 0, "syslog_allow": []}, "cert_id": 0, "cert_ports": [],
            "login": {"off": False, "threshold": 5, "window_min": 10, "no_notify": False}}


def _link(spec):
    if not spec["linked"]:
        return {"linked": False, "connected": False, "requesting": False, "pending_code": "", "local_notifications": False}
    return {"linked": True, "connected": True, "account_id": "acc_demo01", "plan": spec["plan"], "node_name": spec["name"],
            "location": spec["location"], "remote_mgmt": "full", "hub": "hub1.ipv64.net", "last_sync_at": iso(minutes_ago=1),
            "connected_at": iso(hours_ago=5), "linked_at": iso(days_ago=30), "outbox": 0, "conflicts": 0, "last_error": "",
            "pending_code": "", "requesting": False, "online_limit": 25, "checks_today": 112, "checks_limit": 500,
            "locations_per_check": 3, "local_notifications": True}


def _status(spec):
    mods = [_module(spec, m) for m in spec["modules"]]
    return {
        "node": {"id": spec["id"], "name": spec["name"], "location": spec["location"], "timezone": "Europe/Berlin",
                 "version": spec["version"], "started_at": iso(seconds_ago=spec["uptime"]), "uptime_seconds": spec["uptime"],
                 "database_bytes": spec["db"], "log_level": "info"},
        "link": _link(spec), "modules": mods,
        "boards": [{"module": m, "ok": o, "down": d, "paused": p, "other": x} for m, o, d, p, x in spec["boards"]],
    }


def _module(spec, m):
    name, ver, on = m
    return {"name": name, "version": ver, "changed": "2026-10-%02d" % (10 + len(name)), "available": True, "enabled": on,
            "running": on, "path": "/" + name, "last_error": ""}


def _register_node(prefix, spec):
    b = prefix + "/api"
    r = lambda method, path, val: route(method, b + path, val)
    mod_state = {m[0]: m[2] for m in spec["modules"]}  # enable/disable is remembered per node
    paused = {"until": None}

    def status(ctx):
        s = _status(spec)
        for m in s["modules"]:
            m["enabled"] = m["running"] = mod_state.get(m["name"], m["enabled"])
        return s

    def set_mod(on):
        def h(ctx):
            mod_state[ctx.params["name"]] = on
            return {"name": ctx.params["name"], "enabled": on, "running": on}
        return h

    r("GET", "/health", lambda c: {"status": "ok", "version": spec["version"], "time": iso()})
    r("GET", "/version", lambda c: {"version": spec["version"], "started_at": iso(seconds_ago=spec["uptime"]),
                                    "hostname": spec["hostname"], "time": iso()})
    r("GET", "/status", status)
    r("GET", "/modules", lambda c: [_module(spec, (n, v, mod_state.get(n, o))) for n, v, o in spec["modules"]])
    r("POST", "/modules/{name}/enable", set_mod(True))
    r("POST", "/modules/{name}/disable", set_mod(False))
    r("GET", "/logs", lambda c: {"level": "info", "lines": _lines(spec)[-int(c.query.get("lines", 100)):]})
    r("GET", "/settings", {"node.name": spec["name"], "node.location": spec["location"], "node.timezone": "Europe/Berlin",
                           "node.base_url": "http://192.168.1.10:8080", "notify.quiet_enabled": "0", "notify.rate_per_hour": "30",
                           "updates.check": "1"})
    r("PUT", "/settings", lambda c: {"node.name": spec["name"], **{k: str(v) for k, v in c.body.items()}})
    r("GET", "/audit", lambda c: [{"id": 50 - i, "time": iso(minutes_ago=15 + i * 40), "actor": "Demo User", "action": a, "target": t,
                                    "details": "", "ip": "192.168.1.23", "origin": "web"}
                                   for i, (a, t) in enumerate([("login", "user"), ("check.pause", "Pi-hole DNS"), ("module.enable", "honeypot"),
                                                               ("settings.update", "node.location"), ("key.create", "Prometheus")])])
    r("GET", "/logging", {"level": "info", "modules": {}, "known_modules": [m[0] for m in spec["modules"]] + ["core", "link"]})
    r("PUT", "/logging", lambda c: {"level": c.body.get("level", "info"), "modules": c.body.get("modules") or {}, "known_modules": []})
    r("GET", "/retention", {"raw_days": 14, "hourly_days": 365, "chart_detail": "normal", "audit_days": 90, "options": {}})
    r("PUT", "/retention", lambda c: {"raw_days": 14, "hourly_days": 365, "chart_detail": "normal", "audit_days": 90, **c.body})
    r("GET", "/keys", lambda c: [{"id": 1, "name": "Mobile app", "hint": "node64_demo", "created_at": iso(days_ago=20), "last_used_at": iso(minutes_ago=1)},
                                 {"id": 2, "name": "Prometheus", "hint": "node64_prom", "created_at": iso(days_ago=60), "last_used_at": iso(minutes_ago=2)}])
    r("POST", "/keys", lambda c: (201, {"id": 3, "name": c.body.get("name", "New key"), "hint": "node64_new0", "created_at": iso(),
                                       "key": "node64_demo_xxxxxxxxxxxxxxxxxxxxxxxx"}))
    r("DELETE", "/keys/{id}", lambda c: (204, None))
    r("GET", "/overview", lambda c: {
        "node": {"id": spec["id"], "name": spec["name"], "location": spec["location"], "version": spec["version"],
                 "started_at": iso(seconds_ago=spec["uptime"]), "db_bytes": spec["db"]},
        "modules": [{"name": n, "version": v, "enabled": mod_state.get(n, o), "running": mod_state.get(n, o), "last_error": ""} for n, v, o in spec["modules"]],
        "traffic": {"available": True, "rx_since_start": 52_428_800_000, "tx_since_start": 18_350_080_000,
                    "rx_total": 210_000_000_000, "tx_total": 96_000_000_000, "since": iso(days_ago=120)},
        "healthchecks": {"checks_by_location": {"local": len(spec["checks"])}, "nodes": 2, "nodes_online": 2},
        "account": {"account": "Demo User", "plan": spec["plan"] or "Free", "plan_until": iso(days_ago=-200), "checked_at": iso(minutes_ago=5),
                    "error": "", "limits": [{"key": "checks", "used": 112.0, "max": 500.0, "unlimited": False, "pct": 22}]}})
    r("GET", "/dashboard/layout", {"items": [{"id": "status", "size": "l", "hidden": False}, {"id": "checks", "size": "m", "hidden": False}]})
    r("PUT", "/dashboard/layout", lambda c: c.body or {"items": []})
    r("DELETE", "/dashboard/layout", lambda c: (204, None))

    # Link / pairing (node side)
    r("GET", "/link", lambda c: _link(spec))
    r("POST", "/link/apikey", lambda c: {**_link({**spec, "linked": True, "plan": spec["plan"] or "Pro"}),
                                         **({"location": c.body["location"]} if c.body.get("location") else {}),
                                         **({"remote_mgmt": c.body["remote_mgmt"]} if c.body.get("remote_mgmt") else {})})
    r("POST", "/link/unlink", lambda c: (204, None))
    r("GET", "/link/conflicts", [])
    r("POST", "/link/conflicts/{id}", lambda c: (204, None))
    r("PUT", "/link/settings", lambda c: {"local_notifications": c.body.get("local_notifications", True)})
    r("GET", "/ipv64", {"key_stored": True, "account": "Demo User", "account_id": 4711, "plan": "Pro", "key_name": "Node64 Homelab",
                        "checked_at": iso(minutes_ago=5), "expires_at": iso(days_ago=-300), "days_left": 300})
    r("PUT", "/ipv64/key", lambda c: {"key_stored": True, "account": "Demo User", "account_id": 4711, "plan": "Pro",
                                      "key_name": "Node64 Homelab", "checked_at": iso(), "days_left": 300})
    r("DELETE", "/ipv64/key", lambda c: (204, None))

    # DNS64
    def blocking():
        u = paused["until"]
        if u and u > now().timestamp():
            return {"paused": True, "until_resumed": False, "paused_until": iso(minutes_ago=-int((u - now().timestamp()) / 60))}
        return {"paused": False, "until_resumed": False, "paused_until": None}

    def pause(ctx):
        paused["until"] = now().timestamp() + 60 * int(ctx.body.get("minutes", 5))
        return blocking()

    def resume(ctx):
        paused["until"] = None
        return blocking()

    d_ver = [m for m in spec["modules"] if m[0] == "dns64"][0][1]
    r("GET", "/dns64/status", lambda c: {"version": d_ver, "listen": "192.168.1.10:53", "startedAt": iso(seconds_ago=spec["uptime"]),
                                         "router": "192.168.1.1", "routerSource": "auto", "retentionDays": 30, "blocking": blocking()})
    r("GET", "/dns64/summary", lambda c: {
        "summary": {"total": spec["queries"], "blocked": spec["blocked"], "cached": spec["queries"] // 3, "clients": spec["clients"],
                    "avgUpstreamMs": 14.7, "droppedLogEntries": 0},
        "blocklistSize": 412_380, "listsLoadedAt": iso(hours_ago=3), "cache": _cache(spec), "uptimeSeconds": spec["uptime"], "version": d_ver})
    r("GET", "/dns64/timeline", lambda c: {"stepMs": 3_600_000, "buckets": [
        {"t": (epoch(hours_ago=23 - h)) * 1000, "total": _n(spec, 1200 + (h * 173) % 900), "blocked": _n(spec, 200 + (h * 61) % 300)} for h in range(24)]})

    tops = {"blocked": [("ads.tracker.example", None, 2140), ("telemetry.example.org", None, 1388), ("metrics.adnet.example", None, 954),
                        ("pixel.example.com", None, 702), ("cdn.badads.example", None, 431)],
            "allowed": [("nas.home64.de", None, 5210), ("api.example.com", None, 3902), ("time.example.org", None, 2780),
                        ("update.example.net", None, 1860), ("shop.example.com", None, 1245)],
            "clients": [("Laptop", "192.168.1.23", 12040), ("Smart TV", "192.168.1.40", 9870), ("Phone", "192.168.1.52", 8410),
                        ("Camera", "192.168.1.31", 6320), ("Printer", "192.168.1.60", 540)]}

    def top(ctx):
        lim = int(ctx.query.get("limit", 10))
        return [{"name": n, "hostname": h, "count": _n(spec, c), "blocked": _n(spec, c) // 5} for n, h, c in tops.get(ctx.query.get("kind", "blocked"), tops["blocked"])][:lim]

    r("GET", "/dns64/top", top)
    r("GET", "/dns64/log", lambda c: {"total": 5000, "rows": [
        {"t": epoch(seconds_ago=20 + i * 17) * 1000, "client": "192.168.1.%d" % (23 + i % 3), "clientName": ["Laptop", "Phone", "Smart TV"][i % 3],
         "domain": [x[0] for x in tops["allowed"] + tops["blocked"]][i % 10], "qtype": "A", "status": ["allowed", "blocked", "cached"][i % 3],
         "reason": "", "rcode": "NOERROR", "upstream": "9.9.9.9", "ms": 11.5 + i, "proto": "udp", "group": ""} for i in range(int(c.query.get("limit", 25)) if False else 25)]})
    r("GET", "/dns64/lists", [{"name": "StevenBlack", "url": "https://lists.example.org/hosts", "rules": 168_220, "updatedAt": iso(hours_ago=3), "error": "", "failures": 0},
                              {"name": "OISD small", "url": "https://lists.example.net/oisd-small.txt", "rules": 52_340, "updatedAt": iso(hours_ago=3), "error": "", "failures": 0}])
    r("POST", "/dns64/lists", lambda c: _dns_settings())
    r("DELETE", "/dns64/lists", lambda c: _dns_settings())
    r("GET", "/dns64/upstreams", {"mode": "fastest", "servers": [
        {"name": "Quad9", "url": "tls://dns.example.org", "proto": "dot", "ips": ["192.0.2.53"], "requests": 20410, "wins": 14022, "errors": 3,
         "avgMs": 12.4, "healthy": True, "lastError": "", "lastErrorAt": "", "active": True}], "localDNS": "192.168.1.1", "localSource": "router", "count": 1})
    r("POST", "/dns64/upstreams", lambda c: _dns_settings())
    r("DELETE", "/dns64/upstreams", lambda c: _dns_settings())
    r("GET", "/dns64/cache", lambda c: _cache(spec))
    r("POST", "/dns64/cache/flush", {"flushed": 4210})
    r("GET", "/dns64/allowlist", [{"domain": "shop.example.com", "addedAt": iso(days_ago=12)}, {"domain": "update.example.net", "addedAt": iso(days_ago=30)}])
    r("POST", "/dns64/allowlist", lambda c: {"domain": c.body.get("domain", "example.com"), "addedAt": iso()})
    r("DELETE", "/dns64/allowlist", lambda c: (204, None))
    r("POST", "/dns64/allowlist/import", lambda c: {"imported": len(c.body.get("domains") or []), "invalid": 0})
    r("GET", "/dns64/settings", lambda c: _dns_settings())
    r("PUT", "/dns64/settings", lambda c: {**_dns_settings(), **c.body})
    r("POST", "/dns64/pause", pause)
    r("POST", "/dns64/resume", resume)
    r("GET", "/dns64/devices", lambda c: [{"ip": ip, "name": n, "mac": mac, "group": "", "group_id": 0, "queries": q, "last_seen": iso(minutes_ago=1 + i * 4)}
                                         for i, (ip, n, mac, q) in enumerate(spec["devices"])])
    r("GET", "/dns64/device-names", [{"name": d[1], "ips": [d[0]]} for d in spec["devices"]])
    r("GET", "/dns64/records", [{"id": 1, "name": "nas.lan", "type": "A", "value": "192.168.1.20", "ttl": 300, "enabled": True,
                                 "created_at": iso(days_ago=50), "comment": "NAS", "no_ptr": False, "local_only": True, "dynamic": False},
                                {"id": 2, "name": "router.lan", "type": "A", "value": "192.168.1.1", "ttl": 300, "enabled": True,
                                 "created_at": iso(days_ago=50), "comment": "", "no_ptr": False, "local_only": True, "dynamic": False}])

    # Honeypot
    r("GET", "/honeypot/status", lambda c: _honeypot_status(spec))
    r("GET", "/honeypot/stats", lambda c: _stats(spec))
    r("GET", "/honeypot/events", lambda c: [_event(spec, i) for i in range(int(c.query.get("limit", 20)))][:25])
    r("DELETE", "/honeypot/events", {"deleted": 342})
    r("GET", "/honeypot/config", lambda c: _hp_config(spec))
    r("PUT", "/honeypot/config", lambda c: {**_hp_config(spec), **c.body})
    r("GET", "/honeypot/catalog", {"profile": "standard", "running": True, "extra": [], "services": ["ssh", "telnet", "http", "rdp", "mysql"],
                                   "pages": ["generic", "wp-login"], "groups": ["remote", "web", "db"], "presets": {},
                                   "entries": [{"service": "ssh", "port": 22, "default_port": 22, "proto": "tcp", "group": "remote", "description": "SSH login trap",
                                                "profiles": ["standard", "full"], "on": True, "state": "open", "owner": "", "purpose": "", "error": "", "conns": 410}]})
    r("GET", "/honeypot/live", lambda c: {"minutes": 60, "now": epoch(), "target": {"cc": "DE", "flag": "DE", "name": "Germany", "x": 0.53, "y": 0.28, "known": True, "manual": False},
                                          "events": [{"t": epoch(minutes_ago=i * 5), "cc": ATTACKERS[i % 8][1], "flag": ATTACKERS[i % 8][1], "name": ATTACKERS[i % 8][1],
                                                      "x": 0.2 + 0.08 * i, "y": 0.3 + 0.03 * i, "ip": ATTACKERS[i % 8][0], "kind": "login", "group": "login",
                                                      "service": "ssh", "port": 22, "source": "honeypot"} for i in range(8)]})

    # DynDNS
    r("GET", "/dyndns/targets", lambda c: [_target(spec, t) for t in spec["targets"]])
    r("GET", "/dyndns/targets/{id}", lambda c: _target(spec, next((t for t in spec["targets"] if str(t[0]) == c.params["id"]), spec["targets"][0])))
    r("POST", "/dyndns/targets", lambda c: (201, {**_target(spec, spec["targets"][0]), **{k: v for k, v in c.body.items() if k != "token"}}))
    r("PUT", "/dyndns/targets/{id}", lambda c: {**_target(spec, spec["targets"][0]), **{k: v for k, v in c.body.items() if k != "token"}})
    r("DELETE", "/dyndns/targets/{id}", lambda c: (204, None))
    r("POST", "/dyndns/targets/{id}/update", lambda c: _target(spec, spec["targets"][0]))
    r("GET", "/dyndns/settings", {"interval_minutes": 5, "source4": "auto", "source6": "auto", "keep_checks_days": 7, "keep_updates_days": 90,
                                  "notify_ip": True, "notify_fail": True, "notify_limit": True})
    r("PUT", "/dyndns/settings", lambda c: {"interval_minutes": 5, "source4": "auto", "source6": "auto", **c.body})
    r("GET", "/dyndns/status", {"running": True, "targets": len(spec["targets"])})
    r("POST", "/dyndns/check", {"started": True})

    # PROXY64 hosts
    r("GET", "/cdn64/hosts", lambda c: [_host(spec, h) for h in spec["hosts"] if not c.query.get("kind") or h[2] == c.query["kind"]])
    r("GET", "/cdn64/hosts/{id}", lambda c: _host(spec, next((h for h in spec["hosts"] if str(h[0]) == c.params["id"]), spec["hosts"][0])))

    # Healthchecks (list/get/events/incidents/pause/resume)
    cs = lambda: [_check(spec, i, c) for i, c in enumerate(spec["checks"])]
    r("GET", "/checks", lambda c: [x for x in cs() if (not c.query.get("type") or x["type"] == c.query["type"])
                                   and (not c.query.get("state") or x["state"]["state"] == c.query["state"])])
    r("GET", "/checks/{token}", lambda c: next((x for x in cs() if x["token"] == c.params["token"]), cs()[0]))
    r("POST", "/checks/{token}/pause", lambda c: {**cs()[0], "paused": True})
    r("POST", "/checks/{token}/resume", lambda c: {**cs()[0], "paused": False})
    r("POST", "/checks/{token}/run", lambda c: (204, None))
    r("GET", "/events", lambda c: _events(spec))
    r("GET", "/checks/{token}/events", lambda c: _events(spec))
    r("GET", "/incidents", lambda c: _incidents(spec))
    r("GET", "/checks/{token}/incidents", lambda c: _incidents(spec))

    # Channels (list only)
    r("GET", "/channels", [{"id": 1, "type": "email", "name": "Demo User", "enabled": True, "config": {"to": "demo@example.com"}},
                           {"id": 2, "type": "push", "name": "Demo phone", "enabled": True, "config": {}}])


def _cache(spec):
    return {"entries": _n(spec, 3120), "max": 10000, "staleEntries": 12, "hits": _n(spec, 21400), "misses": _n(spec, 6200),
            "staleServed": 40, "refreshed": 910, "refreshFailures": 1}


def _dns_settings():
    return {"port": 53, "bind": "0.0.0.0", "router": "192.168.1.1", "retention_days": 30, "upstream_mode": "fastest",
            "upstreams": [{"name": "Quad9", "url": "tls://dns.example.org", "ips": ["192.0.2.53"], "enabled": True, "custom": False}],
            "lists": [{"name": "StevenBlack", "url": "https://lists.example.org/hosts", "enabled": True, "custom": False}],
            "list_hours": 24, "block_mode": "zero", "cache_size": 10000, "anonymize": "off", "allow_nets": ["192.168.1.0/24"],
            "doh": False, "dot": False, "doq": False, "doh_port": 443, "dot_port": 853, "names": [], "cert_mode": "self",
            "notify_upstream": True, "notify_lists": True, "acme_domain": "", "acme_prefix": "", "acme_email": "", "acme_staging": False,
            "upstream_count": 1, "cert_id": 0, "firefox_canary": True}


def _events(spec):
    out = []
    for i, c in enumerate(spec["checks"][:5]):
        out.append({"id": 100 - i, "event_id": "ev_%02d" % i, "token": "chk_%s_%02d" % (spec["id"][-4:], i + 1), "kind": "state",
                    "time": iso(minutes_ago=20 + i * 90), "from": "up", "to": "down" if i % 2 == 0 else "up", "reason": "", "message": c[0],
                    "duration_seconds": 300, "flaps": 0, "maintenance": False, "silent": False, "incident_id": 10 - i})
    return out


def _incidents(spec):
    return [{"id": 10 - i, "token": "chk_%s_%02d" % (spec["id"][-4:], i + 1), "started_at": iso(hours_ago=2 + i * 20),
             "ended_at": None if i == 0 else iso(hours_ago=1 + i * 20), "open": i == 0, "reason": "connection_refused", "message": c[0]}
            for i, c in enumerate(spec["checks"][:3])]


def register():
    for prefix, spec in NODES.items():
        _register_node(prefix, spec)
