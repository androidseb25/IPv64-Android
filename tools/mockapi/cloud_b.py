"""Cloud mock routes: CDN, DNS64 clients, Honeypot64, Threat. Dummy data only (documentation IP ranges).
Not mocked on purpose: CloudRoutersApi, GatewaysApi, PeersApi (the app never calls them)."""
import hashlib

from core import API, data, page, route, iso, epoch, now, echo

# ---------------------------------------------------------------- CDN
CDN_SERVER = {"name": "cdn-fra-1", "hostname": "cdn-fra-1.ipv64.net", "ipv4": "203.0.113.50", "ipv6": "2001:db8:cd::50"}


def _cdn(token, domain, domain_id, proxy="on", pm="off", frontend="https_redirect", cert="letsencrypt",
         pending=(), acl=False, cache=True, mb=(1832.4, 421.7), days=40, mappings=()):
    return {
        "id": token, "domain": domain, "domain_id": domain_id, "active": True, "pending": list(pending),
        "dns_records_enabled": True, "server": CDN_SERVER,
        "proxy": {"state": proxy, "frontend": frontend,
                  "backend": {"scheme": "http", "port": 8080}, "websocket": True, "certificate": cert,
                  "caching": {"enabled": cache, "time_minutes": 60, "size_mb": 256},
                  "access_list": {"enabled": acl, "mode": "allowlist"}},
        "portmapper": {"state": pm}, "traffic_mb": {"in": mb[0], "out": mb[1]},
        "created_at": iso(days_ago=days), "mappings": list(mappings),
    }


def _mapping(mid, name, listen, target, tr, record, fqdn):
    return {"id": mid, "name": name, "listen_port": listen, "transport": tr, "target_port": target,
            "record": record, "fqdn": fqdn}


MAPPINGS = {
    "cdn_demo0001": [_mapping("map_ssh01", "SSH", 50022, 22, "tcp", "rec_demo_a", "demo.home64.de"),
                     _mapping("map_wg002", "WireGuard", 51820, 51820, "udp", "rec_demo_a", "demo.home64.de")],
    "cdn_nas00002": [_mapping("map_smb01", "Web UI", 50443, 5001, "tcp", "rec_nas_a", "nas.home64.de")],
}


def cdn_domains():
    return [
        _cdn("cdn_demo0001", "demo.home64.de", 1001, pm="on", acl=True, mappings=MAPPINGS["cdn_demo0001"]),
        _cdn("cdn_nas00002", "nas.home64.de", 1002, pm="on", frontend="http_and_https", mb=(9120.8, 2310.2),
             mappings=MAPPINGS["cdn_nas00002"]),
        _cdn("cdn_shop0003", "shop.example.com", 1004, cert="issuing", pending=["certificate"], mb=(240.1, 88.5), days=3),
        _cdn("cdn_cam00004", "cam.home64.de", 1005, proxy="off", cert="default", frontend="http", cache=False,
             mb=(0.0, 0.0), days=12),
    ]


def cdn_detail(ctx):
    items = cdn_domains()
    t = ctx.params.get("token")
    return next((d for d in items if d["id"] == t or d["domain"] == t), {**items[0], "id": t or items[0]["id"]})


def cdn_list_item(d):
    return {k: v for k, v in d.items() if k != "mappings"}


def _deep_merge(a, b):
    out = dict(a)
    for k, v in b.items():
        if v is None:
            continue
        out[k] = _deep_merge(out[k], v) if isinstance(v, dict) and isinstance(out.get(k), dict) else v
    return out


def cdn_update(ctx):
    d = cdn_detail(ctx)
    b = ctx.body
    patch = {"proxy": {}}
    if "frontend" in b: patch["proxy"]["frontend"] = b["frontend"]
    if "websocket" in b: patch["proxy"]["websocket"] = b["websocket"]
    if "backend_scheme" in b or "backend_port" in b:
        patch["proxy"]["backend"] = {k: b[s] for s, k in (("backend_scheme", "scheme"), ("backend_port", "port")) if s in b}
    for k in ("caching", "access_list"):
        if isinstance(b.get(k), dict): patch["proxy"][k] = b[k]
    return data(_deep_merge(d, patch))


def cdn_with(path_patch):
    return lambda ctx: data(_deep_merge(cdn_list_item(cdn_detail(ctx)), path_patch))


def cdn_records(ctx):
    d = cdn_detail(ctx)
    base, dom = d["domain"], d["domain"]
    mk = lambda i, typ, ip, st, pre="": {
        "id": 5000 + i, "domain_id": d["domain_id"], "domain": dom, "prefix": pre,
        "fqdn": dom if not pre else f"{pre}.{dom}", "type": typ, "content": ip, "ttl": 60, "state": st,
        "pending_dns": False, "token": f"rec_{base.split('.')[0]}_{i}", "updated_at": iso(minutes_ago=30 * i)}
    return page([mk(1, "A", "192.0.2.10", "cdn_proxied"), mk(2, "AAAA", "2001:db8::10", "cdn_proxied"),
                 mk(3, "A", "198.51.100.23", "active", "www"), mk(4, "AAAA", "2001:db8::23", "cdn_partial", "www")])


def cdn_record_toggle(state):
    def h(ctx):
        rec = ctx.params["record"]
        return data({"id": 5001, "domain": cdn_detail(ctx)["domain"], "prefix": "", "fqdn": cdn_detail(ctx)["domain"],
                     "type": "A", "content": "192.0.2.10", "ttl": 60, "state": state, "pending_dns": True,
                     "token": rec if not rec.isdigit() else "rec_demo_a", "updated_at": iso()})
    return h


def cdn_mappings(ctx):
    return data(cdn_detail(ctx)["mappings"] or MAPPINGS["cdn_demo0001"])


def cdn_mapping_create(ctx):
    b = ctx.body
    return 201, data(_mapping("map_new001", b.get("name") or "Custom", b.get("listen_port") or 50555,
                              b.get("target_port") or 8080, b.get("transport") or "tcp",
                              b.get("record") or "rec_demo_a", cdn_detail(ctx)["domain"]))


ACCESS = [(1, "198.51.100.0/24"), (2, "203.0.113.17"), (3, "2001:db8:1::/48")]


def register():
    route("GET", API + "/cdn", lambda ctx: data([cdn_list_item(d) for d in cdn_domains()]))
    route("POST", API + "/cdn", lambda ctx: (201, data(_cdn("cdn_new00005", str(ctx.body.get("domain") or "new.home64.de"), 1006,
                                                           proxy="off", cert="default", pending=["server_assignment"], mb=(0.0, 0.0), days=0))))
    route("GET", API + "/cdn/{token}", lambda ctx: data(cdn_detail(ctx)))
    route("PATCH", API + "/cdn/{token}", cdn_update)
    route("DELETE", API + "/cdn/{token}", lambda ctx: (202, data(_deep_merge(cdn_list_item(cdn_detail(ctx)), {"pending": ["deletion"]}))))
    for path, patch in (("proxy/enable", {"proxy": {"state": "on"}, "pending": ["config_deploy"]}),
                        ("proxy/disable", {"proxy": {"state": "disabling"}}),
                        ("portmapper/enable", {"portmapper": {"state": "on"}, "pending": ["config_deploy"]}),
                        ("portmapper/disable", {"portmapper": {"state": "disabling"}}),
                        ("redeploy", {"pending": ["config_deploy"]}),
                        ("move", {"pending": ["server_move"]}),
                        ("renew-certificate", {"proxy": {"certificate": "issuing"}, "pending": ["certificate"]})):
        route("POST", API + "/cdn/{token}/" + path, lambda ctx, p=patch: (202, data(_deep_merge(cdn_list_item(cdn_detail(ctx)), p))))
    route("POST", API + "/cdn/{token}/reset-traffic", cdn_with({"traffic_mb": {"in": 0.0, "out": 0.0}}))
    route("GET", API + "/cdn/{token}/records", cdn_records)
    route("POST", API + "/cdn/{token}/records/{record}/enable", lambda ctx: (202, cdn_record_toggle("cdn_proxied")(ctx)))
    route("POST", API + "/cdn/{token}/records/{record}/disable", lambda ctx: (202, cdn_record_toggle("active")(ctx)))
    route("GET", API + "/cdn/{token}/mappings", cdn_mappings)
    route("POST", API + "/cdn/{token}/mappings", cdn_mapping_create)
    route("DELETE", API + "/cdn/{token}/mappings/{mapping}", lambda ctx: (204, None))
    route("GET", API + "/cdn/{token}/access-list", lambda ctx: data([{"id": i, "address": a} for i, a in ACCESS]))
    route("POST", API + "/cdn/{token}/access-list",
          lambda ctx: (201, data({"id": 4, "address": ctx.body.get("address") or "203.0.113.99"})))
    route("DELETE", API + "/cdn/{token}/access-list/{id}", lambda ctx: (204, None))

    # DNS64
    route("GET", API + "/dns64/clients", lambda ctx: data([dns64_client(c, detail=False) for c in DNS64_CLIENTS]))
    route("POST", API + "/dns64/clients", lambda ctx: (201, data(dns64_client(
        {"id": "d64_new0004", "name": ctx.body.get("name") or "New client", "cid": "k7x2m9", "queries": (0, 0),
         "blocklists": [], "state": "applying"}, detail=True))))
    route("GET", API + "/dns64/clients/{token}", lambda ctx: data(dns64_client(_d64(ctx), detail=True)))
    route("PATCH", API + "/dns64/clients/{token}", dns64_update)
    route("DELETE", API + "/dns64/clients/{token}", lambda ctx: (202, data({"id": ctx.params["token"], "state": "deleting"})))
    route("POST", API + "/dns64/clients/{token}/rules", dns64_add_rule)
    route("DELETE", API + "/dns64/clients/{token}/rules/{id}", lambda ctx: (204, None))
    route("POST", API + "/dns64/clients/{token}/sources", dns64_add_source)
    route("DELETE", API + "/dns64/clients/{token}/sources/{id}", lambda ctx: (204, None))

    # Honeypot64
    route("GET", API + "/honeypot/sensors", lambda ctx: data(honeypot_sensors()))
    route("GET", API + "/honeypot/list-token", data({"token": "demo_hp_8f3a1c9e5b7d4026a1f0",
                                                    "live": True,
                                                    "base": "https://ipv64.net/blocklists/live/demo_hp_8f3a1c9e5b7d4026a1f0/"}))
    route("GET", API + "/honeypot/stats", lambda ctx: data(honeypot_stats()))
    route("POST", API + "/honeypot/reports", lambda ctx: (202, data(
        {"accepted": 42, "rejected": 1, "rejected_reasons": {"not_public": 1},
         "report_id": ctx.body.get("report_id") or "demo-sensor-1700000000", "duplicate": False})))

    # Threat
    route("GET", API + "/threat/ip/{ip}", threat_ip)
    route("GET", API + "/threat/stats", lambda ctx: data(threat_stats()))
    route("GET", API + "/threat/watch", lambda ctx: data(threat_watch()))
    route("POST", API + "/threat/watch", lambda ctx: (201, data(
        {"id": 14, "cidr": ctx.body.get("cidr") or "203.0.113.0/24", "label": ctx.body.get("label"),
         "verified": False, "created_at": iso()})))
    route("DELETE", API + "/threat/watch/{id}", lambda ctx: (204, None))
    route("GET", API + "/threat/watch/alerts", lambda ctx: data(threat_alerts()))
    route("GET", API + "/threat/feed", lambda ctx: data(threat_feed()))
    # NOTE: text/plain in the real API; core.py JSON-encodes it, see report (lead: send str bodies raw).
    route("GET", API + "/threat/passwords/range/{prefix}", password_range)


# ---------------------------------------------------------------- DNS64
def _srv(loc, host, v6, v4s, v6s):
    return {"location": loc, "hostname": host, "doh": f"https://{host}/dns-query/{{cid}}",
            "dot": "{cid}." + host, "doq": "quic://{cid}." + host, "ipv6": v6, "ipv4_shared": v4s, "ipv6_shared": v6s}


DNS64_CLIENTS = [
    {"id": "d64_home0001", "name": "Home network", "cid": "a8f3k2", "queries": (48213, 6120),
     "blocklists": ["ads"], "state": "ready", "blocking": True},
    {"id": "d64_phone002", "name": "Phone (IPv6-only)", "cid": "p4q9z1", "queries": (12877, 1904),
     "blocklists": ["ads", "threats"], "state": "ready", "blocking": True},
    {"id": "d64_lab00003", "name": "Lab VLAN", "cid": "m2c7x5", "queries": (903, 0),
     "blocklists": [], "state": "ready", "blocking": False},
]


def dns64_client(c, detail):
    cid = c["cid"]
    servers = [_srv("Frankfurt", "dns64-fra.ipv64.net", f"2001:db8:64::{cid[:2]}", "198.51.100.64", "2001:db8:64::1"),
               _srv("Nuremberg", "dns64-nbg.ipv64.net", f"2001:db8:65::{cid[:2]}", "198.51.100.65", "2001:db8:65::1")]
    for s in servers:
        for k in ("doh", "dot", "doq"):
            s[k] = s[k].replace("{cid}", cid)
        if detail:
            s["stamps"] = {k: "sdns://demo" + hashlib.sha1((cid + k).encode()).hexdigest()[:24] for k in ("doh", "dot", "doq")}
    out = {"id": c["id"], "name": c["name"], "client_id": cid, "state": c.get("state", "ready"),
           "blocking_enabled": c.get("blocking", True), "blocklists": c["blocklists"], "servers": servers,
           "queries_total": c["queries"][0], "queries_blocked": c["queries"][1], "created_at": iso(days_ago=21)}
    if detail:
        out["rules"] = [{"id": 31, "domain": "ads.example.com", "action": "block", "state": "ready"},
                        {"id": 32, "domain": "updates.example.com", "action": "allow", "state": "ready"},
                        {"id": 33, "domain": "tracker.example.net", "action": "block", "state": "ready"}]
        out["sources"] = [{"id": "17", "kind": "ip", "address": "203.0.113.45"},
                          {"id": "rec_demo_a", "kind": "record", "address": "192.0.2.10", "fqdn": "demo.home64.de"}]
    return out


def _d64(ctx):
    t = ctx.params.get("token")
    return next((c for c in DNS64_CLIENTS if c["id"] == t), {**DNS64_CLIENTS[0], "id": t})


def dns64_update(ctx):
    c = dict(_d64(ctx))
    b = ctx.body
    if "name" in b: c["name"] = b["name"]
    if "blocking_enabled" in b: c["blocking"] = b["blocking_enabled"]
    if "blocklists" in b: c["blocklists"] = b["blocklists"]
    return data(dns64_client(c, detail=True))


def dns64_add_rule(ctx):
    d = dns64_client(_d64(ctx), detail=True)
    d["rules"].append({"id": 40, "domain": ctx.body.get("domain") or "example.org",
                       "action": ctx.body.get("action") or "block", "state": "applying"})
    return 201, data(d)


def dns64_add_source(ctx):
    d = dns64_client(_d64(ctx), detail=True)
    b = ctx.body
    d["sources"].append({"id": "18", "kind": "ip", "address": b["ip"]} if b.get("ip") else
                        {"id": str(b.get("record") or "rec_cam_a"), "kind": "record", "address": "198.51.100.23", "fqdn": "cam.home64.de"})
    return 201, data(d)


# ---------------------------------------------------------------- Honeypot64
def honeypot_sensors():
    return [{"id": "5b1e6c3a-0d2f-4a7e-9c11-demo00000001", "version": "1.4.2", "profile": "standard", "mode": "module",
             "first_seen": iso(days_ago=45), "last_seen": iso(minutes_ago=4), "online": True, "reports": 3120,
             "disabled": False, "sources_24h": 187, "sources_7d": 902, "events_24h": 2416, "events_7d": 14873},
            {"id": "9d4a7f20-6b8c-4e15-a3d2-demo00000002", "version": "1.4.2", "profile": "minimal", "mode": "headless",
             "first_seen": iso(days_ago=20), "last_seen": iso(hours_ago=3), "online": False, "reports": 870,
             "disabled": False, "sources_24h": 0, "sources_7d": 122, "events_24h": 0, "events_7d": 1904}]


def honeypot_stats():
    kinds = [("ssh:bruteforce", 6120, 301), ("http:exploit:sqli", 2210, 87), ("generic:portscan", 1980, 412),
             ("telnet:bruteforce", 1405, 96), ("rdp:bruteforce", 760, 54)]
    ports = [("ssh", 22, 6300, 320), ("http", 80, 3100, 190), ("telnet", 23, 1500, 99), ("rdp", 3389, 780, 57), ("https", 443, 640, 61)]
    ev = [1710, 2250, 2040, 2390, 2105, 1990, 2416]
    return {"sensors": 2, "sensors_online": 1, "contributor": True, "last_report": iso(minutes_ago=4),
            "events_24h": 2416, "events_7d": 16777, "sources_24h": 187, "sources_7d": 1024,
            "top_kinds": [{"kind": k, "events": e, "sources": s} for k, e, s in kinds],
            "top_ports": [{"service": sv, "port": p, "events": e, "sources": s} for sv, p, e, s in ports],
            "timeline": [{"day": iso(days_ago=6 - i)[:10], "events": e} for i, e in enumerate(ev)]}


# ---------------------------------------------------------------- Threat
def threat_ip(ctx):
    ip = ctx.params["ip"]
    listed = not ip.endswith(".1")
    v6 = ":" in ip
    base = {"ip": ip, "family": 6 if v6 else 4, "listed": listed, "status": "listed" if listed else "not_listed",
            "lists": [], "kinds": [], "ports": [], "release": {"status": "none"},
            "details": f"https://ipv64.net/threat/ip/{ip}"}
    if listed:
        base.update({"score": 18, "lists": ["ssh", "scanner"], "kinds": ["ssh:bruteforce", "generic:portscan"],
                     "ports": [{"service": "ssh", "port": 22}, {"service": "telnet", "port": 23}],
                     "first_seen": iso(days_ago=30)[:10], "last_seen": iso(days_ago=1)[:10],
                     "accounts": 14, "country": "NL", "asn": 64496})
    return data(base)


def threat_stats():
    ent = lambda items: [{"value": v, "events": e, "accounts": a} for v, e, a in items]
    return {"generated_at": iso(minutes_ago=6), "window_days": 30, "threshold": {"accounts": 3, "active_accounts_7d": 842},
            "sensors": {"active_7d": 1310, "online": 1127}, "attacks_24h": {"events": 3481220, "sources": 52310},
            "timeline": [{"day": iso(days_ago=29 - i)[:10], "events": 2900000 + (i * 37 % 11) * 85000} for i in range(30)],
            "top_ports": [{"service": s, "port": p, "events": e} for s, p, e in
                          [("ssh", 22, 9120000), ("http", 80, 4410000), ("telnet", 23, 2750000), ("rdp", 3389, 1980000), ("smb", 445, 1210000)]],
            "top_kinds": [{"kind": k, "events": e} for k, e in
                          [("ssh:bruteforce", 8800000), ("generic:portscan", 5100000), ("http:exploit:sqli", 2300000), ("telnet:bruteforce", 1900000)]],
            "lists": {"v64": 48210, "honeypot_passwords": 25000, "honeypot_usernames": 8000},
            "top": {"usernames": ent([("root", 5120000, 1100), ("admin", 2210000, 1040), ("user", 910000, 802), ("test", 640000, 760)]),
                    "passwords": ent([("123456", 1200000, 1090), ("password", 880000, 1002), ("admin", 640000, 950), ("qwerty", 410000, 700)]),
                    "paths": ent([("/wp-login.php", 410000, 640), ("/.env", 380000, 590), ("/admin", 210000, 500)]),
                    "user_agents": ent([("Mozilla/5.0 (compatible; demo-scanner)", 190000, 310), ("curl/8.0", 120000, 280)]),
                    "fingerprints": ent([("ja4:t13d1516h2_demo", 90000, 120)]),
                    "countries": [{"country": c, "ips": i, "events": e} for c, i, e in
                                  [("NL", 9100, 5200000), ("US", 8200, 4100000), ("DE", 3100, 1100000), ("FR", 2300, 900000)]]},
            "info": "Demo statistics of Honeypot64.", "license": "CC BY 4.0"}


def threat_watch():
    return {"limits": {"max": 10, "min_prefix_v4": 16, "min_prefix_v6": 32},
            "networks": [{"id": 11, "cidr": "192.0.2.0/24", "label": "Office", "verified": True, "created_at": iso(days_ago=30)},
                         {"id": 12, "cidr": "198.51.100.23/32", "label": "Webserver", "verified": False, "created_at": iso(days_ago=9)},
                         {"id": 13, "cidr": "2001:db8::/48", "label": "Home IPv6", "verified": True, "created_at": iso(days_ago=4)}],
            "dyndns": [{"ip": "192.0.2.10", "domain": "demo.home64.de"}, {"ip": "198.51.100.23", "domain": "cam.home64.de"}]}


def _alert(i, ip, cidr, nid, src, label, score, kinds, hours_ago, reason, seen):
    return {"id": 900 + i, "ip": ip, "cidr": cidr, "network_id": nid, "source": src, "label": label, "listed": True,
            "score": score, "kinds": kinds, "first_seen": iso(days_ago=3)[:10], "last_seen": iso(hours_ago=hours_ago)[:10],
            "created_at": iso(hours_ago=hours_ago),
            "details": {"accounts": 6 + i, "sensors": 9 + i, "events": 240 * (i + 1), "lists": ["ssh"], "reason": reason,
                        "seen": [{"origin": "honeypot", "service": sv, "port": p, "kind": k, "count": c, "sensors": 5,
                                  "last": iso(hours_ago=hours_ago)} for sv, p, k, c in seen]}}


def threat_alerts():
    # oldest first, like the real API
    al = [_alert(1, "203.0.113.77", "192.0.2.0/24", 11, "network", "Office", 12, ["ssh:bruteforce"], 70, "threshold",
                 [("ssh", 22, "ssh:bruteforce", 410)]),
          _alert(2, "198.51.100.201", "198.51.100.23/32", 12, "network", "Webserver", 25,
                 ["http:exploit:sqli", "generic:portscan"], 30, "threshold",
                 [("http", 80, "http:exploit:sqli", 120), ("http", 443, "generic:portscan", 60)]),
          _alert(3, "203.0.113.130", "192.0.2.10/32", None, "dyndns", "demo.home64.de", 8, ["telnet:bruteforce"], 6,
                 "customer_ip", [("telnet", 23, "telnet:bruteforce", 95)])]
    return {"alerts": al, "next": iso(hours_ago=6)}


def threat_feed():
    return {"changes": [{"ip": "203.0.113.77", "action": "add", "lists": ["ssh"], "previous_lists": [], "at": iso(minutes_ago=20)},
                        {"ip": "198.51.100.201", "action": "update", "lists": ["ssh", "web"], "previous_lists": ["ssh"], "at": iso(minutes_ago=12)},
                        {"ip": "203.0.113.9", "action": "remove", "lists": [], "previous_lists": ["scanner"], "at": iso(minutes_ago=5)}],
            "next": f"c{epoch()}", "more": False}


_COMMON = {"123456": 1200000, "password": 880000, "admin": 640000, "qwerty": 410000, "letmein": 98000,
           "demo1234": 120, "hunter2": 2300}


def password_range(ctx):
    """HIBP-style: <35 hex>:<count> lines for every known password whose SHA-1 starts with the prefix, plus padding."""
    prefix = ctx.params["prefix"].upper()
    lines = []
    for pw, n in _COMMON.items():
        h = hashlib.sha1(pw.encode()).hexdigest().upper()
        if h.startswith(prefix):
            lines.append(f"{h[5:]}:{n}")
    lines += [f"{hashlib.sha1((prefix + str(i)).encode()).hexdigest().upper()[5:]}:0" for i in range(5)]
    return "\r\n".join(lines)
