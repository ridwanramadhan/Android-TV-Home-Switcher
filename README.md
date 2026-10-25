# 📺 Android TV Home Switcher

Transform your Android TV or Google TV into a **clean, fast, ad-free Apple TV-style home screen** with a single command from your computer.

Works with **any** Android TV or Google TV brand (Sony, TCL, Xiaomi, Chromecast with Google TV, AQUA, Hisense, Philips, etc.).

| 📺 Original TV Home (Stock) | 🍏 Fresh Mode (Clean Apple TV Grid) | 🕌 Widgets Mode (Prayer, Weather & AQI) |
| :---: | :---: | :---: |
| <img src="docs/mode-default.png" width="280" alt="Default TV launcher"> | <img src="docs/mode-fresh.png" width="280" alt="Fresh theme"> | <img src="docs/mode-widgets.png" width="280" alt="Widgets theme"> |
| Your TV's original factory screen | Clean 5-slot dock + grid (zero ads) | Clean grid + Live Prayer, Weather & AQI |

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

Open Terminal on your computer, navigate to this folder, and run:
```bash
./switch.sh status 192.168.1.50
```
*(Replace `192.168.1.50` with your actual TV IP address).*

> 💡 **Look at your TV screen!**  
> A popup will appear saying **"Allow USB debugging?"**.  
> Use your remote to check the box **"Always allow from this computer"** and click **OK**.

---

### Step 3: Choose your Home Screen!
Whenever you want to switch your TV, just run the command you want:

```bash
# 🍏 Switch to clean Apple TV-style grid (no ads, super fast):
./switch.sh fresh

# 🕌 Switch to clean grid + Live Prayer Times, Weather & Air Quality:
./switch.sh widgets

# 📺 Revert back to your TV's original factory home screen anytime:
./switch.sh default
```

*(You can also pass your TV's IP directly, for example: `./switch.sh fresh 192.168.1.50`)*

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
```bash
./switch.sh default
```

### 2. Full System Restore from Backup
If you ever want to re-enable everything and restore exact factory states:
```bash
./switch.sh restore
```
Or view all saved device backups:
```bash
./restore.sh --list
```

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
* **Fix:** You need the standard Android tools installed. On Mac, run:
  ```bash
  brew install android-platform-tools
  ```
  *(Or if you have Android Studio installed, it will automatically find it).*

### Q: On Google TV, the remote asks "Complete action using..."
* **Fix:** On newer Google TV sets (Chromecast, Sony, TCL), Android may ask you which launcher to use when you press HOME for the first time. Simply highlight **Aqua TV Home** and click **Always**.

### Q: Where do the Prayer Times & Weather come from?
* **Widgets Mode** automatically gets your location from the TV to show local prayer times (AlAdhan / KEMENAG) and weather / Air Quality (Open-Meteo). If your TV has no location fix, it defaults to Jakarta coordinates.

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
   ./build.sh fresh       # Recompiles clean grid build
   ./build.sh widgets     # Recompiles widgets build
   ./build.sh all         # Recompiles both variants
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
├── switch.sh                       # Universal launcher switcher & coordinator
├── backup.sh                       # Standalone Android TV backup utility
├── restore.sh                      # Top-level disaster recovery launcher
├── build.sh                        # Recompiles fresh/widgets APKs from source
├── launcher/                       # Complete Java & XML Android Gradle source
├── .env                            # Local TV IP configuration (git-ignored)
├── .env.example                    # Configuration template
├── apks/                           # Compiled release APKs
│   ├── aqua-tv-home-fresh.apk
│   └── aqua-tv-home-widgets.apk
├── backups/                        # Automatic & manual TV snapshots
└── README.md
```

</details>

---

Launcher source based on [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT). Aqua TV Home is package `com.gathin.tvhome` v2.7 (code 8).
