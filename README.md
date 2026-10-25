# 📺 Android TV Home Switcher (Glass TV Edition)

Transform your Android TV or Google TV into a **clean, fast, ad-free Apple TV-style Glass Home Screen** with a single command from your computer.

Powered by [cocombee/glass-tv](https://github.com/cocombee/glass-tv) with **Apple Liquid Glass** design, live widgets, and full reversible safety backups.

Works with **any** Android TV or Google TV brand (Sony, TCL, Xiaomi, Chromecast with Google TV, AQUA, Hisense, Philips, etc.).

| 📺 Original TV Home (Stock) | 💎 Glass Fresh Mode (Apple TV Grid) | 🕌 Glass Widgets Mode (Clock, Prayer, Weather & AQI) |
| :---: | :---: | :---: |
| <img src="docs/mode-default.png" width="280" alt="Default TV launcher"> | <img src="docs/mode-fresh.png" width="280" alt="Fresh theme"> | <img src="docs/mode-widgets.png" width="280" alt="Widgets theme"> |
| Your TV's original factory screen | Clean 5-slot dock + Liquid Glass selection (zero ads) | Beautiful digital clock + 3 Liquid Glass widgets + Adzan sound |

---

## ✨ Features

* **💎 Apple Liquid Glass Selection & Panels:** Gliding selection and informational cards feature translucent frosted glass, specular diagonal shine highlights catching top-left and bottom-right edges (`GlassRim`), and soft ambient app glow.
* **✌️ Two Distinct Styles:**
  * **Fresh Style (`glass` / `fresh`):** Super-clean minimalist Apple TV-style dock and app grid without widgets.
  * **Widgets Style (`glass-widgets` / `widgets`):** Features a beautiful digital clock + 3 live Liquid Glass cards:
    1. **Live Islamic Prayer Times:** 7 prayer slots (Imsak, Subuh, Syuruq, Dzuhur, Ashar, Maghrib, Isya) with next prayer countdown pill.
    2. **Real-time Weather:** Current temperature, weather condition symbol, high/low, and 5-hour micro forecast bar.
    3. **Air Quality Index (AQI):** Radial ring meter with US AQI color codes (Good, Moderate, Sensitive, Unhealthy) and status rating.
* **🌌 Ambient Liquid Glass Screensaver:**
  * Displays the large clock, date, and **all 3 Liquid Glass widgets (Prayer, Weather, AQI)** right on your TV screensaver.
  * Features smart **OLED burn-in protection** with subtle micro-drifting pixel shifts every minute.
* **🕌 1080p Full HD Dedicated Adzan Videos:**
  * **🌅 Adzan Subuh (Fajr):** Automatically plays the official **1080p Full HD [Riko The Series - Adzan Subuh](https://www.youtube.com/watch?v=p6zIq6J0rSk)** (`res/raw/adzan_subuh.mp4`) featuring the morning prayer call (*Ash-shalatu khairum minan naum*).
  * **☀️ Dzuhur, Ashar, Maghrib & Isya:** Automatically plays the official **1080p Full HD [Upin & Ipin Iqra' - Laungan Azan](https://www.youtube.com/watch?v=UPAnvq9mAc0)** (`res/raw/adzan.mp4`).
  * **Active Everywhere:** Triggers on the home screen, during the screensaver, and **even while watching movies on Netflix, Disney+, YouTube, or Prime Video**.
  * **Smart Movie Resume:** Automatically pauses Netflix/Disney+ for the Adzan call. When the Adzan finishes or when you press **Back** on your remote, it smoothly returns right to your movie where you left off.
* **🏷️ Minimalist Glass TV Watermark:** Subtle, non-intrusive watermark attribution to [cocombee/glass-tv](https://github.com/cocombee/glass-tv) on the home screen.
* **⚡ Ultra-lightweight & Snappy:** Instant boot, zero lag, hardware-accelerated MediaCodec video decoding, and minimal RAM footprint on low-spec TVs.

---

## 🛡️ Is it safe? Will it break my TV?

**It is 100% safe, non-destructive, and completely reversible:**

* **Zero Risk of "Bricking":** It **never deletes or uninstalls** your TV's system software. 
* **Automatic Safety Backup:** The very first time you switch your TV, the tool **automatically takes a full safety backup** of your TV's original settings and apps before touching anything.
* **Instant 1-Second Undo:** If you ever want your original factory screen back, simply run `./switch.sh default` and your TV returns to normal immediately.
* **Emergency TV Remote Escape:** Even without a computer, you can always revert to your factory screen right from your TV remote: go to **Settings ⚙️ $\rightarrow$ Apps $\rightarrow$ Aqua TV Home $\rightarrow$ Uninstall**.

---

## 🚀 3-Step Setup Guide

### Step 1: Prepare your TV (1 minute)
1. On your TV, open **Settings ⚙️ $\rightarrow$ System $\rightarrow$ About** (on older TVs: **Settings $\rightarrow$ Device Preferences $\rightarrow$ About**).
2. Scroll down to **Build** (or *Android TV OS build*) and **click it 7 times** with your remote until you see the message: *"You are now a developer!"*.
3. Press Back once, open the new **Developer options** menu, and turn on:
   * **USB debugging** $\rightarrow$ **ON**
   * **Network debugging** (or *ADB debugging*) $\rightarrow$ **ON**
4. Check your TV's IP address: go to **Settings $\rightarrow$ Network & Internet** and click your Wi-Fi or Ethernet. (For example: `192.168.1.50`).

---

### Step 2: Connect from your Computer (One-time)
Make sure your computer and your TV are connected to the **same Wi-Fi or home network**.

Open your terminal on your computer (**PowerShell** on Windows, or **Terminal** on Mac/Linux), navigate to this folder, and run:

* **On Windows (PowerShell or CMD):**
  ```powershell
  .\switch.ps1 status 192.168.1.50
  # Or simply: switch status 192.168.1.50
  ```
* **On Mac / Linux:**
  ```bash
  ./switch.sh status 192.168.1.50
  ```
*(Replace `192.168.1.50` with your actual TV IP address).*

> 💡 **Look at your TV screen!**  
> A popup will appear saying **"Allow USB debugging?"**.  
> Use your remote to check the box **"Always allow from this computer"** and click **OK**.

---

### Step 3: Choose your Home Screen!
Whenever you want to switch your TV, run the command for your system:

#### 💻 Windows (PowerShell or Command Prompt)
```powershell
# 💎 Switch to Glass TV Fresh mode (Apple TV grid, liquid glass, no widgets):
.\switch.ps1 glass
# (or: .\switch.ps1 fresh)

# 🕌 Switch to Glass TV Widgets mode (Digital Clock + Prayer, Weather, AQI & Adzan):
.\switch.ps1 glass-widgets
# (or: .\switch.ps1 widgets)

# 📺 Revert back to your TV's original factory home screen anytime:
.\switch.ps1 default
```

#### 🍎 Mac / Linux (Terminal)
```bash
# 💎 Switch to Glass TV Fresh mode (Apple TV grid, liquid glass, no widgets):
./switch.sh glass
# (or: ./switch.sh fresh)

# 🕌 Switch to Glass TV Widgets mode (Digital Clock + Prayer, Weather, AQI & Adzan):
./switch.sh glass-widgets
# (or: ./switch.sh widgets)

# 📺 Revert back to your TV's original factory home screen anytime:
./switch.sh default
```

*(You can also pass your TV's IP directly, e.g.: `.\switch.ps1 fresh 192.168.1.50` or `./switch.sh fresh 192.168.1.50`)*

---

## 🎮 How your Remote Control works

* **Clicking HOME 🏠 on the remote:** Opens your selected clean home screen (Aqua TV Home).
* **Opening an app & clicking BACK ⬅️:** Smoothly takes you right back to your clean home screen.
* **Switching back to default:** Run `./switch.sh default`, and the remote's HOME button returns to your factory home screen.

---

## 🛟 Safety & Disaster Recovery (How to Restore)

You have multiple safety nets in place at all times:

### 1. The Normal Revert (Recommended)
To go back to your original factory screen:
* **Windows:** `.\switch.ps1 default`
* **Mac / Linux:** `./switch.sh default`

### 2. Full System Restore from Backup
If you ever want to re-enable all original packages and restore exact factory states:
* **Windows:** `.\switch.ps1 restore`
* **Mac / Linux:** `./switch.sh restore`

### 3. Emergency Restore from TV (No computer needed)
If your computer is off or you gave the TV to someone else, simply grab your TV remote:
1. Open **Settings ⚙️ $\rightarrow$ Apps $\rightarrow$ See all apps**.
2. Find **Aqua TV Home** and select **Uninstall**.
3. Your TV will immediately and automatically revert to its factory stock home screen!

---

## ❓ Frequently Asked Questions & Troubleshooting

### Q: It says `Could not connect` or `Connection refused`.
* **Fix:** Make sure **Network debugging** (or USB debugging) is turned **ON** in TV Developer options. If your TV went to sleep or the router gave it a new IP address, check the IP on the TV and try again.

### Q: It says `unauthorized`.
* **Fix:** Look at your TV screen and accept the **"Allow USB debugging?"** prompt. Tick *"Always allow from this computer"* so it doesn't ask again.

### Q: It says `adb was not found`.
* **Fix:** You need Android platform-tools installed on your computer:
  * **On Windows:** Open PowerShell and run:
    ```powershell
    winget install Google.PlatformTools
    ```
    *(Or download the official zip from [Google Android Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and extract it).*
  * **On Mac:** Open Terminal and run:
    ```bash
    brew install android-platform-tools
    ```

### Q: On Google TV, the remote asks "Complete action using..."
* **Fix:** On newer Google TV sets (Chromecast, Sony, TCL), Android may ask you which launcher to use when you press HOME for the first time. Simply highlight **Aqua TV Home** and click **Always**.

### Q: Where do the Prayer Times & Weather come from?
* **Widgets Mode** automatically gets your location from the TV to show local prayer times (AlAdhan / KEMENAG) and weather / Air Quality (Open-Meteo). If your TV has no location fix, it defaults to Jakarta coordinates.

### Q: How do I enable the Widgets Screensaver?
* On your TV, open **Settings ⚙️ $\rightarrow$ System $\rightarrow$ Screensaver** (or *Device Preferences $\rightarrow$ Screensaver*) and select **Aqua TV Home** (or run `adb shell settings put secure screensaver_components com.gathin.tvhome/.ClockDream`).
* When the TV is idle, it will display the ambient digital clock + live **Prayer countdown, Weather forecast, and AQI gauge** with automatic micro-drifting OLED burn-in protection.

### Q: How does Adzan work while watching movies (Netflix, Disney+, YouTube)?
* When a valid prayer time arrives (Subuh, Dzuhur, Ashar, Maghrib, Isya), the built-in exact alarm scheduler triggers a high-priority full-screen Adzan player.
* Your movie is smoothly paused while the **1080p Full HD Upin & Ipin Adzan** plays with audio.
* Pressing **Back** or **OK** on your remote (or waiting for the 3-minute video to complete) dismisses the Adzan and takes you **directly back to your movie where you left off**.

### Q: What are the Adzan video resolutions and sources?
* Both videos are encoded in crisp **1080p Full HD (1920×1080)** with hardware-accelerated H.264 AVC and AAC audio:
  * **Adzan Subuh:** [Riko The Series - Adzan Subuh](https://www.youtube.com/watch?v=p6zIq6J0rSk) (includes *Ash-shalatu khairum minan naum*).
  * **Dzuhur, Ashar, Maghrib, Isya:** [Upin & Ipin Iqra' - Laungan Azan](https://www.youtube.com/watch?v=UPAnvq9mAc0).

---

<details>
<summary><b>🛠️ Advanced & Developer Guide (Click to expand)</b></summary>

### Customizing Pinned Dock Apps & Settings
The full Java and XML source code for the launcher is included in the [`launcher/`](launcher/) folder:

1. **Change Pinned Dock Apps:**
   Edit [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
   ```xml
   <string-array name="dock_apps">
       <item>com.google.android.youtube.tv</item>
       <item>com.netflix.ninja</item>
       <item>com.disney.disneyplus</item>
       <item>com.vidio.android.tv</item>
       <item>com.mediatek.wwtv.tvcenter</item>
   </string-array>
   ```
2. **Change Fallback City Coordinates:**
   Edit `SAVED_LAT` and `SAVED_LON` in [`launcher/app/src/main/java/com/gathin/tvhome/DashboardRepository.java`](launcher/app/src/main/java/com/gathin/tvhome/DashboardRepository.java).
3. **Rebuild APKs from Source:**
   ```bash
   ./build.sh glass           # Recompiles clean Glass TV grid build
   ./build.sh glass-widgets   # Recompiles Glass TV widgets build (+ Adzan)
   ./build.sh all             # Recompiles both variants
   ```

### Android TV OS Version Compatibility

| Android Version | API Level | Stock Launcher | Compatibility |
| :--- | :--- | :--- | :--- |
| **Android 8.0 & 8.1 (Oreo)** | 26 – 27 | `com.google.android.tvlauncher` | ✅ Full support (legacy `pm set-home-activity` fallback) |
| **Android 9.0 (Pie)** | 28 | `com.google.android.tvlauncher` | ✅ Full support |
| **Android 10 (Q)** | 29 | `com.google.android.tvlauncher` | ✅ Full support |
| **Android 11 (R)** | 30 | `com.google.android.tvlauncher` / `launcherx` | ✅ Full support (verified on MediaTek/Haier/AQUA) |
| **Android 12 & 12L (S)** | 31 – 32 | `com.google.android.apps.tv.launcherx` | ✅ Full support |
| **Android 13 (Tiramisu)** | 33 | `com.google.android.apps.tv.launcherx` | ✅ Full support |
| **Android 14 (Upside Down Cake)** | 34 | `com.google.android.apps.tv.launcherx` | ✅ Full support (includes shell-push fallback) |
| **Pre-Android 8.0 (Android 6 / 7)** | $\le$ 25 | Legacy AOSP / Leanback | ⚠️ Requires lowering `minSdk` in `launcher/app/build.gradle` |

### Repository Structure
```
androidtv-switcher-launcher/
├── switch.sh                       # Mac/Linux switcher script (modes: glass, glass-widgets, default)
├── switch.ps1                      # Windows PowerShell switcher script
├── switch.bat                      # Windows Command Prompt launcher
├── backup.sh                       # Standalone Android TV backup utility
├── restore.sh                      # Top-level disaster recovery launcher
├── build.sh                        # Recompiles glass/widgets APKs from source
├── launcher/                       # Complete Java & XML Android Gradle source (Liquid Glass & Adzan)
├── .env                            # Local TV IP configuration (git-ignored)
├── .env.example                    # Configuration template
├── apks/                           # Compiled release APKs
│   ├── glass-tv-fresh.apk          # Clean Apple TV grid, liquid glass, no widgets
│   ├── glass-tv-widgets.apk        # Digital clock, 3 Liquid Glass widgets & Adzan
│   ├── aqua-tv-home-fresh.apk
│   └── aqua-tv-home-widgets.apk
├── backups/                        # Automatic & manual TV snapshots
└── README.md
```

</details>

---

Launcher source based on [cocombee/glass-tv](https://github.com/cocombee/glass-tv) and [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT). Glass TV Home is package `com.gathin.tvhome` v2.7 (code 8).
