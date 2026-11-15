# NekoShield Roadmap

> **Status: Early build — DNS protection working on Android + Android TV**

This roadmap tracks work items. Checked items are implemented and device-tested; unchecked items are still planned.

## 1. Planning & Architecture
- [x] Define minimum and target Android versions (minSdk 24, targetSdk 36)
- [x] Choose the app architecture and technology stack (Kotlin, classic Views, DNS66-style `VpnService`)
- [x] Research Android Private DNS, DoH, DoT, VPN, proxy, and filtering capabilities
- [x] Document Android VPN-service conflicts and platform limitations
- [x] Define permission, privacy, and security requirements

## 2. DNS Management
- [x] Build DNS provider selection and configuration
- [x] Add custom DNS profiles (UDP / DoT / DoH)
- [x] Investigate and implement supported DoH and DoT options (persistent `SSLSocket` + OkHttp)
- [x] Add supported domain filtering and allowlist/blocklist controls
- [x] Add DNS resolution and connectivity diagnostics
- [ ] Per-provider latency display and automatic fastest pick
- [ ] Negative-caching / response TTL handling

## 3. VPN & Network Protection
- [x] Research Android VPN service implementation and constraints
- [x] Design transparent VPN connection controls (start/stop, status, notifications)
- [x] Investigate per-app routing and filtering where supported (per-app DNS bypass)
- [x] Add connection status and troubleshooting
- [x] Test coexistence limitations with other VPN applications (one-VPN-at-a-time verified)
- [ ] Optional always-on guidance and boot recovery edge cases

## 4. Proxy Management
- [ ] Research supported HTTP(S) and SOCKS proxy configurations
- [ ] Add saved proxy profiles
- [ ] Investigate per-app proxy routing where supported
- [ ] Add connection testing and clear status indicators

## 5. Android Mobile & TV
- [x] Create the Android mobile interface
- [x] Create TV-friendly layouts
- [x] Support D-pad and remote-control navigation
- [x] Verify focus states, readability, and accessibility (violet focus rings)
- [x] Test on Android phones, tablets, Android TV, and Google TV (verified on Pixel-class phone + Android TV box)
- [ ] Tablet-specific two-pane layout

## 6. Release Preparation
- [x] Complete privacy and security review (on-device only, no analytics)
- [x] Test supported Android versions and device types
- [x] Document feature limitations and provider requirements
- [ ] Prepare release builds and installation instructions
- [ ] Publish the first stable release

Suggestions and bug reports can be submitted through GitHub Issues.
