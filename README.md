<p align="center">
  <img src="docs/assets/icon.png" width="104" alt="ZedTunnel">
</p>

<h1 align="center">ZedTunnel</h1>

<p align="center">A personal SSH + OpenVPN + OpenConnect client, trimmed down from ZedSecure.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-C7F24E?style=flat-square&labelColor=15170B" alt="Android">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-AGPL--3.0-C7F24E?style=flat-square&labelColor=15170B" alt="AGPL-3.0"></a>
</p>

## What is ZedTunnel?

ZedTunnel is a private fork of [ZedSecure](https://github.com/CluvexStudio/ZedSecure), built for one
person's own day-to-day use rather than general release. It keeps the three connection methods that
are actually used — **SSH with a custom payload injector**, **OpenVPN** (`.ovpn` import) and
**OpenConnect** — and drops the rest of the interface: no subscriptions, vault, map, AI assistant,
Tor, or extra protocol screens. Android only; the Linux/Windows/macOS desktop targets are untouched
by this fork and are not built or tested here.

> This is not a general-purpose release. It carries the parent project's AGPL-3.0 licence and
> credits below, and anyone is free to use the source the same way — but the choices on this page
> (what's kept, what's hidden, the app ID) were made for one person's setup, not for a wide audience.

## What's kept

| | |
|---|---|
| **SSH** | Password or key auth, host-key pinning (TOFU with a saved fingerprint), and a custom payload engine: send a hand-written HTTP-style request before the SSH handshake, optionally through an HTTP proxy and/or wrapped in TLS with a chosen SNI. Placeholders: `[host]` `[port]` `[host_port]` `[method]` `[protocol]` `[crlf]` `[cr]` `[lf]` `[split]` `[split=ms]`. |
| **OpenVPN** | `.ovpn` import, carried by the bundled sing-box engine as before. |
| **OpenConnect** | Cisco AnyConnect and compatible gateways. |
| **VPN basics** | Per-app routing, Bypass LAN, kill switch, DNS and MTU settings, auto-reconnect, network-change handling, foreground service, live logs, settings export/import. |

## What's new in this fork

- **Payload injection for SSH** — direct-to-host or through-a-proxy, with optional TLS+SNI, a
  `[split]` tag for timed, multi-part sends, configurable handling of the server's HTTP reply, and
  host-key pinning. This did not exist in upstream ZedSecure's SSH mode.
- **Data usage tab** — daily, weekly and monthly charts of tunnel traffic, kept on the device only,
  in the same Material 3 style as the rest of the app.

## What's hidden or removed

Removed from the UI (the add-server menu, tabs and settings): Xray/VLESS/VMess/Trojan/Shadowsocks/
Hysteria, WireGuard/AmneziaWG, Tor, Psiphon, IKEv2, DNS tunnels (DNSTT/VayDNS/MasterDNS), SNI spoof,
subscriptions, the config vault, the server map, the AI assistant, easter eggs, and the
support/update/rate/about screens.

Most of this is a UI change, not a binary one — the shared multi-protocol engine
([ZedSecure-Cores](https://github.com/CluvexStudio/ZedSecure-Cores)) is still compiled into the app
the same way it is upstream, just unreachable from the interface. The one part that is actually
**not shipped** any more is Tor and the SNI-spoof engine: their native libraries and Tor's bundled
GeoIP data are left out of the Android build, which is roughly 24 MB off the APK.

## Building

Package ID: `com.shifat.zedtunnel`. Signing key and CI are set up as a private GitHub Actions
workflow in this repository (`android-release.yml`); there is no public release channel.

```sh
./tools/get-cores.sh android
./gradlew :app:assembleRelease
```

## Credits

ZedTunnel is a fork of [ZedSecure](https://github.com/CluvexStudio/ZedSecure) by CluvexStudio, and
keeps its full dependency list even where this fork's UI does not expose every engine, since the
same shared binaries are still built in: [Xray-core](https://github.com/XTLS/Xray-core),
[sing-box](https://github.com/SagerNet/sing-box), [Psiphon](https://github.com/Psiphon-Labs/psiphon-tunnel-core),
[MasterDnsVPN](https://github.com/masterking32/MasterDnsVPN), [AmneziaWG](https://github.com/amnezia-vpn/amneziawg-go),
[OpenConnect](https://www.infradead.org/openconnect/), [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel),
[zeptun](https://github.com/Noisemux/zeptun), [JSch (mwiede fork)](https://github.com/mwiede/jsch)
for the SSH layer, and the DNS-tunnel credits in upstream's README (SlipNet, VayDNS, dnstt) for code
that may still be present in the shared engine binary even though this fork's UI does not use it.
Tor and SNI-spoof are the one pair of engines actually removed from this build, so Tor's own credit
line is dropped here.

## License

[AGPL-3.0](LICENSE), inherited from upstream ZedSecure. The shared engine also links sing-box,
Psiphon and sing-openvpn, which are GPL-3.0; section 13 of GPL-3.0 allows combining them with
AGPL-3.0 code, and each keeps its own licence. See [NOTICE](NOTICE).
