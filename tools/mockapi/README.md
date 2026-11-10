# Mock API for screenshots

Dummy IPv64 cloud API (v2), Healthchecks 2.0 and two Node64 nodes. Python 3 stdlib only; all data is fictitious
(documentation IP ranges, example.com / home64.de names, fake tokens).

    python3 tools/mockapi/server.py --port 8064      # unknown routes: "UNMAPPED ..." on stderr + 404
    python3 tools/mockapi/selfcheck.py               # hits every route, expects 2xx + valid JSON

Build the app against it (debug only; the property is ignored for release, empty by default):

    ./gradlew :app:installDebug -PmockApi=http://10.0.2.2:8064

The mock URL redirects all cloud clients (API, ipv4/ipv6 "my IP") to `<mock>/api/v2` and push registration to `<mock>/secntfy`.

- Login with API key `demo` (any key is accepted).
- Direct node connections (Network > Node, local access), any key:
  - `http://10.0.2.2:8064/node/api` : "Homelab" (also linked in the cloud list)
  - `http://10.0.2.2:8064/node2/api` : "Cluster-Node-2" (direct only)
- Layout: `core.py` (router), `cloud_a.py` / `cloud_b.py` / `hc2.py` / `node.py` (fixtures).
