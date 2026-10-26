# Link Workshop v0.3.1 — home VPN remote access

Install Link-Workshop-v0.3.1.apk. This uses the same development signing certificate as v0.2.0–v0.3.0 and normally updates in place. v0.1.0 used a different, lost certificate.

New: explicit Local Wi-Fi / Ethernet or Remote through home VPN routing; remote MQTT/HTTP/camera; VPN loss checks and fresh-registration reconnect without changing-command/upload replay; remote camera diagnostics; selected-IP UDP identity; saved route preferences and in-app setup help. System/Light/Dark and prior files/controls remain included.

Your Pi must be configured separately. Follow [REMOTE_ACCESS.md](../../REMOTE_ACCESS.md) for a Tailscale single-printer /32 route, route approval and phone/port-specific access policy. Adding a narrow rule does not override broader existing permissions. Phone-to-Pi encryption is provided by the VPN; the Pi-to-printer LAN hop remains plaintext. App VPN checks are not a kernel kill switch: Android Always-on VPN / Block connections without VPN provides stronger enforcement. Do not publicly forward printer ports.

Keep the printer's home IP in the app, enable LAN Only and resolve local MQTT authorization first. Remote access does not correct the earlier code 5 refusal or enable a missing HTTP service. Uploads still need HTTP port 80. Native Android/Pi/CC2 remote acceptance remains pending.

73 JVM tests passed; lint has zero errors, 21 UI translation warnings and one test-only dependency update notice. Final APK manifest, ZIP, DEX and unchanged signing certificate verified. See [VALIDATION.md](../../VALIDATION.md).

The source ZIP includes build scripts/wrapper, code, tests, setup guide, licenses and a SOURCE.txt pin. No credentials or signing keys are included. SHA256SUMS covers both downloads.
