"""Cloud API part A: Meta, Account, ApiKeys, Domains, DynDNS, Records, Integrations, Nodes (hc2/nodes),
Offline pages, classic Status pages, Failover, classic Healthchecks."""
import copy

from core import API, NODE1_ID, data, echo, epoch, iso, page, route

MY_V4 = "192.0.2.17"
MY_V6 = "2001:db8::17"


import hashlib


def _md5(name):  # same tokens as the hc2 mock checks
    return hashlib.md5(name.encode()).hexdigest()


def tok(seed):
    """Deterministic fake 32 char token."""
    return ("demo_" + str(seed) * 32)[:32]


def unit(_ctx=None):
    return 204, None


def first_or(items, key, value, default=None):
    """Item whose key equals value (as str), else the first item (handlers never fail for unknown ids)."""
    return next((i for i in items if str(i.get(key)) == str(value)), default if default is not None else items[0])


# ---------------------------------------------------------------- domains / records

# (id, name, kind, records[(prefix, type, content, state)], extra)
_DOMAINS = [
    (101, "demo.home64.de", "dyndns", [("", "A", MY_V4, "active"), ("", "AAAA", MY_V6, "active"),
                                       ("www", "CNAME", "demo.home64.de", "active")], {"update_count": 4821}),
    (102, "nas.home64.de", "dyndns", [("", "A", MY_V4, "active"), ("", "AAAA", MY_V6, "active"),
                                      ("files", "A", MY_V4, "active")], {"wildcard": True, "update_count": 2210}),
    (103, "vpn.home64.de", "dyndns", [("", "A", "198.51.100.23", "active"),  # mismatch: differs from MY_V4
                                      ("", "AAAA", "2001:db8::23", "active")], {"update_count": 377}),
    (104, "shop.example.com", "own", [("", "A", "203.0.113.10", "active"), ("", "AAAA", "2001:db8:1::10", "active"),
                                      ("www", "CNAME", "shop.example.com", "active"),
                                      ("", "MX", "10 mail.example.com", "active"),
                                      ("", "TXT", "v=spf1 mx -all", "active"),
                                      ("cdn", "A", "203.0.113.20", "cdn_proxied")], {"update_count": 12}),
    (105, "cam.home64.de", "dyndns", [("", "A", MY_V4, "active")], {"ignore": "ipv6", "update_count": 9034}),
    (106, "new.home64.de", "dyndns", [], {"update_count": 0, "active": True}),  # no A/AAAA record
    (107, "git.home64.de", "dyndns", [("", "A", MY_V4, "active"), ("", "AAAA", MY_V6, "active")],
     {"ipv6_prefix": "", "update_count": 640}),
]


def _record(domain_id, domain, rid, prefix, rtype, content, state, minutes_ago):
    fqdn = f"{prefix}.{domain}" if prefix else domain
    return {"id": rid, "domain_id": domain_id, "domain": domain, "prefix": prefix, "fqdn": fqdn, "type": rtype,
            "content": content, "ttl": 60, "state": state, "pending_dns": False, "token": tok(rid % 10),
            "updated_at": iso(minutes_ago=minutes_ago)}


def _domain(d, with_records):
    did, name, kind, recs, extra = d
    zone = ".".join(name.split(".")[-2:]) if kind == "dyndns" else name
    out = {"id": did, "name": name, "kind": kind, "zone": zone, "wildcard": extra.get("wildcard", False),
           "ignore": extra.get("ignore", "none"), "ipv6_prefix": extra.get("ipv6_prefix"), "active": True,
           "update_token": f"demo_upd_{did}" + "x" * 20, "update_count": extra.get("update_count", 0),
           "created_at": iso(days_ago=300 - did), "updated_at": iso(minutes_ago=3 + did % 7)}
    if with_records:
        out["records"] = [_record(did, name, did * 10 + i, *r, minutes_ago=3 + i) for i, r in enumerate(recs)]
    return out


def _find_domain(ref):
    return next((d for d in _DOMAINS if ref in (str(d[0]), d[1])), _DOMAINS[0])


def _all_records():
    return [r for d in _DOMAINS for r in _domain(d, True)["records"]]


def domains_list(ctx):
    kind = ctx.query.get("kind")
    return data([_domain(d, False) for d in _DOMAINS if not kind or d[2] == kind])


def domain_create(ctx):
    name = ctx.body.get("name", "new.home64.de")
    d = _domain((108, name, "own" if ctx.body.get("own") else "dyndns", [], {}), True)
    return 201, data(d)


def domain_get(ctx):
    return data(_domain(_find_domain(ctx.params["domain"]), True))


def domain_update(ctx):
    d = _domain(_find_domain(ctx.params["domain"]), True)
    d.update({k: v for k, v in ctx.body.items() if k in ("wildcard", "ignore", "ipv6_prefix")})
    return data(d)


def domain_rotate(ctx):
    d = _domain(_find_domain(ctx.params["domain"]), False)
    d["update_token"] = "demo_upd_rotated" + "z" * 16
    return data(d)


def domain_reset(ctx):
    d = _domain(_find_domain(ctx.params["domain"]), True)
    d["records"] = []
    return data(d)


def records_list(ctx):
    recs = _domain(_find_domain(ctx.params["domain"]), True)["records"]
    q = ctx.query
    return data([r for r in recs if (not q.get("type") or r["type"] == q["type"])
                 and (q.get("prefix") is None or r["prefix"] == q["prefix"])])


def record_create(ctx):
    d = _find_domain(ctx.params["domain"])
    prefix = ctx.body.get("prefix") or ""
    prefix = "" if prefix == "@" else prefix
    r = _record(d[0], d[1], 9000 + epoch() % 1000, prefix, ctx.body.get("type", "A"),
                ctx.body.get("content", MY_V4), "active", 0)
    return 201, data(r)


def record_get(ctx):
    recs = _all_records()
    ref = ctx.params["record"]
    return data(next((r for r in recs if ref in (str(r["id"]), r["token"])), recs[0]))


def record_update(ctx):
    recs = _all_records()
    ref = ctx.params["record"]
    r = copy.deepcopy(next((r for r in recs if ref in (str(r["id"]), r["token"])), recs[0]))
    r.update({k: v for k, v in ctx.body.items() if k in ("type", "prefix", "content")})
    r["updated_at"] = iso()
    return data(r)


def dyndns_update(ctx):
    d = _find_domain(ctx.params["domain"])
    b = ctx.body
    results = [{"ip": ip, "type": t, "result": "unchanged"}
               for ip, t in ((b.get("ipv4") or MY_V4, "A"), (b.get("ipv6") or MY_V6, "AAAA")) if ip]
    return data({"domain": d[1], "prefix": b.get("prefix") or "", "changed": False, "results": results})


# ---------------------------------------------------------------- account

def me(_ctx):
    def c(used, limit):
        return {"used": used, "limit": limit}
    return data({
        "id": 4711, "email": "demo@example.com", "username": "Demo User",
        "registered_at": iso(days_ago=640), "last_login_at": iso(hours_ago=5),
        "plan": {"id": 3, "name": "Pro", "valid_until": iso(days_ago=-200)},
        "usage": {"dyndns_domains": c(6, 20), "own_domains": c(1, 5), "dyndns_updates_24h": c(412, 5000),
                  "healthchecks": c(8, 50), "healthcheck_pings_24h": c(2300, 20000), "sms_24h": c(0, 10),
                  "failover_policies": c(1, 5), "failover_monitors": c(2, 10), "failover_rules": c(2, 10),
                  "cdn_domains": c(2, 5), "cdn_traffic_gb": c(3.4, 100),
                  "vpn_traffic_gb": {"used": 12.8, "limit": 500, "limited": False}, "dns64_clients": c(2, 10)},
        "log_retention_days": 30,
        "auth": {"type": "api_key", "key_id": 2, "key_name": "Android app"}})


_LOGS = [("Domain updated", "demo.home64.de: A set to 192.0.2.17"), ("Domain updated", "nas.home64.de: A unchanged"),
         ("Record created", "files.nas.home64.de A 192.0.2.17"), ("Login", "Login from 198.51.100.7"),
         ("Healthcheck down", "Router WAN did not respond"), ("Healthcheck up", "Router WAN recovered"),
         ("API key created", "Key 'Android app' created"), ("Domain updated", "cam.home64.de: A set to 192.0.2.17"),
         ("Integration added", "Telegram 'Alerts' added"), ("Failover", "Policy 'Web failover' armed"),
         ("Domain created", "git.home64.de created"), ("Login", "Login from 203.0.113.44"),
         ("Domain updated", "vpn.home64.de: A set to 198.51.100.23"), ("Record deleted", "old.demo.home64.de A"),
         ("Token rotated", "Update token of vpn.home64.de rotated")]


def logs(ctx):
    n = len(_LOGS)
    before = int(ctx.query["before"]) if ctx.query.get("before", "").isdigit() else n + 1
    limit = int(ctx.query.get("limit") or 50)
    items = [{"id": n - i, "domain_id": 101 + i % 5, "title": t, "message": m, "time": iso(minutes_ago=7 + i * 95)}
             for i, (t, m) in enumerate(_LOGS) if n - i < before][:limit]
    nxt = items[-1]["id"] if items and len(items) == limit and items[-1]["id"] > 1 else None
    return {"data": items, "meta": {"limit": limit, "next_before": nxt, "retention_days": 30}}


def account_links(_ctx):
    return data({
        "shared_with": [{"id": 1, "direction": "shared_with", "email": "partner@example.com", "status": "active",
                         "created_at": iso(days_ago=90), "last_used_at": iso(days_ago=2)},
                        {"id": 2, "direction": "shared_with", "email": "admin@example.org", "status": "pending",
                         "created_at": iso(days_ago=1)}],
        "managed": [{"id": 3, "direction": "managed", "email": "friend@example.net", "status": "active",
                     "created_at": iso(days_ago=200), "last_used_at": iso(days_ago=10), "account_id": 5120}]})


def api_keys(_ctx):
    return data([
        {"id": 2, "name": "Android app", "token_hint": "demo…a1b2", "created_at": iso(days_ago=120),
         "expires_at": None, "expired": False, "uses": 18342},
        {"id": 5, "name": "Home automation", "token_hint": "demo…c3d4", "created_at": iso(days_ago=60),
         "expires_at": iso(days_ago=-300), "expired": False, "uses": 5120},
        {"id": 7, "name": "Old CI key", "token_hint": "demo…e5f6", "created_at": iso(days_ago=400),
         "expires_at": iso(days_ago=30), "expired": True, "uses": 211}])


def api_key_create(ctx):
    exp = ctx.body.get("expires_in") or 0
    return 201, data({"id": 9, "name": ctx.body.get("name") or "New key", "token_hint": "demo…z9y8",
                      "created_at": iso(), "expires_at": iso(seconds_ago=-exp) if exp else None,
                      "expired": False, "uses": 0, "token": "demo_" + "k" * 40})


# ---------------------------------------------------------------- integrations

def _integrations():
    return [
        {"id": 0, "type": "account_email", "name": "Account e-mail", "status": "active", "default": True,
         "options": {"email": "demo@example.com"}, "created_at": iso(days_ago=640), "last_used_at": iso(days_ago=1), "uses": 41},
        {"id": 11, "type": "telegram", "name": "Alerts", "status": "active", "default": True,
         "options": {"chat_id": "123456789", "api_token": "demo_telegram_token"}, "created_at": iso(days_ago=200),
         "last_used_at": iso(hours_ago=6), "uses": 87},
        {"id": 12, "type": "ntfy", "name": "Phone ntfy", "status": "active", "default": False,
         "options": {"server_url": "https://ntfy.example.com", "topic": "demo-alerts"}, "created_at": iso(days_ago=90),
         "last_used_at": iso(days_ago=3), "uses": 12},
        {"id": 13, "type": "discord", "name": "Team Discord", "status": "pending_verification", "default": False,
         "options": {"webhook_url": "https://discord.example.com/api/webhooks/demo"}, "created_at": iso(days_ago=1),
         "uses": 0}]


def integration_get(ctx):
    items = _integrations()
    return data(first_or(items, "id", ctx.params["id"]))


def integration_create(ctx):
    b = ctx.body
    return 201, data({"id": 14, "type": b.get("type", "webhook"), "name": b.get("name", "New"), "status": "active",
                      "default": bool(b.get("default")), "options": {}, "created_at": iso(), "uses": 0})


def integration_update(ctx):
    i = copy.deepcopy(first_or(_integrations(), "id", ctx.params["id"]))
    i.update({k: v for k, v in ctx.body.items() if k in ("name", "default")})
    return data(i)


# ---------------------------------------------------------------- nodes

def _node(node_id, name, location, online, minutes, mgmt="full", checks=0):
    return {"node_id": node_id, "name": name, "location": location, "version": "1.8.2", "timezone": "Europe/Berlin",
            "modules": ["healthchecks", "dyndns", "honeypot"], "remote_mgmt": mgmt, "online": online,
            "last_seen": iso(minutes_ago=minutes), "last_sync": iso(minutes_ago=minutes + 1), "outbox_size": 0,
            "conflicts": 0, "hub": "hub.example.com", "linked_at": iso(days_ago=45)}


def _nodes():
    return [_node(NODE1_ID, "Homelab", "Basement rack", True, 1),
            _node("nd_vps_cloud03", "Cloud VPS", "Frankfurt", True, 2, mgmt="read")]


def node_get(ctx):
    n = next((x for x in _nodes() if x["node_id"] == ctx.params["node_id"]), None)
    if n is None:  # direct-only nodes have no cloud entry
        return 404, {"type": "about:blank", "title": "Not Found", "status": 404, "detail": "Node not found"}
    checks = [{"token": tok(1), "name": "Router WAN", "type": "icmp", "target": "192.168.1.1", "interval_seconds": 60, "node_id": n["node_id"],
               "state": {"state": "up", "last_check_at": iso(seconds_ago=40), "last_latency_ms": 1.2}},
              {"token": tok(2), "name": "NAS web", "type": "http", "target": "http://192.168.1.20", "interval_seconds": 120, "node_id": n["node_id"],
               "state": {"state": "up", "last_check_at": iso(seconds_ago=70), "last_latency_ms": 38.0}}]
    return data({**n, "checks": checks})


def node_update(ctx):
    n = copy.deepcopy(first_or(_nodes(), "node_id", ctx.params["node_id"]))
    n.update({k: v for k, v in ctx.body.items() if k in ("name", "location", "remote_mgmt")})
    return data(n)


def node_commands(ctx):
    nid = ctx.params["node_id"]
    return data([
        {"command_id": 31, "node_id": nid, "name": "run_check", "args": {"token": tok(1)}, "created_at": iso(minutes_ago=30),
         "done_at": iso(minutes_ago=29), "ok": True, "output": {"status": "up", "latency_ms": 12.4}},
        {"command_id": 30, "node_id": nid, "name": "pause", "args": {"token": tok(2)}, "created_at": iso(hours_ago=5),
         "done_at": iso(hours_ago=5), "ok": True, "output": "paused"},
        {"command_id": 29, "node_id": nid, "name": "repair", "args": {}, "created_at": iso(days_ago=1),
         "done_at": iso(days_ago=1), "ok": False, "output": {"error": "nothing to repair"}}])


def node_send_command(ctx):
    return 202, data({"command_id": 32, "node_id": ctx.params["node_id"], "name": ctx.body.get("name", "run_check"),
                      "args": ctx.body.get("args") or {}, "created_at": iso(), "done_at": None, "ok": None})


# ---------------------------------------------------------------- offline pages / status pages

def _offline_pages():
    return [{"id": "op_demo01", "name": "Maintenance page", "domain": "shop.example.com", "email": "demo@example.com",
             "phone": "+49 30 1234567", "alternative_url": "https://status.example.com",
             "text": "We are doing maintenance and are back shortly.", "icon_url": None, "views": 42,
             "in_use_by_policy": "fp_demo01", "active": True,
             "preview_url": "https://offline.example.com/op_demo01"},
            {"id": "op_demo02", "name": "Sorry page", "domain": "demo.home64.de", "email": "demo@example.com",
             "phone": None, "alternative_url": None, "text": "The service is temporarily unavailable.",
             "icon_url": None, "views": 0, "in_use_by_policy": None, "active": True,
             "preview_url": "https://offline.example.com/op_demo02"}]


def offline_get(ctx):
    return data(first_or(_offline_pages(), "id", ctx.params["id"]))


def offline_create(ctx):
    return 201, data({**_offline_pages()[1], "id": "op_demo03", "views": 0, **ctx.body})


def offline_update(ctx):
    return data({**first_or(_offline_pages(), "id", ctx.params["id"]), **ctx.body})


def _status_pages():
    return [{"id": 1, "name": "Demo Status", "slug": "demo-status", "url": "https://status.example.com/demo-status",
             "description": "Public status of the demo services", "password_protected": False, "design": 1,
             "autorefresh": True, "healthchecks": [_md5(k) for k in ("web", "vpn", "shop")], "created_at": iso(days_ago=120),
             "last_viewed_at": iso(minutes_ago=14)}]


def status_get(ctx):
    return data(first_or(_status_pages(), "id", ctx.params["id"]))


def status_create(ctx):
    b = ctx.body
    return 201, data({**_status_pages()[0], "id": 2, "slug": b.get("slug") or "demo-page", "healthchecks": [],
                      "password_protected": bool(b.get("password")), **{k: v for k, v in b.items() if k != "password"}})


def status_update(ctx):
    b = ctx.body
    return data({**first_or(_status_pages(), "id", ctx.params["id"]),
                 **{k: v for k, v in b.items() if k != "password"}})


# ---------------------------------------------------------------- failover

def _policy(pid="fp_demo01", name="Web failover", status="active"):
    return {"id": pid, "name": name, "status": status, "manual": False, "mode": "auto_failover_and_fallback",
            "fallback_requires_all": False, "linking": "or", "notifications_enabled": True, "integrations": [11],
            "monitors_down": 0, "last_check_at": iso(minutes_ago=1),
            "monitors": [{"healthcheck": tok(1), "name": "Shop HTTP", "negate": False, "healthcheck_status": "up"},
                         {"healthcheck": tok(2), "name": "Router WAN", "negate": False, "healthcheck_status": "up"}],
            "rules": [{"record": "1040", "fqdn": "shop.example.com", "record_type": "A", "state": "standby",
                       "target": {"kind": "ip", "ip": "203.0.113.99"}},
                      {"record": "1041", "fqdn": "www.shop.example.com", "record_type": "AAAA", "state": "standby",
                       "target": {"kind": "offline_page", "offline_page": "op_demo01"}}]}


def policies(_ctx):
    return data([_policy(), _policy("fp_demo02", "Home failover", "paused")])


def policy_get(ctx):
    pid = ctx.params["id"]
    return data(_policy(pid, "Home failover", "paused") if pid == "fp_demo02" else _policy(pid))


def policy_create(ctx):
    return 201, data({**_policy("fp_demo03", "New policy", "pending"), "monitors": [], "rules": [], **ctx.body})


def policy_update(ctx):
    return data({**_policy(ctx.params["id"]), **ctx.body})


def policy_state(status, code=200):
    return lambda ctx: (code, data(_policy(ctx.params["id"], status=status)))


# ---------------------------------------------------------------- classic healthchecks

# (n, name, type, status, check dict)
_HC = [(1, "Shop HTTP", "http", "up", {"url": "https://shop.example.com", "verify_tls": True}),
       (2, "Router WAN", "icmp", "up", {"target": "198.51.100.1"}),
       (3, "NAS SSH", "tcp", "down", {"target": "nas.home64.de", "port": 22}),
       (4, "Backup job", "push", "up", {}),
       (5, "Blog keyword", "keyword", "warning", {"url": "https://blog.example.com", "keyword": "Welcome"}),
       (6, "Old VPN", "icmp", "paused", {"target": "vpn.home64.de"})]


def _hc(row, detail=False):
    n, name, typ, status, chk = row
    out = {"token": tok(n), "name": name, "type": typ, "status": status,
           "notify": {"on_down": True, "on_up": True, "integrations": [11]},
           "created_at": iso(days_ago=100), "last_seen_at": iso(minutes_ago=n), "last_alarm_at": iso(hours_ago=n * 7),
           "pings_total": 1000 + n * 311, "failed_in_row": 3 if status == "down" else 0}
    if typ == "push":
        out["push"] = {"url": f"https://ping.example.com/{tok(n)}", "period_seconds": 3600, "grace_seconds": 300,
                       "expected_by": iso(minutes_ago=-40)}
    else:
        out["check"] = {"interval_seconds": 60, "retry_interval_seconds": 30, "failures_before_alarm": 3,
                        "next_check_at": iso(seconds_ago=-30), "prefer_ipv4": True, **chk}
    if detail:
        out["recent"] = _hc_events(n)
    return out


def _hc_events(n, count=8):
    return [{"id": 500 - i, "time": iso(minutes_ago=5 + i * 60), "status": "up" if i % 4 else "down",
             "message": "OK" if i % 4 else "Timeout", "latency_ms": 12.5 + i} for i in range(count)]


def _hc_row(token):
    return next((r for r in _HC if tok(r[0]) == token or str(r[0]) == token), _HC[0])


def hc_list(ctx):
    q = ctx.query
    return data([_hc(r) for r in _HC if (not q.get("type") or r[2] == q["type"])
                 and (not q.get("status") or r[3] == q["status"])])


def hc_create(ctx):
    b = ctx.body
    return 201, data({**_hc((7, b.get("name", "New check"), b.get("type", "http"), "new",
                             {k: b[k] for k in ("target", "url", "port", "keyword") if k in b}), True)})


def hc_update(ctx):
    return data({**_hc(_hc_row(ctx.params["token"]), True),
                 **({"name": ctx.body["name"]} if "name" in ctx.body else {})})


def hc_set_status(status):
    return lambda ctx: data(_hc(_hc_row(ctx.params["token"])[:3] + (status,) + _hc_row(ctx.params["token"])[4:]))


def hc_rotate(ctx):
    h = _hc(_hc_row(ctx.params["token"]))
    h["token"] = "demo_rotated" + "r" * 20
    return data(h)


def hc_events(ctx):
    limit = int(ctx.query.get("limit") or 100)
    ev = _hc_events(_hc_row(ctx.params["token"])[0], 20)[:limit]
    return {"data": ev, "meta": {"limit": limit, "has_more": False, "next_before": ev[-1]["id"], "next_after": ev[0]["id"]}}


def hc_incidents(ctx):
    return data([
        {"id": 12, "status": "open", "created_at": iso(minutes_ago=42), "events": [
            {"time": iso(minutes_ago=42), "kind": "down", "message": "Timeout after 3 failures"}]},
        {"id": 11, "status": "closed", "created_at": iso(days_ago=2), "closed_at": iso(days_ago=2, minutes_ago=-18),
         "events": [{"time": iso(days_ago=2), "kind": "down", "message": "Connection refused"},
                    {"time": iso(days_ago=2, minutes_ago=-18), "kind": "up", "message": "Recovered"}]}])


def hc_stats(ctx):
    rng = ctx.query.get("range", "24h")
    hours = {"1h": 1, "24h": 24, "7d": 168, "30d": 720}.get(rng, 24)
    step = max(hours * 3600 // 24, 60)
    series = [{"time": iso(seconds_ago=(23 - i) * step), "measurements": step // 60,
               "availability_percent": 100.0 if i != 9 else 93.3, "latency_avg_ms": 14.0 + i % 5,
               "latency_max_ms": 30.0 + i % 7} for i in range(24)]
    return data({"range": rng, "from": iso(hours_ago=hours), "to": iso(), "measurements": hours * 60,
                 "availability_percent": 99.7, "latency_ms": {"avg": 16.2, "min": 9.1, "max": 88.4},
                 "bucket_seconds": step, "series": series})


# ---------------------------------------------------------------- registration

def register():
    # Meta
    route("GET", API, lambda c: data({"name": "IPv64 API", "version": "2.0.0",
                                      "documentation": "https://ipv64.net/api-docs", "time": iso()}))
    route("GET", API + "/health", data({"status": "ok", "version": "2.0.0"}))
    route("GET", API + "/ip", data({"ip": MY_V4, "family": "ipv4"}))  # same base for v4/v6 -> v6 stays unknown
    route("GET", API + "/openapi.json", {"openapi": "3.0.3", "info": {"title": "IPv64 API", "version": "2.0.0"}, "paths": {}})
    # Account / API keys
    route("GET", API + "/me", me)
    route("GET", API + "/me/logs", logs)
    route("GET", API + "/me/account-links", account_links)
    route("GET", API + "/api-keys", api_keys)
    route("POST", API + "/api-keys", api_key_create)
    route("DELETE", API + "/api-keys/{id}", unit)
    # Domains / DynDNS / Records
    route("GET", API + "/domains", domains_list)
    route("POST", API + "/domains", domain_create)
    route("GET", API + "/domains/{domain}", domain_get)
    route("PATCH", API + "/domains/{domain}", domain_update)
    route("DELETE", API + "/domains/{domain}", unit)
    route("POST", API + "/domains/{domain}/rotate-token", domain_rotate)
    route("POST", API + "/domains/{domain}/reset", domain_reset)
    route("POST", API + "/domains/{domain}/dyndns", dyndns_update)
    route("GET", API + "/domains/{domain}/records", records_list)
    route("POST", API + "/domains/{domain}/records", record_create)
    route("DELETE", API + "/domains/{domain}/records", unit)
    route("GET", API + "/records/{record}", record_get)
    route("PATCH", API + "/records/{record}", record_update)
    route("DELETE", API + "/records/{record}", unit)
    # Integrations
    route("GET", API + "/integrations", lambda c: data(_integrations()))
    route("POST", API + "/integrations", integration_create)
    route("GET", API + "/integrations/{id}", integration_get)
    route("PATCH", API + "/integrations/{id}", integration_update)
    route("DELETE", API + "/integrations/{id}", unit)
    route("POST", API + "/integrations/{id}/test", data({"sent": True}))
    # Nodes (cloud-linked)
    route("POST", API + "/node64/hello", unit)
    route("GET", API + "/node64/nodes", lambda c: data(_nodes()))
    route("GET", API + "/hc2/nodes", lambda c: data(_nodes()))
    route("POST", API + "/hc2/nodes/link", (200, data({"accepted": True, "detail": "Node will fetch its token shortly."})))
    route("GET", API + "/hc2/nodes/{node_id}", node_get)
    route("PATCH", API + "/hc2/nodes/{node_id}", node_update)
    route("DELETE", API + "/hc2/nodes/{node_id}",
          data({"unlinked": True, "checks_kept_online": 2, "checks_deleted": 0}))
    route("GET", API + "/hc2/nodes/{node_id}/commands", node_commands)
    route("POST", API + "/hc2/nodes/{node_id}/commands", node_send_command)
    # Offline pages
    route("GET", API + "/offline-pages", lambda c: data(_offline_pages()))
    route("POST", API + "/offline-pages", offline_create)
    route("GET", API + "/offline-pages/{id}", offline_get)
    route("PATCH", API + "/offline-pages/{id}", offline_update)
    route("DELETE", API + "/offline-pages/{id}", unit)
    route("POST", API + "/offline-pages/{id}/icon", offline_get)
    route("DELETE", API + "/offline-pages/{id}/icon", unit)
    # Status pages (classic)
    route("GET", API + "/status-pages", lambda c: data(_status_pages()))
    route("POST", API + "/status-pages", status_create)
    route("GET", API + "/status-pages/{id}", status_get)
    route("PATCH", API + "/status-pages/{id}", status_update)
    route("DELETE", API + "/status-pages/{id}", unit)
    # Failover
    route("GET", API + "/failover/policies", policies)
    route("POST", API + "/failover/policies", policy_create)
    route("GET", API + "/failover/policies/{id}", policy_get)
    route("PATCH", API + "/failover/policies/{id}", policy_update)
    route("DELETE", API + "/failover/policies/{id}", unit)
    route("POST", API + "/failover/policies/{id}/pause", policy_state("paused"))
    route("POST", API + "/failover/policies/{id}/resume", policy_state("active"))
    route("POST", API + "/failover/policies/{id}/failover", policy_state("failover_requested", 202))
    route("POST", API + "/failover/policies/{id}/fallback", policy_state("fallback_requested", 202))
    route("POST", API + "/failover/policies/{id}/monitors", lambda c: data(_policy(c.params["id"])))
    route("DELETE", API + "/failover/policies/{id}/monitors/{token}", unit)
    route("POST", API + "/failover/policies/{id}/rules", lambda c: data(_policy(c.params["id"])))
    route("DELETE", API + "/failover/policies/{id}/rules/{record}", unit)
    # Healthchecks (classic)
    route("GET", API + "/healthchecks", hc_list)
    route("POST", API + "/healthchecks", hc_create)
    route("GET", API + "/healthchecks/{token}", lambda c: data(_hc(_hc_row(c.params["token"]), True)))
    route("PATCH", API + "/healthchecks/{token}", hc_update)
    route("DELETE", API + "/healthchecks/{token}", unit)
    route("POST", API + "/healthchecks/{token}/pause", hc_set_status("paused"))
    route("POST", API + "/healthchecks/{token}/resume", hc_set_status("up"))
    route("POST", API + "/healthchecks/{token}/rotate-token", hc_rotate)
    route("GET", API + "/healthchecks/{token}/events", hc_events)
    route("GET", API + "/healthchecks/{token}/incidents", hc_incidents)
    route("GET", API + "/healthchecks/{token}/stats", hc_stats)
    route("POST", API + "/healthchecks/{token}/test-notification", data({"sent": True}))
