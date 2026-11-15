# NekoShield

> **Early development build — DNS protection working on Android + Android TV**

NekoShield is a network privacy and management app from **Nekomo**, designed for Android phones, tablets, and Android TV.

Inspired by the broad network-control approach of projects such as [Rethink DNS](https://github.com/celzero/rethink-app), NekoShield brings **DNS, filtering, and network diagnostics** together in one accessible app.

**Project status:** early build. The DNS-only VPN core, provider presets (UDP / DoT / DoH), remote blocklists, user rules, per-app bypass, query log, and diagnostics are implemented and verified on a physical Android TV (Android 14) and phone (Android 17). Proxy profiles and a full TCP relay are still planned.

## Features

### Implemented
- **DNS-only VPN tunnel** — Android `VpnService` routes only the virtual DNS IPs through the app, so lookups are filtered without proxying (or slowing down) the rest of your traffic. No full-TCP relay, minimal battery cost.
- **Provider presets** — Cloudflare, Cloudflare Family, Google, Quad9, AdGuard, AdGuard Family, Mullvad, plus DoH endpoints for networks that block port 853.
- **Custom DNS** — plain UDP, DNS-over-TLS, or DNS-over-HTTPS endpoint of your choice.
- **Encrypted DNS** — DoT via persistent `SSLSocket`, DoH via OkHttp.
- **Remote blocklists** — an AdGuard-style categorized catalog (Ad blocking, Tracking protection, Security, Annoyances, Adult & other) with 20+ free lists: StevenBlack, HaGeZi (Light/Normal/Pro/TIF/native/gambling/fake/popup-ads), OISD, AdGuard DNS Filter, AdAway, Dan Pollock, 1Hosts, Phishing Army, WindowsSpyBlocker, Amnesty NSO indicators, and more. Each list shows its domain count and last-update date, and can be toggled individually.
- **Custom filter lists** — add any public hosts/domain/adblock-format list URL; it is downloaded, counted, and updated alongside the built-in ones.
- **User rules** — personal allow/block lists; allow always wins.
- **Per-app bypass** — pick apps whose DNS skips the tunnel.
- **Query log** — last 500 decisions, in-memory only, never uploaded.
- **Diagnostics** — resolver test, latency check, leak check, tunnel state, raw wire answers.
- **Android TV** — same layout, D-pad focus rings, remote navigation.

### Planned
- HTTP(S)/SOCKS proxy profiles and per-app proxy routing.
- Richer traffic visibility and per-app rules.

## How it works

Only the two virtual DNS IPs (`10.55.66.2`, `10.55.66.3`) are routed into the tunnel. Every packet that arrives is a DNS query: the app matches the name against your allow/block rules, answers filtered domains with `NXDOMAIN`, and forwards everything else to the configured upstream — your phone's own resolver, a plain UDP server, DoT, or DoH. Replies are reassembled into IPv4/UDP packets and written back. Nothing else ever enters the tunnel, so streaming, gaming, and downloads keep their normal speed.

## Privacy, Security & Transparency

- Queries leave the device only to the DNS provider you pick — that provider can see them.
- The query log lives in memory and clears when the app closes; nothing is uploaded.
- Blocklists download over HTTPS and stay on-device.
- No analytics, no accounts, no secrets committed.
- In-app legal docs: privacy policy, terms of use, disclaimer, and DMCA/contact info ship inside Settings → Legal and in the repo (`PRIVACY.md`, `TERMS.md`, `DISCLAIMER.md`, `DMCA.md`).
- **One VPN at a time** — Android allows a single active VPN per user/profile. Turning NekoShield on asks other VPN apps to stop; it never silently takes over. Always-on and lockdown work with Android's own VPN settings.
- No promise of anonymity or complete ad blocking — coverage depends on your lists and provider.

## Platforms

| Platform | Status |
| --- | --- |
| Android phones | Working build (minSdk 24, targetSdk 36) |
| Android tablets | Working build |
| Android TV / Google TV | Working build, D-pad verified |

Verified on physical hardware: Pixel-class phone (Android 17) and an Android TV box (Android 14). The VPN consent dialog is a system component; on some pre-release Android builds it may dismiss early — a manual tap usually completes it.

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK. `local.properties` should point `sdk.dir` at your SDK (use forward slashes on Windows — backslashes get eaten as escapes).

## Disclaimer

NekoShield is an independent project within the Nekomo organization. Rethink DNS is referenced only as an example; NekoShield is not affiliated with it. Third-party provider names belong to their owners. Results depend on the selected providers, active configuration, and Android version.

Legal documents ship in the repo and inside the app under Settings → Legal: [Privacy Policy](PRIVACY.md) · [Terms of Use](TERMS.md) · [Disclaimer](DISCLAIMER.md) · [DMCA & Content Removal](DMCA.md)

## Links

- [Nekomo organization](https://github.com/Nekomo-App)
- [NekoShield repository](https://github.com/Nekomo-App/NekoShield)
- [Rethink DNS (inspiration/reference)](https://github.com/celzero/rethink-app)

---

**NekoShield — private, filtered DNS for your whole device.**
