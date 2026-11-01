# NekoShield

> **Coming Soon · In Development**

NekoShield is a planned all-in-one network privacy and management app from **Nekomo**, designed for Android phones, tablets, and Android TV.

Inspired by the broad network-control approach of projects such as [Rethink DNS](https://github.com/celzero/rethink-app), NekoShield aims to bring **DNS, VPN, proxy, and network filtering tools** together in one accessible app. The goal is to make network configuration, privacy controls, and connection diagnostics easier to manage from one place.

**Project status:** Coming soon — currently in development. All features below are planned and subject to technical research, implementation, and testing. No public release is available yet.

## Planned Features

### DNS Management
- **DNS provider presets** — choose from supported public and custom DNS services.
- **Custom DNS** — add, edit, and manage DNS configurations.
- **Encrypted DNS** — investigate DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT), subject to Android and provider capabilities.
- **DNS filtering** — use compatible services and lists to filter ads, trackers, malicious domains, and other unwanted traffic.
- **Allowlist and blocklist management** — manage domain exceptions and custom lists where supported.
- **DNS diagnostics** — test resolution, connectivity, and configuration.

### VPN & Network Protection
- **VPN features** — investigate an Android VPN-based local filtering and traffic-routing mode using supported Android APIs.
- **Per-app controls** — explore app-specific routing or filtering where Android's VPN framework allows it.
- **Connection controls** — start, stop, and monitor supported network protection modes.
- **Traffic visibility** — provide understandable connection or traffic information where technically and legally available.
- **Always-on and lockdown compatibility** — document and test Android system-level VPN options where supported.

### Proxy Management
- **Proxy configuration** — add and manage supported proxy server profiles.
- **Proxy protocols** — investigate HTTP(S) and SOCKS proxy support, depending on platform capabilities.
- **Per-app proxy options** — explore supported ways to route selected app traffic through configured proxies.
- **Quick switching** — switch between saved configurations with clear connection status.

### Additional Network Tools
- Network diagnostics and connectivity tests.
- Connection status and troubleshooting information.
- Saved profiles and quick configuration switching.
- Optional rules and filtering controls.
- Clear explanations of active DNS, VPN, proxy, and filtering settings.

Not every feature can necessarily run simultaneously. Android restrictions, VPN-service limitations, provider capabilities, and device compatibility will determine which combinations are supported.

## Planned Platforms

| Platform | Status |
| --- | --- |
| Android phones | Planned |
| Android tablets | Planned |
| Android TV / Google TV | Planned |

The mobile interface will prioritize touch interaction. The Android TV interface will include readable layouts, D-pad focus, and remote-control navigation. Compatibility with specific Android versions and devices will be documented after testing.

## Potential DNS Providers

Provider presets to investigate include:

- AdGuard DNS
- Cloudflare
- Quad9
- NextDNS

Provider support, endpoints, terms, and configuration requirements must be verified before inclusion. Users should be able to add custom configurations where technically supported.

## Privacy, Security & Transparency

NekoShield is intended to make network configuration understandable and transparent.

- Explain what each feature does and which permissions it requires.
- Clearly identify when DNS queries or network traffic are handled by a third-party provider or proxy.
- Avoid collecting unnecessary personal information.
- Never commit API keys, credentials, tokens, or private configuration values.
- Use secure configuration storage where appropriate.
- Document limitations and avoid promising complete anonymity or protection.

DNS providers, VPN operators, and proxy operators may be able to observe some information about network activity. Users should review the privacy policies and trustworthiness of services they configure.

## Android Implementation Notes

Android's system Private DNS, app-level DNS configuration, VPN service APIs, and proxy settings have different capabilities and limitations. Android generally allows only one active VPN service per user/profile at a time, which can conflict with other VPN apps. NekoShield must explain these limitations and avoid silently taking over an existing VPN connection.

The app must not claim to provide system-wide filtering, encrypted DNS, VPN protection, proxy routing, or per-app controls unless the active configuration genuinely supports them. Any traffic inspection or filtering must be implemented transparently, with clear user consent and appropriate security safeguards.

## Development Roadmap

See [ROADMAP.md](ROADMAP.md) for planned milestones.

- [ ] Finalize architecture and supported Android versions
- [ ] Build the Android mobile interface
- [ ] Build Android TV layouts and remote navigation
- [ ] Implement and test DNS configuration and filtering
- [ ] Research and implement supported VPN features
- [ ] Research and implement supported proxy features
- [ ] Add diagnostics, profiles, and connection controls
- [ ] Complete privacy, security, and compatibility testing
- [ ] Prepare the first public release

## Disclaimer

NekoShield is an independent project within the Nekomo organization. Rethink DNS is referenced only as an example of a project with a broad network-management feature set; NekoShield is not affiliated with or endorsed by it. Third-party provider names and services belong to their respective owners. Mention of a provider does not imply endorsement, partnership, or affiliation.

NekoShield does not guarantee anonymity, complete ad blocking, or protection against every online threat. Results depend on the selected providers, active configuration, Android version, and supported features.

## Links

- [Nekomo organization](https://github.com/Nekomo-App)
- [NekoShield repository](https://github.com/Nekomo-App/NekoShield)
- [Rethink DNS (inspiration/reference)](https://github.com/celzero/rethink-app)

---

**NekoShield — DNS, VPN, proxy, and network tools in one place.**
