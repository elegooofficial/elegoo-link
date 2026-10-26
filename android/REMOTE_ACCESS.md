# Away-from-home access with your Pi

Link Workshop v0.3.4 retains a **Remote through home VPN** route. Your always-on Raspberry Pi can be the gateway: phone → encrypted Tailscale tunnel → Pi → printer at `192.168.1.84`. Authentication is separate: full-control LAN mode requires LAN Only; the experimental read-only PIN probe tests normal cloud mode with Matrix retained. See [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md). A Pi cannot make a firmware-disabled local path available. This is a user-managed VPN connection, not Elegoo cloud login. A commercial privacy VPN alone does not provide a route into your home.

The app is built and tested with simulated transports; the Pi has not been configured for you, and real CC2/S24+ remote operation remains unverified. First establish working local MQTT authentication and registration. Your earlier HTTP refusal and MQTT authorization error are independent of remote routing; a VPN cannot fix them.

## What makes this safer?

Another computer is not a security guarantee. Tailscale authenticates devices and encrypts the phone-to-Pi connection with WireGuard. Printer ports stay off the public internet. Access rules can restrict which enrolled phone reaches which printer ports. The Pi-to-printer hop still uses the app's plaintext LAN protocol; this is not encryption all the way into the printer.

The gateway and identity account become part of the trust boundary. Keep Pi/Tailscale updated, use MFA on the sign-in account, revoke lost devices and protect Pi administration. A compromised gateway, phone or account remains a risk. No printer credentials are sent to an app-operated relay, and this app adds no public listener to the Pi.

## Prepare the home gateway

1. Reserve the printer's current `192.168.1.84` address in the home router's DHCP settings. Keep the Pi on the same reachable home network; Ethernet is suitable. If the printer IP changes, update the route, access rules and app together.
2. Install Tailscale using its [Linux instructions](https://tailscale.com/docs/install/linux) and [Android instructions](https://tailscale.com/docs/install/android). Sign both devices into your own tailnet. A new Linux installation can be enrolled with `sudo tailscale up`; use the displayed sign-in flow. Do not put reusable auth keys in scripts or this repository.
3. On a Linux Pi with `/etc/sysctl.d`, enable IPv4 forwarding:

   ```sh
   printf '%s\n' 'net.ipv4.ip_forward = 1' | sudo tee /etc/sysctl.d/99-link-workshop-forwarding.conf
   sudo sysctl -p /etc/sysctl.d/99-link-workshop-forwarding.conf
   ```

   This creates a dedicated configuration file. Before enabling forwarding, check the Pi's firewall policy: unwanted forwarding should remain denied; permit the intended Tailscale-to-printer traffic according to the firewall in use. Do not flush firewall rules or broadly open forwarding. Existing custom firewalls can block the intended route.

4. Advertise only this printer:

   ```sh
   sudo tailscale set --advertise-routes=192.168.1.84/32
   ```

   This option replaces the advertised-route list. If the Pi already advertises routes for another purpose, preserve the required entries in the comma-separated list. Keep subnet SNAT enabled (the default); the printer then needs no return route to Tailscale addresses. No exit node or router port forwarding is required.
5. In the Tailscale admin console's Machines page, open the Pi and approve `192.168.1.84/32` under subnet routes. Route approval and access permission are separate settings.

## Restrict access permission

In the Tailscale Machines page, find **your phone's Tailscale IPv4**, not its home Wi-Fi IP. For a fresh, dedicated tailnet, the following is an example complete access policy. Replace `100.101.102.103` with that phone's address before saving. The example also allows read-only UDP identity lookup.

```json
{
  "acls": [],
  "grants": [
    {
      "src": ["100.101.102.103"],
      "dst": ["192.168.1.84/32"],
      "ip": ["tcp:1883", "tcp:80", "tcp:8080", "udp:52700"]
    }
  ]
}
```

Review and save this in the policy editor; it is not applied by the app. Default tailnets can permit broad device access. ACLs and grants combine permissions: adding this narrow grant does not cancel an existing allow-all rule. For an existing tailnet, adapt its policy and retain separately needed permissions instead of replacing it wholesale. Check the editor's validation/tests before saving. An explicit empty `acls` array avoids relying on an omitted ACL section; broader grants must also be removed or narrowed for the restriction to be effective. Re-enrolling a phone may require updating its source address.

| Port | App use | Can omit? |
| --- | --- | --- |
| TCP 1883 | MQTT monitoring, files queries and controls | Required for the printer session |
| TCP 80 | HTTP identity fallback, uploads and downloads | Yes, if uploads/downloads are unnecessary and another identity path works |
| TCP 8080 | Usual MJPEG camera endpoint | Yes, if camera is unnecessary; an alternate reported camera port needs its own rule |
| UDP 52700 | Selected-IP read-only identity/authentication flags | Yes, with manual serial fallback; discovery still times out before fallback |

The `/32` route targets one host. The policy limits that enrolled phone's access through Tailscale; it does not isolate the printer from other devices already on your home LAN.

## Connect from Android

1. Install v0.3.4 over the earlier v0.2/v0.3 build. In Tailscale, connect to your tailnet. Android accepts approved subnet routes automatically. Ensure Link Workshop is not excluded by Tailscale's app split-tunneling settings.
2. In Android VPN settings, enable **Always-on VPN** and **Block connections without VPN** when available. Blocking applies to the phone's networking beyond this app; review that effect. These system controls provide stronger enforcement during route changes than the app's checks alone. Only one VPN can run per Android user/profile at a time.
3. In Link Workshop Settings, choose **Remote through home VPN**. Enter the **printer's home IP** `192.168.1.84`, not the Pi's `100.x` VPN address. Select the same authentication that already worked at home: LAN access code with LAN Only, or the explicit read-only cloud-mode PIN probe with cloud mode retained. Enter the corresponding credential in its separate field. If discovery fails, enter the exact Serial Number from printer Settings → Device. Save a profile if useful; its route preference is retained.
4. Run **Check connection**, then **Connect**. Once the gateway is configured, test on cellular with home Wi-Fi off. Compare status and camera against the printer before relying on remote controls.

Remote mode uses Android's default VPN-aware route for MQTT, HTTP and camera, rather than binding sockets to underlying Wi-Fi. It requires an active VPN for this app and refuses process-bound routing. New TCP connections and UDP sends recheck the captured VPN. VPN loss/replacement closes the session, cancels work and stops camera playback when detected; LAN authentication uses bounded reconnect with fresh registration; the PIN probe stops without automatic retries. Changing commands and uploads are never replayed. Local mode retains explicit Wi-Fi/Ethernet routing.

VPN presence does not prove that the VPN reaches your Pi, advertises this IP, has the intended access policy, or uses a particular encryption configuration. App checks/callbacks are not a kernel kill switch: route changes can race connection setup. Use Android's blocking setting for enforcement. Broadcast printer scanning is disabled remotely; selected-IP UDP discovery can work through the approved route.

## Troubleshooting and limits

- **VPN unavailable:** connect Tailscale and include this app in its VPN. Disconnect other VPN apps. If the VPN changes, reconnect after the app stops its old session.
- **All printer ports unreachable:** check Pi power/internet, route approval, source-phone policy, forwarding/firewall and the printer's current reserved IP. A working Pi VPN address alone does not establish a working printer route.
- **MQTT reachable, code 5:** resolve the same selected-authentication issue as at home; route access does not authenticate the client. LAN protection off uses the upstream default only when discovery explicitly reports that setting. The explicit PIN probe has no default/fallback and stops if refused; keep Matrix/cloud mode enabled for that experiment.
- **MQTT works, HTTP refused:** LAN monitoring/controls can work with identity available; the PIN probe still permits reads only. Uploads remain unavailable; the Pi does not create a missing printer HTTP service.
- **Camera only fails:** verify the reported stream URL and allowed port. Bandwidth, latency and firmware stream support can limit playback.
- **Remote Wi-Fi uses the same address range:** overlapping private networks can cause routing conflicts. Test on cellular; do not broaden exposure to solve a conflict.
- **Pi or VPN outage / phone process death:** remote monitoring and alerts stop. This is not an emergency-stop channel; commands depend on fresh status and network delivery.

Do not publicly forward printer ports 80, 1883 or 8080. For rollback, choose Local Wi-Fi / Ethernet in the app and use home Wi-Fi. If retiring the Pi gateway, remove only this advertised route (preserve others), revoke its approval and remove its printer grant. Review the dedicated forwarding file before deleting it; other Pi services may depend on forwarding.

## Primary references

- [Tailscale subnet routers](https://tailscale.com/docs/features/subnet-routers): forwarding, route approval, Android route acceptance and SNAT.
- [Tailscale security](https://tailscale.com/security): WireGuard and identity controls.
- [Tailscale ACLs](https://tailscale.com/docs/features/access-control/acls), [grants](https://tailscale.com/docs/features/access-control/grants) and [grants syntax](https://tailscale.com/docs/reference/syntax/grants): additive permissions, source/destination selectors and protocol/port rules.
- [Android VPN](https://developer.android.com/develop/connectivity/vpn): default routing, per-app inclusion and block-without-VPN.
- [Tailscale Android split tunneling](https://tailscale.com/docs/features/client/android-app-split-tunneling): app exclusion behavior.

Checked 2026-10-06. This guide assumes Linux on the Pi; exact firewall administration depends on its OS and existing configuration.
