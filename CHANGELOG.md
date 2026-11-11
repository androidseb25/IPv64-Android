# Changelog

All notable changes to the IPv64net Android app. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.2.0] - 2026-11-11

### Added
- **Monitor64** in the Healthchecks tab: the monitored hosts of each connected node (servers, VMs, Docker, Proxmox, SNMP and more) with status, charts (touch for time and value), services, open problems (acknowledge, resolve), new services (adopt, ignore) and accesses. Create, edit, pause and delete hosts and their accesses, including a connection test.

### Changed
- IPv64-Package 2.1.0.
- The status filter counts in Healthchecks include node checks and Monitor64 hosts.

### Removed
- The Docker check type (Docker monitoring now lives in Monitor64).

## [2.1.0] - 2026-11-10

### Added
- New five-tab layout: DynDNS, Healthchecks, CDN64 (becomes Proxy64 with a node), DNS64 (with a node) and More.
- **Nodes** under More: link a node to your account or connect directly by local or public IP and API key. The node's modules decide whether the Proxy64 and DNS64 tabs appear.
- **Node healthchecks:** the local checks of each connected node next to your account checks, with detail view (status, statistics, incidents, events) and editor.
- **Proxy64:** proxy and redirect hosts (create, edit, enable/disable, delete), CDN64 per host with mode choice (pass through or terminate on the CDN servers), and CDN statistics with the node's quota.
- **DNS64 dashboard** for nodes: blocking with timed pause (5, 15, 30 min, 1 h, until resumed), query timeline chart, top lists, upstreams, cache, live query log and local DNS records.
- **Honeypot** with network numbers from ipv64.net, attack charts, your own sensors and the share of your nodes running the honeypot.
- **IP lookup & My IP** merged into one screen; password check.
- **Push notifications** via SecNtfy: encrypted messages including images, guided setup right after login (permission, device registration, test message).
- Discord link in More.

### Changed
- All API calls now use the IPv64-Package (API v2); healthchecks moved to Healthchecks 2.0.
- Integrations are replaced by notification channels (More → Channels).
- "Domains" is now called "DynDNS".
- Forms and choices open as bottom sheets (new domain, new DNS record, API key, node link/connection, DynDNS update, pickers and more); confirmations stay dialogs.
- Detail actions are tiles; Save and Delete sit in the top bar.
- Error messages show the server's details instead of a generic connection error.
- Minimum Android version is now Android 12 (API 31).

## [2.0.1] - 2025-12-04

### Added
- Multi-account support.
- Signed release builds (APK and AAB) published as GitHub releases.

## [2.0.0] - 2025-10-06

### Changed
- App rebuilt with Jetpack Compose: domains, healthchecks, integrations, logs, account and QR code login.

## Earlier versions

1.0.1 to 1.1.3 (2023) were the original View-based app with domains, healthchecks, integrations and dark mode.

[2.1.0]: https://github.com/androidseb25/IPv64-Android/compare/v2.0.1...v2.1.0
[2.0.1]: https://github.com/androidseb25/IPv64-Android/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/androidseb25/IPv64-Android/releases/tag/v2.0.0
