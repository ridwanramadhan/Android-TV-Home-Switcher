<#
.SYNOPSIS
    Android TV Home Switcher for Windows PowerShell
.DESCRIPTION
    Universal launcher switcher compatible with all Android TV and Google TV devices.
    Modes:
      default       - Reverts to the stock TV launcher (Google TV / Android TV / PatchWall)
      glass         - Switches to Glass TV, clean Apple TV-style grid (liquid glass, no widgets)
      glass-widgets - Switches to Glass TV with Prayer Times, Weather & AQI widgets + Adzan
      status        - Displays device details, current Home & stock launcher detection
      backup        - Full backup of device properties, packages, settings & stock APK
      restore       - Restores stock Home & packages from the latest backup
.EXAMPLE
    .\switch.ps1 glass
    .\switch.ps1 default
    .\switch.ps1 status 192.168.1.50
    .\switch.ps1 glass-widgets -Device 192.168.1.50
#>

param(
    [Parameter(Position = 0)][string]$Action = "",
    [Parameter(Position = 1)][string]$Device = "",
    [switch]$DryRun,
    [switch]$NoBackup,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $ScriptDir ".env"
$BackupsDir = Join-Path $ScriptDir "backups"
$ApksDir = Join-Path $ScriptDir "apks"

$GlassFreshApk = Join-Path $ApksDir "glass-tv-fresh.apk"
$GlassWidgetsApk = Join-Path $ApksDir "glass-tv-widgets.apk"
$FreshApk = if (Test-Path $GlassFreshApk) { $GlassFreshApk } else { Join-Path $ApksDir "aqua-tv-home-fresh.apk" }
$WidgetsApk = if (Test-Path $GlassWidgetsApk) { $GlassWidgetsApk } else { Join-Path $ApksDir "aqua-tv-home-widgets.apk" }
$LauncherPackage = "com.gathin.tvhome"
$LauncherActivity = "com.gathin.tvhome/.MainActivity"

function Show-Usage {
    Write-Host @"
Android TV Home Switcher (Windows PowerShell) v1.1.0
Attribution: https://github.com/cocombee/glass-tv

Usage:
  .\switch.ps1 <mode> [device_ip] [options]
  .\switch.ps1 status [device_ip]
  .\switch.ps1 backup [device_ip]
  .\switch.ps1 restore [device_ip]

Modes:
  default, stock, 1            Stock launcher (auto-detects Google TV, Android TV, PatchWall)
  glass, glass-fresh, fresh, 2 Glass TV clean grid (Apple TV style, liquid glass, no widgets)
  glass-widgets, widgets, 3    Glass TV with digital clock, 3 Liquid Glass widgets & Adzan sound

Management:
  status                  Show TV details, stock launcher detection, and backup status
  backup                  Create a full baseline backup of packages, settings & stock APK
  restore                 Restore stock Home & packages from the latest backup

Options:
  -Device <ip:port>       Target TV address (overrides .env)
  -NoBackup               Skip automatic baseline backup on first switch
  -DryRun                 Print actions without modifying the TV
  -Help                   Show this help

Examples:
  .\switch.ps1 status
  .\switch.ps1 glass
  .\switch.ps1 glass-widgets
  .\switch.ps1 default
  .\switch.ps1 glass 192.168.1.50
"@
}

if ($Help -or -not $Action) {
    Show-Usage
    exit 0
}

function Load-Env {
    if (Test-Path $EnvFile) {
        Get-Content $EnvFile | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
                $name = $matches[1].Trim()
                $val = $matches[2].Trim().Trim('"').Trim("'")
                if (-not (Get-Variable -Name $name -Scope Script -ErrorAction SilentlyContinue)) {
                    Set-Variable -Name $name -Value $val -Scope Script
                }
            }
        }
    }
}

function Find-Adb {
    if (Get-Command adb.exe -ErrorAction SilentlyContinue) {
        return "adb.exe"
    }
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        return "adb"
    }
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe",
        "$env:ProgramFiles(x86)\Android\platform-tools\adb.exe",
        "C:\platform-tools\adb.exe",
        "C:\adb\adb.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            return $c
        }
    }
    if ($DryRun) { return "adb.exe" }
    throw "adb was not found. Install it on Windows using: winget install Google.PlatformTools"
}

Load-Env

# Validate device target
$TvHost = if ($Device) { $Device } elseif ($script:TV_HOST) { $script:TV_HOST } else { "" }
$TvPort = if ($script:TV_PORT) { $script:TV_PORT } else { "5555" }

if (-not $TvHost) {
    throw "TV address is empty. Specify it as argument (.\switch.ps1 status 192.168.1.50) or set TV_HOST in .env"
}

$Serial = if ($TvHost -match ":") { $TvHost } else { "$TvHost`:$TvPort" }
$AdbBin = Find-Adb

function Run-Adb {
    param([string[]]$AdbArgs, [switch]$CheckExitCode)
    if ($DryRun) {
        Write-Host "  [dry-run] adb -s $Serial $($AdbArgs -join ' ')"
        return ""
    }
    $pinfo = New-Object System.Diagnostics.ProcessStartInfo
    $pinfo.FileName = $AdbBin
    $escapedArgs = @("-s", $Serial) + $AdbArgs
    $pinfo.Arguments = ($escapedArgs | ForEach-Object { if ($_ -match '\s') { "`"$_`"" } else { $_ } }) -join ' '
    $pinfo.RedirectStandardOutput = $true
    $pinfo.RedirectStandardError = $true
    $pinfo.UseShellExecute = $false
    $pinfo.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($pinfo)
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    $combined = ($stdout + "`n" + $stderr).Trim()
    if ($CheckExitCode -and $proc.ExitCode -ne 0) {
        throw "adb failed (exit $($proc.ExitCode)): $combined"
    }
    return $combined
}

function Connect-Tv {
    if ($DryRun) {
        Write-Host "  [dry-run] adb connect $Serial"
        return
    }
    $state = (Run-Adb @("get-state"))
    if ($state -match "device") {
        Write-Host "[ok] Already connected: $Serial" -ForegroundColor Green
        return
    }
    Write-Host "==> Connecting to $Serial" -ForegroundColor Cyan
    & $AdbBin connect $Serial | Out-Null
    $i = 0
    while ($i -lt 15) {
        $state = (Run-Adb @("get-state"))
        if ($state -match "device") {
            Write-Host "[ok] Connected: $Serial" -ForegroundColor Green
            return
        }
        if ($state -match "unauthorized") {
            throw "The TV has not authorized this computer. Accept the 'Allow USB debugging?' prompt on the TV screen."
        }
        Start-Sleep -Seconds 1
        $i++
    }
    throw "Could not connect to $Serial after 15s. Ensure TV is awake and Network debugging is ON."
}

function Resolve-Home {
    $out = Run-Adb @("shell", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
    $line = ($out -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -Last 1)
    if (-not $line -or $line -match "No activity found") {
        $out = Run-Adb @("shell", "pm resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME")
        $line = ($out -split "`r?`n" | Where-Object { $_ -match "^[a-zA-Z0-9._]+/[a-zA-Z0-9._]+" } | Select-Object -First 1)
    }
    return $line
}

# cmd package set-home-activity exists on API 30+; API 26-29 only shipped pm set-home-activity.
function Set-HomeActivity {
    param([string]$Component)

    if (-not $Component) { throw "Set-HomeActivity requires a component name." }
    Write-Host "==> Setting default Home to $Component" -ForegroundColor Cyan

    if ($DryRun) {
        $dryCmd = "cmd package set-home-activity --user 0 $Component"
        Write-Host "  [dry-run] adb -s $Serial shell $dryCmd"
        return
    }

    $out = ""
    $failed = $false
    try {
        $out = Run-Adb @("shell", "cmd package set-home-activity --user 0 $Component") -CheckExitCode
    } catch {
        $failed = $true
        $out = $_.Exception.Message
    }

    if ($failed -or $out -match "Error|error|Exception|exception|Unknown|unknown") {
        if ($out) { Write-Host $out -ForegroundColor Yellow }
        Write-Host "==> cmd package unavailable; trying pm set-home-activity (API 26-29)..." `
            -ForegroundColor Yellow
        try {
            $out = Run-Adb @("shell", "pm set-home-activity $Component") -CheckExitCode
        } catch {
            throw "Could not set Home to $Component`: $($_.Exception.Message)"
        }
        if ($out -match "Error|error|Exception|exception|Unknown|unknown") {
            throw "Could not set Home to $Component`: $out"
        }
    }
    if ($out) { Write-Host $out.Trim() }
}

# Poll until Home resolves to $Package, so the stock launcher is never disabled blind.
function Wait-ForHome {
    param([string]$Package, [int]$Attempts = 5)

    if ($DryRun) { return $true }
    for ($i = 0; $i -lt $Attempts; $i++) {
        $home = Resolve-Home
        if ($home -and $home.Trim().StartsWith("$Package/")) {
            Write-Host "[ok] Home now resolves to $home" -ForegroundColor Green
            return $true
        }
        if ($i -lt ($Attempts - 1)) { Start-Sleep -Seconds 1 }
    }
    return $false
}

function Detect-StockLauncher {
    if ($DryRun) {
        $script:StockPackage = "com.google.android.apps.tv.launcherx"
        $script:StockActivity = "com.google.android.apps.tv.launcherx/.home.HomeActivity"
        $script:StockLabel = "Google TV"
        return
    }
    $currentHome = Resolve-Home
    $installed = Run-Adb @("shell", "pm list packages -u")

    if ($currentHome -and -not $currentHome.StartsWith("$LauncherPackage/") -and $currentHome.Contains("/")) {
        $script:StockActivity = $currentHome.Trim()
        $script:StockPackage = $currentHome.Split('/')[0].Trim()
        $script:StockLabel = "active system home"
        return
    }
    if ($installed -match "package:com\.google\.android\.apps\.tv\.launcherx") {
        $script:StockPackage = "com.google.android.apps.tv.launcherx"
        $script:StockActivity = "com.google.android.apps.tv.launcherx/.home.HomeActivity"
        $script:StockLabel = "Google TV"
        return
    }
    if ($installed -match "package:com\.google\.android\.tvlauncher") {
        $script:StockPackage = "com.google.android.tvlauncher"
        $script:StockActivity = "com.google.android.tvlauncher/.MainActivity"
        $script:StockLabel = "Android TV Leanback"
        return
    }
    if ($installed -match "package:com\.mitv\.tvhome") {
        $script:StockPackage = "com.mitv.tvhome"
        $script:StockActivity = "com.mitv.tvhome/.MainActivity"
        $script:StockLabel = "Xiaomi PatchWall"
        return
    }
    if ($installed -match "package:com\.amazon\.tv\.launcher") {
        $script:StockPackage = "com.amazon.tv.launcher"
        $script:StockActivity = "com.amazon.tv.launcher/.ui.HomeActivity_v3"
        $script:StockLabel = "Amazon Fire TV"
        return
    }
    $script:StockPackage = "com.google.android.tvlauncher"
    $script:StockActivity = "com.google.android.tvlauncher/.MainActivity"
    $script:StockLabel = "default fallback"
}

function Backup-Tv {
    Detect-StockLauncher

    $timestamp = (Get-Date -Format "yyyyMMdd_HHmmss")
    $brand = if ($DryRun) { "DryRunBrand" } else { (Run-Adb @("shell", "getprop ro.product.brand")).Trim() }
    $model = if ($DryRun) { "DryRunModel" } else { (Run-Adb @("shell", "getprop ro.product.model")).Trim() }
    $home = if ($DryRun) { $StockActivity } else { "$(Resolve-Home)".Trim() }
    $folderName = "${brand}_${model}_$timestamp" -replace '[\\/:*?"<>|\s]', '_'
    $dest = Join-Path $BackupsDir $folderName

    Write-Host "==> Creating safety backup in $dest" -ForegroundColor Blue
    $homeQuery = "cmd package query-activities --brief -a android.intent.action.MAIN"
    $homeQuery += " -c android.intent.category.HOME"
    if ($DryRun) {
        Write-Host "  [dry-run] adb -s $Serial shell pm list packages -u"
        Write-Host "  [dry-run] adb -s $Serial shell pm list packages -e"
        Write-Host "  [dry-run] adb -s $Serial shell pm list packages -d"
        Write-Host "  [dry-run] adb -s $Serial shell dumpsys tv_input"
        Write-Host "  [dry-run] adb -s $Serial shell getprop"
        Write-Host "  [dry-run] adb -s $Serial shell settings list system"
        Write-Host "  [dry-run] adb -s $Serial shell settings list secure"
        Write-Host "  [dry-run] adb -s $Serial shell settings list global"
        Write-Host "  [dry-run] adb -s $Serial shell $homeQuery"
        Write-Host "  [dry-run] adb -s $Serial shell pm path $StockPackage"
        Write-Host "  [dry-run] adb -s $Serial pull <apk> $dest\stock_launcher.apk"
        Write-Host "  [dry-run] write backup_info.env (stock package, activity, device, date)"
        return
    }

    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    (Run-Adb @("shell", "pm list packages -u")) | Out-File (Join-Path $dest "packages_all.txt") -Encoding utf8
    (Run-Adb @("shell", "pm list packages -e")) | Out-File (Join-Path $dest "packages_enabled.txt") -Encoding utf8
    (Run-Adb @("shell", "pm list packages -d")) | Out-File (Join-Path $dest "packages_disabled.txt") -Encoding utf8
    (Run-Adb @("shell", "dumpsys tv_input")) | Out-File (Join-Path $dest "tv_inputs.txt") -Encoding utf8
    (Run-Adb @("shell", "getprop")) | Out-File (Join-Path $dest "props_all.txt") -Encoding utf8

    # Settings dumps and HOME claimants, matching backup.sh so restore has the same inputs.
    $settingsDumps = @(
        @{ File = "settings_system.txt"; Cmd = "settings list system" },
        @{ File = "settings_secure.txt"; Cmd = "settings list secure" },
        @{ File = "settings_global.txt"; Cmd = "settings list global" }
    )
    foreach ($dump in $settingsDumps) {
        $lines = (Run-Adb @("shell", $dump.Cmd)) -split "`r?`n" | Sort-Object
        ($lines -join "`r`n") | Out-File (Join-Path $dest $dump.File) -Encoding utf8
    }
    $homeFile = Join-Path $dest "home_activities.txt"
    (Run-Adb @("shell", $homeQuery)) | Out-File $homeFile -Encoding utf8

    # Restore-Tv reads this file back, so record the stock Home with the backup.
    $info = @(
        "BACKUP_DEVICE_SERIAL=`"$Serial`"",
        "BACKUP_BRAND=`"$brand`"",
        "BACKUP_MODEL=`"$model`"",
        "BACKUP_CURRENT_HOME=`"$home`"",
        "BACKUP_STOCK_PACKAGE=`"$StockPackage`"",
        "BACKUP_STOCK_ACTIVITY=`"$StockActivity`"",
        "BACKUP_DATE=`"$timestamp`""
    )
    $info | Out-File (Join-Path $dest "backup_info.env") -Encoding ascii

    # Pull stock APK
    $apkPath = (Run-Adb @("shell", "pm path $StockPackage")) -replace "^package:", ""
    if ($apkPath -and -not ($apkPath -match "package:")) {
        $firstApk = ($apkPath -split "`r?`n")[0].Trim()
        if ($firstApk) {
            & $AdbBin -s $Serial pull $firstApk (Join-Path $dest "stock_launcher.apk") | Out-Null
        }
    }
    Write-Host "[ok] Full safety backup completed: $folderName" -ForegroundColor Green
}

function Show-Status {
    Detect-StockLauncher
    $brand = if ($DryRun) { "DryRunBrand" } else { (Run-Adb @("shell", "getprop ro.product.brand")).Trim() }
    $model = if ($DryRun) { "DryRunModel" } else { (Run-Adb @("shell", "getprop ro.product.model")).Trim() }
    $release = if ($DryRun) { "11" } else { (Run-Adb @("shell", "getprop ro.build.version.release")).Trim() }
    $sdk = if ($DryRun) { "30" } else { (Run-Adb @("shell", "getprop ro.build.version.sdk")).Trim() }
    $home = if ($DryRun) { "$StockActivity" } else { Resolve-Home }

    Write-Host "`n  Device       $Serial"
    Write-Host "  Hardware     $brand $model (Android $release, SDK $sdk)"
    Write-Host "  Active Home  $home"
    Write-Host "  Stock TV     $StockPackage [$StockLabel]"
    Write-Host "  Glass Home   $LauncherPackage`n"
}

function Switch-Stock {
    Detect-StockLauncher
    Write-Host "==> Mode: Default Stock TV Launcher ($StockPackage)" -ForegroundColor Cyan
    Write-Host "==> Enabling $StockPackage" -ForegroundColor Cyan
    Run-Adb @("shell", "pm enable --user 0 $StockPackage") | Out-Null
    Set-HomeActivity -Component $StockActivity
    Write-Host "==> Starting Home" -ForegroundColor Cyan
    Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null
    Write-Host "`n[ok] Switched to default stock launcher. Press HOME on your TV remote." -ForegroundColor Green
}

function Switch-Aqua {
    param([string]$Apk, [string]$Label)
    if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }
    Detect-StockLauncher

    if (-not $NoBackup) {
        $slug = "${StockPackage}"
        $existing = @()
        if (Test-Path $BackupsDir) {
            $existing = @(Get-ChildItem $BackupsDir -Directory |
                Where-Object { $_.Name -match $slug })
        }
        if ($existing.Count -eq 0) {
            Write-Host "==> No previous backup found. Creating baseline backup..." `
                -ForegroundColor Yellow
            Backup-Tv
        }
    }

    Write-Host "==> Mode: $Label" -ForegroundColor Cyan
    Write-Host "==> Installing $(Split-Path -Leaf $Apk)..." -ForegroundColor Cyan

    $a11yService = "$LauncherPackage/.HomeKeyService"
    $a11yCmd = "settings put secure enabled_accessibility_services $a11yService"
    $startHome = "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"
    $setHome = "cmd package set-home-activity --user 0 $LauncherActivity"

    if ($DryRun) {
        Write-Host "  [dry-run] adb -s $Serial install -r -d -g $Apk"
        Write-Host "  [dry-run] adb -s $Serial shell pm enable --user 0 $LauncherPackage"
        Write-Host "  [dry-run] adb -s $Serial shell pm grant $LauncherPackage android.permission.ACCESS_FINE_LOCATION"
        Write-Host "  [dry-run] adb -s $Serial shell appops set $LauncherPackage SYSTEM_ALERT_WINDOW allow"
        Write-Host "  [dry-run] adb -s $Serial shell $setHome"
        Write-Host "  [dry-run] adb -s $Serial shell $startHome"
        Write-Host "  [dry-run] verify Home resolves to $LauncherPackage before disabling stock"
        Write-Host "  [dry-run] adb -s $Serial shell pm disable-user --user 0 $StockPackage"
        Write-Host ("  [dry-run] adb -s $Serial shell " + $a11yCmd)
        Write-Host "  [dry-run] adb -s $Serial shell settings put secure accessibility_enabled 1"
        return
    }

    # 1. Install and prove success before Home or the stock launcher is touched.
    $installOut = Run-Adb @("install", "-r", "-d", "-g", $Apk)
    if ($installOut -notmatch "Success") {
        if ($installOut -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
            throw ("The installed Glass TV build was signed with a different key. " +
                "Uninstall it with 'adb -s $Serial uninstall $LauncherPackage' " +
                "(this clears its settings), then run this switch again.")
        }
        Write-Host "==> Standard install failed. Retrying via adb push (Android 14)..." `
            -ForegroundColor Yellow
        $tempApk = "/data/local/tmp/" + (Split-Path -Leaf $Apk)
        Run-Adb @("push", $Apk, $tempApk) | Out-Null
        $installOut = Run-Adb @("shell", "pm install -r -d -g $tempApk")
        Run-Adb @("shell", "rm -f $tempApk") | Out-Null
        if ($installOut -notmatch "Success") {
            throw "Could not install $Apk. Check TV free space and APK integrity: $installOut"
        }
        Write-Host "[ok] APK installed (via adb push fallback)" -ForegroundColor Green
    } else {
        Write-Host "[ok] APK installed" -ForegroundColor Green
    }

    # 2. Enable, grant and prepare the launcher.
    Run-Adb @("shell", "pm enable --user 0 $LauncherPackage") | Out-Null
    Run-Adb @("shell", "pm grant $LauncherPackage android.permission.ACCESS_FINE_LOCATION") | Out-Null
    Run-Adb @("shell", "appops set $LauncherPackage SYSTEM_ALERT_WINDOW allow") | Out-Null

    # 3. Switch Home, then verify it before disabling anything.
    Set-HomeActivity -Component $LauncherActivity
    Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null

    if (-not (Wait-ForHome -Package $LauncherPackage)) {
        Write-Host "==> Home is not $LauncherPackage yet. Re-enabling $StockPackage..." `
            -ForegroundColor Yellow
        Run-Adb @("shell", "pm enable --user 0 $StockPackage") | Out-Null
        throw ("Home did not switch to $LauncherPackage. The TV was left on $StockPackage. " +
            "Run .\switch.ps1 default to recover.")
    }

    # 4. Verification passed, so it is safe to route the remote HOME button to Glass TV.
    if ($StockPackage -and $StockPackage -ne $LauncherPackage) {
        Write-Host "==> Home verified. Disabling $StockPackage for user 0..." -ForegroundColor Cyan
        Run-Adb @("shell", "pm disable-user --user 0 $StockPackage") | Out-Null
        Run-Adb @("shell", $a11yCmd) | Out-Null
        Run-Adb @("shell", "settings put secure accessibility_enabled 1") | Out-Null
    }

    Write-Host "`n[ok] Switched to $Label. Press HOME on your TV remote." -ForegroundColor Green
}

function Find-LatestBackup {
    if (-not (Test-Path $BackupsDir)) { return $null }

    # Prefer the 'latest' symlink (backup.sh creates it) and never follow other symlinks.
    $latest = Get-Item (Join-Path $BackupsDir "latest") -Force -ErrorAction SilentlyContinue
    if ($latest -and $latest.LinkType) {
        $target = @($latest.Target)[0]
        if ($target) {
            if (-not [System.IO.Path]::IsPathRooted($target)) {
                $target = Join-Path $BackupsDir $target
            }
            $resolved = Get-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
            if ($resolved -and $resolved.PSIsContainer) { return $resolved.FullName }
        }
    }

    $folders = @(Get-ChildItem $BackupsDir -Directory -Force |
        Where-Object { -not $_.LinkType } |
        Sort-Object LastWriteTime -Descending)
    if ($folders.Count -gt 0) { return $folders[0].FullName }
    return $null
}

function Read-BackupInfo {
    param([string]$Path, [string]$Key)

    if (-not (Test-Path -LiteralPath $Path)) { return "" }
    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^$Key=" } |
        Select-Object -First 1
    if (-not $line) { return "" }
    return ($line -replace "^$Key=", "").Trim().Trim('"').Trim("'")
}

function Restore-Tv {
    $backup = Find-LatestBackup
    if (-not $backup) {
        Write-Host "[!] No backup found in $BackupsDir." -ForegroundColor Yellow
        Write-Host "    Falling back to detection; package/settings not restored." `
            -ForegroundColor Yellow
        Detect-StockLauncher
        Write-Host "==> Restoring stock TV configuration..." -ForegroundColor Yellow
        Run-Adb @("shell", "pm enable --user 0 $StockPackage") | Out-Null
        Set-HomeActivity -Component $StockActivity
        Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null
        Write-Host "[ok] TV restored to stock launcher. Press HOME on your TV remote." -ForegroundColor Green
        return
    }

    $backupName = Split-Path -Leaf $backup
    Write-Host "==> Restoring from backup: $backupName" -ForegroundColor Cyan
    $infoPath = Join-Path $backup "backup_info.env"
    $stockPackage = Read-BackupInfo $infoPath "BACKUP_STOCK_PACKAGE"
    $stockActivity = Read-BackupInfo $infoPath "BACKUP_STOCK_ACTIVITY"

    if (-not $stockPackage) {
        Detect-StockLauncher
        $stockPackage = $StockPackage
        $stockActivity = $StockActivity
    } elseif (-not $stockActivity) {
        Detect-StockLauncher
        if ($StockPackage -eq $stockPackage) {
            $stockActivity = $StockActivity
        } else {
            $stockActivity = "$stockPackage/.MainActivity"
        }
    }
    Write-Host "    Stock Home: $stockActivity" -ForegroundColor Cyan

    # 1. Re-enable the stock launcher package.
    Run-Adb @("shell", "pm enable --user 0 $stockPackage") | Out-Null

    # 2. Re-enable everything that was enabled when the backup was taken.
    $enabledList = Join-Path $backup "packages_enabled.txt"
    if (Test-Path -LiteralPath $enabledList) {
        Write-Host "==> Re-enabling packages recorded as enabled at backup time..." `
            -ForegroundColor Cyan
        foreach ($line in Get-Content -LiteralPath $enabledList) {
            $pkg = $line.Trim()
            if ($pkg -match "^package:") { $pkg = $pkg.Substring(8) }
            if ($pkg) { Run-Adb @("shell", "pm enable --user 0 $pkg") | Out-Null }
        }
    } else {
        Write-Host "[!] packages_enabled.txt missing; skipping package re-enable." `
            -ForegroundColor Yellow
    }

    # 3. Reinstall the backed-up stock launcher APK if its package is missing.
    $stockApk = Join-Path $backup "stock_launcher.apk"
    if (Test-Path -LiteralPath $stockApk) {
        if ($DryRun) {
            Write-Host "  [dry-run] if $stockPackage is missing:"
            Write-Host "  [dry-run] adb -s $Serial install -r -d -g $stockApk"
        } else {
            $installed = (Run-Adb @("shell", "pm list packages")) -split "`r?`n"
            if ($installed -notcontains "package:$stockPackage") {
                Write-Host "==> Reinstalling stock launcher: $stockPackage" -ForegroundColor Cyan
                Run-Adb @("install", "-r", "-d", "-g", $stockApk) | Out-Null
            }
        }
    }

    # 4. Point Home back at the stock component and start it.
    Set-HomeActivity -Component $stockActivity
    Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null

    Write-Host "[ok] Restored stock Home from $backupName. Press HOME on your TV remote." `
        -ForegroundColor Green
}

# Main Command Dispatch
Connect-Tv

# Each mode is defined once; Aliases lists every accepted spelling for it.
$Modes = @{
    stock = @{
        Aliases = @("default", "stock", "1")
        Label   = "Stock TV Launcher"
    }
    glass = @{
        Aliases = @("glass", "glass-fresh", "fresh", "appletv", "2")
        Apk     = $FreshApk
        Label   = "Glass TV (clean liquid glass grid)"
    }
    widgets = @{
        Aliases = @("glass-widgets", "widgets", "dashboard", "3")
        Apk     = $WidgetsApk
        Label   = "Glass TV (widgets: Prayer, Weather & AQI + Adzan)"
    }
    status = @{ Aliases = @("status") }
    backup = @{ Aliases = @("backup") }
    restore = @{ Aliases = @("restore") }
}

$AliasMap = @{}
foreach ($mode in $Modes.Keys) {
    foreach ($alias in $Modes[$mode].Aliases) {
        $AliasMap[$alias] = $mode
    }
}

$requested = $Action.ToLower()
if (-not $AliasMap.ContainsKey($requested)) {
    throw "Unknown mode '$Action'. Run .\switch.ps1 -Help to see available modes."
}

switch ($AliasMap[$requested]) {
    "stock"   { Switch-Stock }
    "glass"   { Switch-Aqua $Modes.glass.Apk $Modes.glass.Label }
    "widgets" { Switch-Aqua $Modes.widgets.Apk $Modes.widgets.Label }
    "status"  { Show-Status }
    "backup"  { Backup-Tv }
    "restore" { Restore-Tv }
}
