# Store listing — Ultimate Guard

Copy-paste values for the Orion Store "Submit an app" form (and any other store).

## 1 · Source

| Field | Value |
|---|---|
| Repo URL | https://github.com/technicalnihilist-lgtm/ultimate-guard |

## 2 · Details

| Field | Value |
|---|---|
| Name | Ultimate Guard |
| Category | Utility |
| Author | technicalnihilist-lgtm |
| App ID | com.ultimateguard |

## 3 · Store listing

| Field | Value |
|---|---|
| Icon URL | https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/icon.png |
| Description | see below |

### Description

Ultimate Guard is an offline DNS filter for Android. Blocked domains are answered
locally, so ads, trackers and blocked sites never resolve — the request dies on your
phone instead of reaching a server. No account, no sign-up, no server, no telemetry.

WHAT IT DOES
• Real DNS filtering: a local VPN tunnel reads every lookup, blocks the names on your
  lists and forwards the rest to the resolver you pick (Cloudflare, Google, Quad9 or
  your own).
• 957 domains bundled: 711 ad networks, 162 trackers and analytics services, 107 adult
  sites. Each list can be switched on or off.
• Your own rules: add domains by hand, keep an allowlist for things you never want
  blocked, or import any hosts-file / Adblock-style text file.
• Catches apps that hardcode 8.8.8.8: public resolver addresses are routed into the
  filter, so skipping the system DNS does not skip the block.
• Optional app blocking (Device Owner): suspend chosen apps so they cannot be opened,
  re-applied after every reboot and reinstall.
• PIN lock: the whole app sits behind a salted PIN, so the block settings cannot be
  quietly changed.
• Live statistics and an activity log of recent lookups.

WHAT IT DOES NOT DO
• It is a DNS filter, not a firewall. An app using its own encrypted DNS (DoH/DoT) to a
  server this app does not intercept can still resolve names.
• Ads served from the same domain as the page, or baked into an app, are not blocked at
  this level.
• Only one VPN can run at a time; another VPN app will take the tunnel.
• Nothing here survives a factory reset, and someone with ADB or recovery access can
  bypass it. This is a self-control and family-safety tool, not tamper-proof lockdown.

Built with the Android framework only — no third-party libraries, no accounts, no
telemetry. minSdk 26, targetSdk 35. Open source on GitHub.

## 4 · Release

| Field | Value |
|---|---|
| Package name | com.ultimateguard |
| Release keyword | leave on Auto (first APK in the release) — release v1.0.0 has exactly one APK |

## 5 · Screenshots (direct image links)

- https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/screenshots/status.png
- https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/screenshots/apps.png
- https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/screenshots/domains.png
- https://raw.githubusercontent.com/technicalnihilist-lgtm/ultimate-guard/main/docs/screenshots/settings.png

The same four links are in the README, so the store can also pick them up from there.

## Downloads

- Release: https://github.com/technicalnihilist-lgtm/ultimate-guard/releases/latest
- Direct APK: https://github.com/technicalnihilist-lgtm/ultimate-guard/releases/download/v1.0.0/UltimateGuard-1.0.0.apk
- SHA-256: cdc5f389cd4d1c4346955f2433ccdde27e40b7ca0d95c40439d7b40503146b5c
