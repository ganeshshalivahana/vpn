# MyVPN (Android, WireGuard)

A real WireGuard VPN client (official `wireguard-android` tunnel library) with a multi-server, country-grouped list.

## Build the APK on GitHub
1. Create a GitHub repo and upload everything in this folder (keep `.github/workflows/android-apk.yml`).
2. Actions tab → "Build & Release Android APK" runs automatically (~3–5 min).
3. On your phone open the repo's **Releases**, download `MyVPN-debug.apk`, install it.
   (A debug-signed APK; Android will ask you to allow installs from your browser.)

## Adding servers (this is where "world VPNs" come from)
The app contains no servers; you supply WireGuard `.conf` files:
- Your own VPS (PiVPN, wg-easy, Algo, etc.)
- A provider that offers WireGuard config downloads (Mullvad, ProtonVPN, IVPN, Windscribe, ...)

In the app: **Import .conf files** (multi-select) or **Paste config**.
Name files with a country prefix — `us-nyc-01.conf`, `de-berlin.conf`, `jp_tokyo.conf` — and they are
grouped by country with flags. Tap a server, then the big button. Tapping another server while
connected switches live.

Configs contain private keys; they are stored in encrypted preferences on the device.
