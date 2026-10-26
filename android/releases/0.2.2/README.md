# Link Workshop v0.2.2

Respects discovery authentication flags: code protection off selects the upstream default credential; enabled or unknown preserves the entered code. Known cloud/WAN mode requests LAN Only before authentication. MQTT code 5 is reported as not authorized, without assuming an incorrect password.

Install over v0.2.0/v0.2.1 normally; signing certificate unchanged. Run Check connection to see advertised LAN mode/code protection, then Connect. Discovery is queried even with a manual serial; the actual selected-IP serial takes priority, with a mismatch warning. A manual serial remains the fallback if UDP is blocked.

Source commit: 7d6c750e451a2b4b5ae2ea6246a2428b0b57daf0

43 JVM tests, clean build, lint (0 errors), and APK signature/manifest verification passed. Physical authentication/registration after the fix remains unverified. HTTP remains required by uploads. Development build; no printer access code or private signing key is included.
