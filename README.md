# NekoShield

> **Coming Soon · In Development**

NekoShield is a planned DNS management and privacy app from **Nekomo**, built for Android phones, tablets, and Android TV.

The goal is to make DNS setup easier across mobile and TV devices, with a simple interface for choosing providers, managing custom DNS settings, and checking connectivity.

**Project status:** Coming soon — currently in development. Features and platform compatibility are subject to change. No public release is available yet.

## Planned Features

- **DNS provider presets** — quickly choose from supported DNS services.
- **Custom DNS** — add and manage custom DNS configurations.
- **Encrypted DNS** — investigate support for DNS-over-HTTPS (DoH) and DNS-over-TLS (DoT), subject to Android and provider capabilities.
- **Filtering options** — use compatible DNS services for domain, ad, and tracker filtering.
- **Allowlist and blocklist management** — manage exceptions where supported by the selected service.
- **Connection diagnostics** — test DNS resolution and identify configuration problems.
- **Quick switching** — make it easier to change between saved configurations.
- **Android TV support** — layouts and controls designed for TV screens and remote navigation.
- **Mobile support** — an accessible interface for Android phones and tablets.

## Planned Platforms

| Platform | Status |
| --- | --- |
| Android phones | Planned |
| Android tablets | Planned |
| Android TV / Google TV | Planned |

Compatibility with specific Android versions and devices will be documented after implementation and testing.

## DNS Providers

Potential provider presets to investigate include:

- AdGuard DNS
- Cloudflare DNS
- Quad9
- NextDNS

Provider support, endpoints, and configuration requirements must be verified before being included. Users should be able to enter custom settings where technically supported.

## Privacy & Security

NekoShield is intended to make DNS configuration transparent and understandable.

- Explain what each setting does and what permissions are required.
- Clearly identify when DNS requests are handled by a third-party provider.
- Avoid collecting unnecessary personal information.
- Never commit API keys, credentials, tokens, or private configuration values.
- Document limitations and avoid promising complete anonymity or protection.

DNS providers may be able to observe the domains devices resolve. Review a provider's privacy policy before using its service.

## Android Implementation Notes

Android's system Private DNS, app-level DNS configuration, and VPN-based filtering have different capabilities and limitations. NekoShield will use supported Android APIs and clearly explain any required user action or limitation.

The app must not claim to provide system-wide DNS filtering, encrypted DNS, or ad blocking unless the active configuration genuinely supports it.

## Development Roadmap

See [ROADMAP.md](ROADMAP.md) for planned milestones.

- [ ] Finalize architecture and supported Android versions
- [ ] Build the Android mobile interface
- [ ] Build Android TV layouts and remote navigation
- [ ] Implement and test DNS configuration
- [ ] Add verified provider presets and custom DNS
- [ ] Add diagnostics and supported filtering controls
- [ ] Complete privacy, security, and compatibility testing
- [ ] Prepare the first public release

## Disclaimer

NekoShield is an independent project within the Nekomo organization. Third-party provider names and services belong to their respective owners. Mention of a provider does not imply endorsement, partnership, or affiliation.

NekoShield does not guarantee anonymity, complete ad blocking, or protection against every online threat. Results depend on the selected provider, device configuration, and supported features.

## Links

- [Nekomo organization](https://github.com/Nekomo-App)
- [NekoShield repository](https://github.com/Nekomo-App/NekoShield)

---

**NekoShield — DNS management, made simpler.**
