<#
.SYNOPSIS
    Android TV Home Switcher for Windows PowerShell
.DESCRIPTION
    Universal launcher switcher compatible with all Android TV and Google TV devices.
    Modes:
      default  - Reverts to stock TV launcher (Google TV / Android TV / PatchWall)
      fresh    - Switches to Aqua TV Home, clean Apple TV-style grid
      widgets  - Switches to Aqua TV Home with Prayer Times, Weather & AQI cards
      status   - Displays device details, current Home & stock launcher detection
      backup   - Full backup of device properties, packages & stock APK
      restore  - Restores from latest backup
.EXAMPLE
    .\switch.ps1 fresh
    .\switch.ps1 default
    .\switch.ps1 status 192.168.1.50
    .\switch.ps1 widgets -Device 192.168.1.50
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

$FreshApk = Join-Path $ApksDir "aqua-tv-home-fresh.apk"
$WidgetsApk = Join-Path $ApksDir "aqua-tv-home-widgets.apk"
$LauncherPackage = "com.gathin.tvhome"
$LauncherActivity = "com.gathin.tvhome/.MainActivity"

function Show-Usage {
    Write-Host @"
Android TV Home Switcher (Windows PowerShell) v1.1.0

Usage:
  .\switch.ps1 <mode> [device_ip] [options]
  .\switch.ps1 status [device_ip]
  .\switch.ps1 backup [device_ip]
  .\switch.ps1 restore [device_ip]

Modes:
  default, stock, 1       Stock launcher (auto-detects Google TV, Android TV, PatchWall)
  fresh, appletv, 2       Aqua TV Home, clean Apple TV-style grid (no widgets)
  widgets, dashboard, 3   Aqua TV Home with Prayer Times, Weather & AQI cards

Management:
  status                  Show TV details, stock launcher detection, and backup status
  backup                  Create a full baseline backup of packages, settings & stock APK
  restore                 Restore the TV back to stock configuration from backup

Options:
  -Device <ip:port>       Target TV address (overrides .env)
  -NoBackup               Skip automatic baseline backup on first switch
  -DryRun                 Print actions without modifying the TV
  -Help                   Show this help

Examples:
  .\switch.ps1 status
  .\switch.ps1 fresh
  .\switch.ps1 default
  .\switch.ps1 fresh 192.168.1.50
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
    param([string[]]$AdbArgs)
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
    return ($stdout + "`n" + $stderr).Trim()
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
    $timestamp = (Get-Date -Format "yyyyMMdd_HHmmss")
    $brand = if ($DryRun) { "DryRunBrand" } else { (Run-Adb @("shell", "getprop ro.product.brand")).Trim() }
    $model = if ($DryRun) { "DryRunModel" } else { (Run-Adb @("shell", "getprop ro.product.model")).Trim() }
    $folderName = "${brand}_${model}_$timestamp" -replace '[\\/:*?"<>|\s]', '_'
    $dest = Join-Path $BackupsDir $folderName

    Write-Host "==> Creating safety backup in $dest" -ForegroundColor Blue
    if (-not $DryRun) {
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        (Run-Adb @("shell", "pm list packages -u")) | Out-File (Join-Path $dest "packages_all.txt") -Encoding utf8
        (Run-Adb @("shell", "pm list packages -e")) | Out-File (Join-Path $dest "packages_enabled.txt") -Encoding utf8
        (Run-Adb @("shell", "pm list packages -d")) | Out-File (Join-Path $dest "packages_disabled.txt") -Encoding utf8
        (Run-Adb @("shell", "dumpsys tv_input")) | Out-File (Join-Path $dest "tv_inputs.txt") -Encoding utf8
        (Run-Adb @("shell", "getprop")) | Out-File (Join-Path $dest "props_all.txt") -Encoding utf8

        # Pull stock APK
        $apkPath = (Run-Adb @("shell", "pm path $StockPackage")) -replace "^package:", ""
        if ($apkPath -and -not ($apkPath -match "package:")) {
            $firstApk = ($apkPath -split "`r?`n")[0].Trim()
            if ($firstApk) {
                & $AdbBin -s $Serial pull $firstApk (Join-Path $dest "stock_launcher.apk") | Out-Null
            }
        }
        Write-Host "[ok] Full safety backup completed: $folderName" -ForegroundColor Green
    } else {
        Write-Host "  [dry-run] Would dump packages, settings, tv_inputs, and pull stock launcher APK"
    }
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
    Write-Host "  Aqua Home    $LauncherPackage`n"
}

function Switch-Stock {
    Detect-StockLauncher
    Write-Host "==> Mode: Default Stock TV Launcher ($StockPackage)" -ForegroundColor Cyan
    Write-Host "==> Enabling $StockPackage" -ForegroundColor Cyan
    Run-Adb @("shell", "pm enable --user 0 $StockPackage") | Out-Null
    Write-Host "==> Setting default Home to $StockActivity" -ForegroundColor Cyan
    Run-Adb @("shell", "cmd package set-home-activity --user 0 $StockActivity") | Out-Null
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
        if (-not (Test-Path $BackupsDir) -or -not (Get-ChildItem $BackupsDir -Directory | Where-Object { $_.Name -match $slug })) {
            Write-Host "==> No previous backup found. Creating automatic baseline backup..." -ForegroundColor Yellow
            Backup-Tv
        }
    }

    Write-Host "==> Mode: $Label" -ForegroundColor Cyan
    Write-Host "==> Installing $(Split-Path -Leaf $Apk)..." -ForegroundColor Cyan

    if ($DryRun) {
        Write-Host "  [dry-run] adb -s $Serial install -r -d -g $Apk"
        Write-Host "  [dry-run] adb -s $Serial shell pm enable --user 0 $LauncherPackage"
        Write-Host "  [dry-run] adb -s $Serial shell pm grant $LauncherPackage android.permission.ACCESS_FINE_LOCATION"
        Write-Host "  [dry-run] adb -s $Serial shell cmd package set-home-activity --user 0 $LauncherActivity"
        Write-Host "  [dry-run] adb -s $Serial shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"
        Write-Host "  [dry-run] adb -s $Serial shell pm disable-user --user 0 $StockPackage"
        Write-Host "  [dry-run] adb -s $Serial shell settings put secure enabled_accessibility_services $LauncherPackage/.HomeKeyService"
        Write-Host "  [dry-run] adb -s $Serial shell settings put secure accessibility_enabled 1"
        return
    }

    & $AdbBin -s $Serial install -r -d -g $Apk | Out-Null
    Run-Adb @("shell", "pm enable --user 0 $LauncherPackage") | Out-Null
    Run-Adb @("shell", "pm grant $LauncherPackage android.permission.ACCESS_FINE_LOCATION") | Out-Null
    Run-Adb @("shell", "cmd package set-home-activity --user 0 $LauncherActivity") | Out-Null
    Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null

    # Disable stock launcher so remote HOME routes to Aqua
    if ($StockPackage -and $StockPackage -ne $LauncherPackage) {
        Write-Host "==> Setting remote HOME button to Aqua (disabling $StockPackage for user 0)..." -ForegroundColor Cyan
        Run-Adb @("shell", "pm disable-user --user 0 $StockPackage") | Out-Null
        Run-Adb @("shell", "settings put secure enabled_accessibility_services $LauncherPackage/.HomeKeyService") | Out-Null
        Run-Adb @("shell", "settings put secure accessibility_enabled 1") | Out-Null
    }

    Write-Host "`n[ok] Switched to $Label. Press HOME on your TV remote." -ForegroundColor Green
}

function Restore-Tv {
    Detect-StockLauncher
    Write-Host "==> Restoring stock TV configuration..." -ForegroundColor Yellow
    Run-Adb @("shell", "pm enable --user 0 $StockPackage") | Out-Null
    Run-Adb @("shell", "cmd package set-home-activity --user 0 $StockActivity") | Out-Null
    Run-Adb @("shell", "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME") | Out-Null
    Write-Host "[ok] TV restored to stock launcher. Press HOME on your TV remote." -ForegroundColor Green
}

# Main Command Dispatch
Connect-Tv

switch ($Action.ToLower()) {
    "status"   { Show-Status }
    "default"  { Switch-Stock }
    "stock"    { Switch-Stock }
    "1"        { Switch-Stock }
    "fresh"    { Switch-Aqua $FreshApk "Aqua TV Home (clean grid)" }
    "appletv"  { Switch-Aqua $FreshApk "Aqua TV Home (clean grid)" }
    "2"        { Switch-Aqua $FreshApk "Aqua TV Home (clean grid)" }
    "widgets"  { Switch-Aqua $WidgetsApk "Aqua TV Home (widgets: Prayer, Weather & AQI)" }
    "3"        { Switch-Aqua $WidgetsApk "Aqua TV Home (widgets: Prayer, Weather & AQI)" }
    "backup"   { Backup-Tv }
    "restore"  { Restore-Tv }
    default    { throw "Unknown mode '$Action'. Run .\switch.ps1 -Help to see available modes." }
}
