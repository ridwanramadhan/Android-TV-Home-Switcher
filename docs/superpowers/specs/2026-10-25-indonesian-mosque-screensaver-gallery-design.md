# Indonesian Mosque Screensaver Gallery Design

## Goal

Turn the existing Aqua TV Home clock screensaver into a rotating photographic gallery of Indonesian mosques. The screensaver keeps the digital clock, prayer, weather, AQI, and Adzan behavior visible while showing a different mosque every minute.

## Scope

- Bundle 25–30 curated landscape photographs from Wikimedia Commons in the widgets APK.
- Change the photograph every 60 seconds with a gentle crossfade.
- Show the mosque name and Indonesian location in the bottom-right corner.
- Show the photographer and source license in a smaller credit line.
- Preserve the existing liquid-glass widgets, clock movement, dashboard refresh, prayer countdown, Adzan checks, and OLED movement behavior.
- Keep the gallery usable without an internet connection.

The fresh build continues to use its current clock screensaver without the photo gallery or dashboard cards.

## Image Selection and Licensing

Each selected photograph must:

- be hosted on Wikimedia Commons;
- depict a mosque located in Indonesia;
- have a reusable public-domain, CC BY, or CC BY-SA license;
- have enough resolution and composition to support a 16:9 center crop;
- avoid prominent identifiable people where practical;
- add geographic and architectural variety across the collection.

The source record for every image will include its Commons file page, creator, license, mosque name, city or regency, and province. The APK will contain optimized derivatives, while `THIRD_PARTY_NOTICES.md` will retain complete human-readable attribution and source links. The on-screen credit will use a compact form such as `Photo: Jane Doe · CC BY-SA 4.0 · Wikimedia Commons`.

The optimized derivatives will preserve the source aspect as much as possible, use a landscape crop suitable for TV, and target 1280×720 or a nearby size. Compression will balance image quality with APK size and low-memory decoding.

## Architecture

### Gallery metadata

A small immutable gallery manifest will map each packaged drawable to:

- mosque name;
- location;
- photographer;
- short license label;
- Wikimedia Commons file-page URL.

The runtime does not query Wikimedia. The manifest and packaged images are the complete offline gallery.

### Gallery player

A dedicated `MosqueGallery` component will own gallery playback. It will:

- decode one next image at a time away from the main thread;
- retain only the current and incoming images during a crossfade;
- shuffle all entries and play each once before reshuffling;
- avoid showing the previous image as the first entry in a new shuffle;
- advance every 60 seconds while the screensaver is active;
- stop scheduled work and release image references when the screensaver stops.

If one image cannot be decoded, the player logs the failure, skips it, and advances to another entry. If no gallery image can be displayed, the existing ambient glow remains as the fallback background.

### Scene integration

`ClockScene` will create and control `MosqueGallery` only when dashboard panels are enabled. The gallery sits behind the existing clock and `DashboardView`. It starts and stops with both the system `ClockDream` and Home's idle `ClockActivity` because they share `ClockScene`.

Existing user-selected clock wallpaper behavior remains available. A configured Home clock wallpaper takes precedence over the bundled mosque gallery, so current themes retain their explicit choice.

## Visual Design

- The mosque photo fills the screen with center-crop scaling.
- A restrained black gradient overlays the image: darker around the clock and cards, lighter through the middle so architectural detail remains visible.
- The existing clock and liquid-glass cards remain above the photograph.
- The caption sits inside the bottom-right safe area and aligns to the right.
- The mosque name uses the medium app typeface; location sits below it in a smaller size; photographer, license, and Wikimedia Commons appear as the smallest line.
- Caption text receives a soft shadow or compact translucent backing to remain readable on bright photographs.
- The caption avoids the dashboard cards and TV overscan margins at 1280×720 and 1920×1080.
- Photo changes use a low-cost alpha crossfade. The clock and cards do not fade or reset during a photo change.

## Timing and State

The first photo appears when the screensaver starts. Each subsequent photo begins its transition 60 seconds later. The gallery shuffle is local to a screensaver session; restarting the screensaver starts a new shuffle. No network, persistent download cache, or long-term viewing history is required.

The existing one-minute `ClockScene` tick can continue updating the clock placement, prayer countdown, and Adzan checks. Gallery timing is owned separately so a slow image decode does not block those tasks.

## Performance and Memory

- Package images at TV-sized resolution instead of original camera resolution.
- Decode to `RGB_565` unless visual testing exposes unacceptable banding.
- Decode on a single background executor.
- Keep at most two decoded full-screen images during transition and one afterward.
- Do not animate per-frame effects beyond the platform alpha transition.
- Release gallery resources when dreaming stops or the activity closes.

These limits target the connected 1 GB, 720p Android TV while remaining suitable for 1080p displays.

## Verification

Automated tests will cover shuffle completeness, no immediate repeat across shuffle boundaries, metadata consistency, and decode-failure selection behavior where those rules can be isolated from Android UI classes.

Device verification on the connected TV will confirm:

- `ClockDream` remains the selected system screensaver;
- the first mosque image, clock, all three cards, and caption render together;
- the photo changes after 60 seconds;
- no immediate repeat occurs during several transitions;
- caption text remains inside the bottom-right safe area;
- the 720p clock and cards do not overlap;
- prayer, weather, AQI, and Adzan behavior still run;
- stopping and restarting the screensaver does not leave stale callbacks or excessive retained bitmap memory;
- the five-minute TV idle timeout is restored after accelerated tests.

## Documentation Deliverables

- Add complete per-photo attribution and license links to `THIRD_PARTY_NOTICES.md`.
- Update the README screensaver description to mention the bundled Indonesian mosque gallery, one-minute rotation, offline behavior, and on-screen captions.
- Rebuild both widgets APK aliases from the same verified output.
