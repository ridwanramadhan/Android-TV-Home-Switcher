# Security policy

## Supported versions

Security fixes are considered for the current source on the default branch. Bundled APKs can lag behind source; compare their release details before installing them.

## Report a vulnerability

Please **do not open a public issue** for an exploitable vulnerability, exposed secret, or unsafe ADB/backup behavior. Use GitHub's private vulnerability reporting for this repository when available, or email **echo.ridwanramadhan@gmail.com** with the subject `Android TV Home Switcher security report`.

Include the affected script or app version, device/API level if relevant, reproduction steps, impact, and a suggested fix if you have one. Redact credentials, ADB keys, TV addresses, backup contents, and precise location data. The maintainer will acknowledge the report and coordinate a fix and disclosure timeline privately. No fixed response deadline is promised.

## Scope and safe testing

Please test only on devices you own or are authorized to manage. Avoid disrupting other users or publishing working exploits before a fix is available. ADB/network debugging exposes powerful device controls; turn it off when you no longer need it and protect the computer-side `backups/` directory.
