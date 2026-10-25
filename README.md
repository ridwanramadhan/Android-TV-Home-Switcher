# Android TV Home Switcher

Switch an Android TV or Google TV between its stock Home screen and **Aqua TV Home**, a glass-style launcher with an optional clock and information dashboard. The scripts install a supplied APK over ADB, keep the stock launcher installed, and save a local backup before the first switch.

![Aqua TV Home overview](docs/banner.png)

## Screenshots

|                            Stock Home                             |                               Fresh mode                                |                                              Widgets mode                                              |
| :---------------------------------------------------------------: | :---------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------------: |
| <img src="docs/mode-default.png" width="280" alt="Stock TV Home"> | <img src="docs/mode-fresh.png" width="280" alt="Aqua TV Home app grid"> | <img src="docs/mode-widgets.png" width="280" alt="Aqua TV Home prayer, weather and air quality cards"> |
|                         Original launcher                         |                            App grid and dock                            |                          Clock, prayer times, weather, air quality, and Adzan                          |

The TV launcher artwork used by Android is [a separate 320×180 banner](launcher/app/src/main/res/drawable-nodpi/banner.jpg).

## Requirements

- An Android TV or Google TV device running **Android 8.0 / API 26 or newer**. OEM behavior varies; a supported API level does not guarantee that a TV permits changing Home.
- A computer and TV on the same local network, with [Android platform-tools (adb)](https://developer.android.com/tools/releases/platform-tools) installed.
- Developer options and ADB/network debugging enabled on the TV. Accept the computer's authorization prompt on the TV.
- The included APKs in [apks/](apks/) or locally built replacements.

Changing the Home app affects the TV's user settings. Read [Backup and recovery](#backup-and-recovery) before switching, and keep ADB access available until Home works on your device.

## Quick start

1. On the TV, open **Settings → System → About**, select **Build** seven times, then enable **USB debugging** and **Network debugging / ADB debugging** under Developer options. Menu names vary by manufacturer. Find the TV's IP address in network settings.
2. From this repository, check the connection. Replace the example address with your TV's IP:

   ```bash
   ./switch.sh status 192.168.1.50
   ```

   On Windows PowerShell, use `.\switch.ps1 status 192.168.1.50`. Accept the ADB prompt on the TV if one appears.

3. Switch modes:

   | Action                | macOS / Linux                            | Windows PowerShell                        |
   | --------------------- | ---------------------------------------- | ----------------------------------------- |
   | Fresh launcher        | `./switch.sh glass 192.168.1.50`         | `.\switch.ps1 glass 192.168.1.50`         |
   | Launcher with widgets | `./switch.sh glass-widgets 192.168.1.50` | `.\switch.ps1 glass-widgets 192.168.1.50` |
   | Return to stock Home  | `./switch.sh default 192.168.1.50`       | `.\switch.ps1 default 192.168.1.50`       |

The first switch creates a baseline snapshot in `backups/` unless `--no-backup` (shell) or `-NoBackup` (PowerShell) is supplied. This directory is ignored by Git. You can copy [.env.example](.env.example) to `.env` and set `TV_HOST`, then omit the IP from later commands. Do not commit `.env` or a TV backup.

Use `./switch.sh list` or `./switch.sh --help` for shell aliases and options. In PowerShell, use `.\switch.ps1 -Help`.

## Modes and data

| Mode                        | APK                         | Features                                                       |
| --------------------------- | --------------------------- | -------------------------------------------------------------- |
| `glass` / `fresh`           | `apks/glass-tv-fresh.apk`   | Glass-style app grid and dock                                  |
| `glass-widgets` / `widgets` | `apks/glass-tv-widgets.apk` | Dashboard cards, clock screensaver, and scheduled Adzan player |
| `default` / `stock`         | None                        | Re-enables and selects the detected stock launcher             |

The widgets build reads the TV's location when available. Prayer times come from AlAdhan; weather and US AQI come from Open-Meteo. If location is unavailable, the code uses a fallback in East Jakarta. These cards require network access and may be empty or stale offline. Adzan playback depends on the TV's alarm, overlay, and activity behavior, so test it on your device before relying on it.

The launcher also provides a `ClockDream` screensaver service. Select **Aqua TV Home** in the TV's screensaver settings if your firmware exposes that option.

## Configuration and privacy

Copy `.env.example` to `.env` to save a TV address and optional overrides. The most useful settings are:

| Setting                           | Purpose                                                   |
| --------------------------------- | --------------------------------------------------------- |
| `TV_HOST`, `TV_PORT`              | TV network address; the example port is `5555`            |
| `ADB_PATH`                        | Explicit path to `adb` when it is not on `PATH`           |
| `STOCK_PACKAGE`, `STOCK_ACTIVITY` | Override automatic stock Home detection for an unusual TV |
| `FRESH_APK`, `WIDGETS_APK`        | Use alternate local APK files in the shell switcher       |

To change pinned dock apps, edit the `dock_apps` array in [config.xml](launcher/app/src/main/res/values/config.xml), then rebuild the APKs. To change the widget fallback location, edit `SAVED_LAT` and `SAVED_LON` in [DashboardRepository.java](launcher/app/src/main/java/com/gathin/tvhome/DashboardRepository.java).

The widgets build sends location coordinates to AlAdhan and Open-Meteo to obtain its cards. The switcher saves device properties, package lists, settings dumps, and a stock launcher APK under the local `backups/` directory. Treat those backups as private device data.

## Backup and recovery

`switch.sh` detects the stock Home, creates a backup on the computer, installs the chosen APK, selects it as Home, and may disable the stock launcher **for user 0**. It does not uninstall the stock launcher. [The architecture diagram](docs/architecture.svg) shows the switch and restore path.

![Computer, ADB, TV, and local backup architecture](docs/architecture.svg)

To return to the factory Home choice, run `./switch.sh default` or `.\switch.ps1 default`. To restore package state captured in a backup, run `./switch.sh restore` or `.\switch.ps1 restore`. The shell backup tool also writes a restore script inside each backup folder. Run `./switch.sh backup` to make another snapshot before experimenting.

Recovery depends on the stock package still being present, the backup being usable, and ADB remaining accessible. If Home will not open, use the TV's Settings app to re-enable the stock launcher or remove Aqua TV Home if the firmware permits it. Consult the manufacturer's recovery steps if those controls are unavailable.

## Troubleshooting

| Symptom                         | Check                                                                                                           |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| `adb` not found                 | Install Android platform-tools and ensure `adb` is on `PATH`, or set `ADB_PATH` in `.env`.                      |
| Connection refused or timed out | Confirm the TV IP, port, debugging setting, and that both devices are on the same network.                      |
| `unauthorized`                  | Accept the debugging authorization dialog on the TV, then rerun `status`.                                       |
| APK install failed              | Check TV storage and whether an installed build uses a different signing key. Uninstalling clears app settings. |
| HOME still opens the old screen | Check `status`, the default Home selection prompt, and OEM launcher restrictions.                               |
| Empty dashboard card            | Check internet and location access. The fallback location is in `DashboardRepository.java`.                     |

Preview a switch with `./switch.sh glass 192.168.1.50 --dry-run` or `.\switch.ps1 glass 192.168.1.50 -DryRun`. The scripts still need device access to detect the TV and its launcher.

## Build from source

The Android app uses Java 17, Android Gradle Plugin 8.5.2, Gradle wrapper 8.14, `compileSdk`/`targetSdk` 34, and `minSdk` 26. Install JDK 17 and Android SDK 34, then run:

```bash
./build.sh glass
./build.sh glass-widgets
# or ./build.sh all
```

`build.sh` updates `launcher/app/src/main/res/values/config.xml` to select a mode, builds the APK, and copies it to `apks/`. Review the working tree before committing after a build. To run JVM unit tests directly:

```bash
cd launcher
./gradlew :app:testDebugUnitTest
```

| Path                                                                                              | Purpose                                          |
| ------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| [switch.sh](switch.sh), [switch.ps1](switch.ps1)                                                  | Connect, install, select Home, and report status |
| [backup.sh](backup.sh), [restore.sh](restore.sh)                                                  | Snapshot and restore TV package state            |
| [MainActivity.java](launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java)               | Main launcher activity                           |
| [DashboardRepository.java](launcher/app/src/main/java/com/gathin/tvhome/DashboardRepository.java) | Widget data and fallback location                |
| [config.xml](launcher/app/src/main/res/values/config.xml)                                         | Build-time widget flag and default dock apps     |
| [AndroidManifest.xml](launcher/app/src/main/AndroidManifest.xml)                                  | Home, screensaver, services, and permissions     |

## Contributing and project policies

See [Contributing](CONTRIBUTING.md) for the development workflow and commit format, [Code of Conduct](CODE_OF_CONDUCT.md) for community expectations, and [Security](SECURITY.md) for private vulnerability reports.

## Credits

Android TV Home Switcher was built with reference to and inspiration from these open-source projects:

- [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) — Android TV cleanup and launcher switching workflow.
- [cocombee/glass-tv](https://github.com/cocombee/glass-tv) — the Glass TV launcher design and source foundation.

Thank you to their authors and contributors for making their work available. Please retain the applicable upstream attribution and license notices when redistributing derived work.

Project code and documentation are offered under the [MIT License](LICENSE). That license does **not** grant rights to third-party media, fonts, app icons, or trademarks. See [third-party notices](THIRD_PARTY_NOTICES.md) and the bundled [font license](launcher/FONT-LICENSE-OFL.txt).
