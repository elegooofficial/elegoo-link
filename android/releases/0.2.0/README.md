# Link Workshop v0.2.0 development release

Source commit: 946b73d3a30fc3ff59a5632fb9080f1a0ba542a8

Added layered connection diagnosis (TCP 80/1883 and HTTP identity), explicit local Wi-Fi routing, service-owned background connection/upload, optional Android Keystore credentials, bounded reconnect without command replay, printer faults/firmware identity, CANVAS trays and confirmed automatic refill.

**Install:** uninstall v0.1.0 before installing this APK because its private debug signing key was lost and v0.2 uses a different certificate. This clears the old app's saved IP. The new development key has a private backup in the owner's Google Drive for subsequent compatible builds; it is excluded from the repository and source ZIP.

**Next on the phone:** enter the current printer IP and LAN access code, enable LAN Only, put the phone on the same Wi-Fi, then run **Check connection**. TCP failure precedes authentication; both ports reachable still requires testing MQTT login and client registration with Connect.

25 JVM tests passed; clean/final Gradle builds and lint passed (0 errors, 12 warnings). APK signature v2 and manifest verified. No emulator, S24+ or physical printer test was available, so the reported v0.1 IP failure is not yet confirmed resolved. Camera, start/resume, file browsing, discovery and separate completion alerts remain pending.

APK SHA-256: 44fe4663782c508f75a4d8933ac2bbeda9a96bf6d82df7e57c183d6041b5d65b
Source ZIP SHA-256: 170fe375f0bbe6181f3663231050ef9fb650f20b76e03144e831433c08639f85

Minimum Android 8/API 26; target Android 16/API 36. This is a debug-signed development APK.
