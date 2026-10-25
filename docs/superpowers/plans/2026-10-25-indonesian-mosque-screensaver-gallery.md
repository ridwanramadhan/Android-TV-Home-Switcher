# Indonesian Mosque Screensaver Gallery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline 30-photo Wikimedia Commons gallery of Indonesian mosques to the widgets screensaver, rotating once per minute with bottom-right location and attribution captions.

**Architecture:** A reproducible importer downloads Wikimedia-generated 1280-pixel derivatives and validates their metadata. Plain Java model and shuffle classes describe the gallery, while a focused Android `MosqueGallery` owns bitmap decoding, crossfades, captions, and cleanup. `ClockScene` starts the gallery only for the widgets build and yields to an explicitly configured clock wallpaper.

**Tech Stack:** Java 17, Android API 26+, Android framework views and bitmap APIs, JUnit 4, Python 3 standard library for the maintainer-only Wikimedia importer, Gradle 8.14, ADB.

## Global Constraints

- Bundle exactly 30 curated landscape photographs hosted on Wikimedia Commons and depicting mosques in Indonesia.
- Accept only public-domain, CC BY, or CC BY-SA source licenses and retain creator, license, and Commons file-page attribution.
- Change the image every 60 seconds and avoid repeats until the full collection has played.
- Keep the clock, prayer, weather, AQI, Adzan, OLED movement, and user-selected clock wallpaper behavior.
- Keep the gallery fully offline at runtime; the app must not contact Wikimedia.
- Optimize every image to a Wikimedia-generated derivative no wider than 1280 pixels and decode it as `RGB_565`.
- Keep at most two full-screen bitmaps during a crossfade and one after it.
- Preserve Android 8.0 / API 26 as the minimum supported version.
- Preserve the existing uncommitted 720p clock-spacing fix in `ClockScene.java` and the rebuilt widgets APKs.

---

## File Map

- Create `scripts/import-mosque-gallery.py`: validate curated Commons metadata, download 1280-pixel derivatives, and generate the Java metadata list plus attribution Markdown.
- Create `docs/mosque-gallery-sources.json`: curated Commons file titles and user-facing mosque/location text.
- Create `launcher/app/src/main/res/drawable-nodpi/mosque_*.jpg`: exactly 30 optimized gallery images.
- Create `launcher/app/src/main/java/com/gathin/tvhome/MosquePhoto.java`: immutable metadata value object.
- Create `launcher/app/src/main/java/com/gathin/tvhome/MosquePhotos.java`: generated static list that pairs drawable resources with captions and credits.
- Create `launcher/app/src/main/java/com/gathin/tvhome/MosqueRotation.java`: pure Java shuffled-deck logic.
- Create `launcher/app/src/test/java/com/gathin/tvhome/MosqueRotationTest.java`: shuffled-deck regression tests.
- Create `launcher/app/src/test/java/com/gathin/tvhome/MosquePhotosTest.java`: gallery count and metadata validation.
- Create `launcher/app/src/main/java/com/gathin/tvhome/MosqueGallery.java`: Android bitmap decode, timing, crossfade, caption, and cleanup.
- Create `launcher/app/src/main/res/drawable/dream_mosque_scrim.xml`: dark readability gradient.
- Modify `launcher/app/src/main/res/layout/dream_clock.xml`: add two image layers, scrim, and bottom-right caption views.
- Modify `launcher/app/src/main/java/com/gathin/tvhome/ClockScene.java`: select, start, and stop the gallery without changing dashboard or Adzan behavior.
- Modify `THIRD_PARTY_NOTICES.md`: include the generated 30-photo attribution section.
- Modify `README.md`: document the offline mosque gallery and one-minute rotation.
- Rebuild `apks/glass-tv-widgets.apk` and `apks/aqua-tv-home-widgets.apk`.

---

### Task 1: Reproducible Wikimedia Source Set

**Files:**

- Create: `docs/mosque-gallery-sources.json`
- Create: `scripts/import-mosque-gallery.py`
- Create: `launcher/app/src/main/res/drawable-nodpi/mosque_*.jpg`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/MosquePhotos.java`
- Modify: `THIRD_PARTY_NOTICES.md`

**Interfaces:**

- Consumes: Wikimedia Commons `action=query`, `prop=imageinfo`, `iiprop=url|extmetadata`, and `iiurlwidth=1280` responses.
- Produces: `MosquePhotos.all()` returning `List<MosquePhoto>` and exactly 30 `mosque_*.jpg` drawables.

- [ ] **Step 1: Curate the 30 source records**

Create a JSON array with exactly 30 reviewed Commons file titles. Each object has stable user-facing text independent of inconsistent Commons descriptions:

```json
[
  {
    "slug": "istiqlal_jakarta",
    "commons_title": "File:Masjid Istiqlal Jakarta.jpg",
    "mosque": "Masjid Istiqlal",
    "location": "Jakarta"
  }
]
```

Cover multiple regions and architectural styles. Start the visual review with these named targets and replace a target only when Commons has no reusable landscape photograph: Istiqlal, Baiturrahman, Al Jabbar, 99 Kubah, Masjid Raya Sumatera Barat, Dian Al-Mahri, Masjid Agung Jawa Tengah, Al-Akbar Surabaya, Islamic Center Samarinda, Hubbul Wathan Mataram, An-Nur Pekanbaru, Masjid Raya Medan, Sultan Suriansyah, Menara Kudus, Masjid Agung Demak, Masjid Agung Banten, Saka Tunggal Banyumas, Gedhe Kauman Yogyakarta, Cheng Ho Surabaya, Cheng Ho Palembang, Al-Irsyad Satya, Amirul Mukminin Makassar, Masjid Agung Palembang, Masjid Agung Tuban, Jami Malang, Sunan Ampel, Masjid Kampus UGM, Masjid Raya Al-Muttaqin Harau, Masjid Jami Lasem, and Hunto Sultan Amay.

- [ ] **Step 2: Write importer metadata validation**

Implement a standard-library Python importer that fails before writing outputs unless there are 30 unique slugs and titles, every API item has a thumbnail URL, creator, license short name, and description URL, and the license is accepted:

```python
ALLOWED_LICENSES = {
    "Public domain",
    "CC0 1.0",
    "CC BY 2.0",
    "CC BY 3.0",
    "CC BY 4.0",
    "CC BY-SA 2.0",
    "CC BY-SA 3.0",
    "CC BY-SA 4.0",
}

def validate(records: list[dict], metadata: dict[str, dict]) -> None:
    if len(records) != 30:
        raise ValueError(f"expected 30 mosque photos, got {len(records)}")
    if len({record["slug"] for record in records}) != 30:
        raise ValueError("mosque photo slugs must be unique")
    for record in records:
        info = metadata[record["commons_title"]]
        if info["license"] not in ALLOWED_LICENSES:
            raise ValueError(f"unsupported license: {info['license']}")
        for key in ("thumb_url", "creator", "license", "description_url"):
            if not info.get(key):
                raise ValueError(f"{record['commons_title']} is missing {key}")
```

- [ ] **Step 3: Download deterministic 1280-pixel derivatives**

For each validated record, download `thumburl` to `launcher/app/src/main/res/drawable-nodpi/mosque_<slug>.jpg` through a temporary file and replace the destination only after a successful JPEG signature check:

```python
def download_jpeg(url: str, destination: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "AquaTVHome/2.7 mosque-gallery-import"})
    data = urllib.request.urlopen(request, timeout=30).read()
    if not data.startswith(b"\xff\xd8"):
        raise ValueError(f"not a JPEG: {url}")
    temporary = destination.with_suffix(".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)
```

Run: `python3 scripts/import-mosque-gallery.py`

Expected: 30 JPEGs, each sourced from the API's `thumburl` for `iiurlwidth=1280`; no original camera-resolution downloads.

- [ ] **Step 4: Generate Java metadata and Markdown attribution**

Generate `MosquePhotos.java` entries with escaped strings and static drawable IDs:

```java
static List<MosquePhoto> all() {
    return Arrays.asList(
            new MosquePhoto(R.drawable.mosque_istiqlal_jakarta,
                    "Masjid Istiqlal", "Jakarta", "AR El Syarif",
                    "CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Masjid_Istiqlal_Jakarta.jpg")
    );
}
```

Replace only the generated block between `<!-- mosque-gallery:start -->` and `<!-- mosque-gallery:end -->` in `THIRD_PARTY_NOTICES.md`. Each line must include mosque, location, creator, license, and direct Commons file-page link.

- [ ] **Step 5: Audit generated assets**

Run:

```bash
find launcher/app/src/main/res/drawable-nodpi -name 'mosque_*.jpg' | wc -l
du -ch launcher/app/src/main/res/drawable-nodpi/mosque_*.jpg | tail -1
rg -n 'new MosquePhoto' launcher/app/src/main/java/com/gathin/tvhome/MosquePhotos.java
```

Expected: 30 files, 30 Java entries, total gallery size at or below 10 MiB. Open a contact sheet and reject images that are portrait-oriented, badly cropped at 16:9, duplicate the same view, show prominent identifiable people, or have unreadable compression artifacts.

- [ ] **Step 6: Commit the source set**

```bash
git add docs/mosque-gallery-sources.json scripts/import-mosque-gallery.py \
  launcher/app/src/main/res/drawable-nodpi/mosque_*.jpg \
  launcher/app/src/main/java/com/gathin/tvhome/MosquePhotos.java THIRD_PARTY_NOTICES.md
git commit -m "assets: add Indonesian mosque screensaver gallery"
```

---

### Task 2: Gallery Model and Shuffled Deck

**Files:**

- Create: `launcher/app/src/main/java/com/gathin/tvhome/MosquePhoto.java`
- Create: `launcher/app/src/main/java/com/gathin/tvhome/MosqueRotation.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/MosqueRotationTest.java`
- Create: `launcher/app/src/test/java/com/gathin/tvhome/MosquePhotosTest.java`

**Interfaces:**

- Consumes: `MosquePhotos.all()` from Task 1.
- Produces: `MosquePhoto(int drawableRes, String mosque, String location, String creator, String license, String sourceUrl)` and `MosqueRotation.shuffled(List<T> source, T previous, Random random)`.

- [ ] **Step 1: Write failing shuffled-deck tests**

```java
@Test
public void shufflePlaysEveryPhotoOnce() {
    List<String> source = Arrays.asList("a", "b", "c", "d");
    List<String> deck = MosqueRotation.shuffled(source, null, new Random(4));
    assertEquals(4, deck.size());
    assertEquals(new HashSet<>(source), new HashSet<>(deck));
}

@Test
public void reshuffleDoesNotImmediatelyRepeatPrevious() {
    List<String> source = Arrays.asList("a", "b", "c");
    for (int seed = 0; seed < 50; seed++) {
        assertNotEquals("b", MosqueRotation.shuffled(source, "b", new Random(seed)).get(0));
    }
}
```

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `cd launcher && ./gradlew testDebugUnitTest --tests com.gathin.tvhome.MosqueRotationTest --no-daemon`

Expected: compilation failure because `MosqueRotation` does not exist.

- [ ] **Step 3: Implement the immutable model and shuffle helper**

```java
final class MosquePhoto {
    final int drawableRes;
    final String mosque, location, creator, license, sourceUrl;

    MosquePhoto(int drawableRes, String mosque, String location, String creator,
                String license, String sourceUrl) {
        this.drawableRes = drawableRes;
        this.mosque = mosque;
        this.location = location;
        this.creator = creator;
        this.license = license;
        this.sourceUrl = sourceUrl;
    }
}
```

```java
static <T> List<T> shuffled(List<T> source, T previous, Random random) {
    List<T> deck = new ArrayList<>(source);
    Collections.shuffle(deck, random);
    if (deck.size() > 1 && Objects.equals(previous, deck.get(0))) {
        Collections.swap(deck, 0, 1);
    }
    return deck;
}
```

- [ ] **Step 4: Add metadata contract tests**

```java
@Test
public void bundledGalleryHasThirtyCompleteUniqueEntries() {
    List<MosquePhoto> photos = MosquePhotos.all();
    assertEquals(30, photos.size());
    Set<Integer> drawables = new HashSet<>();
    Set<String> sources = new HashSet<>();
    for (MosquePhoto photo : photos) {
        assertTrue(photo.drawableRes != 0);
        assertFalse(photo.mosque.trim().isEmpty());
        assertFalse(photo.location.trim().isEmpty());
        assertFalse(photo.creator.trim().isEmpty());
        assertFalse(photo.license.trim().isEmpty());
        assertTrue(photo.sourceUrl.startsWith("https://commons.wikimedia.org/wiki/File:"));
        assertTrue(drawables.add(photo.drawableRes));
        assertTrue(sources.add(photo.sourceUrl));
    }
}
```

- [ ] **Step 5: Run model tests**

Run: `cd launcher && ./gradlew testDebugUnitTest --tests 'com.gathin.tvhome.Mosque*Test' --no-daemon`

Expected: PASS.

- [ ] **Step 6: Commit the model**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/MosquePhoto.java \
  launcher/app/src/main/java/com/gathin/tvhome/MosqueRotation.java \
  launcher/app/src/test/java/com/gathin/tvhome/MosqueRotationTest.java \
  launcher/app/src/test/java/com/gathin/tvhome/MosquePhotosTest.java
git commit -m "feat: add mosque gallery rotation model"
```

---

### Task 3: Screensaver Gallery Views and Player

**Files:**

- Create: `launcher/app/src/main/java/com/gathin/tvhome/MosqueGallery.java`
- Create: `launcher/app/src/main/res/drawable/dream_mosque_scrim.xml`
- Modify: `launcher/app/src/main/res/layout/dream_clock.xml:8`

**Interfaces:**

- Consumes: `MosquePhotos.all()` and `MosqueRotation.shuffled(...)`.
- Produces: `MosqueGallery(View root, List<MosquePhoto> photos, Random random)`, `void start()`, and `void stop()`.

- [ ] **Step 1: Add the two image layers, scrim, and caption block**

Insert before `dreamAmbient`:

```xml
<ImageView
    android:id="@+id/dreamMosqueCurrent"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:importantForAccessibility="no"
    android:scaleType="centerCrop"
    android:visibility="gone" />

<ImageView
    android:id="@+id/dreamMosqueNext"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:alpha="0"
    android:importantForAccessibility="no"
    android:scaleType="centerCrop"
    android:visibility="gone" />

<View
    android:id="@+id/dreamMosqueScrim"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/dream_mosque_scrim"
    android:visibility="gone" />
```

Add a right-aligned caption `LinearLayout` after `dreamContent`, with 36dp right and bottom margins. Give its three `TextView`s IDs `dreamMosqueName`, `dreamMosqueLocation`, and `dreamMosqueCredit`; use 22sp, 15sp, and 11sp respectively, Outfit font resources, text shadows, and `importantForAccessibility="no"`.

- [ ] **Step 2: Add the readability gradient**

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="90"
        android:startColor="#B8000000"
        android:centerColor="#26000000"
        android:endColor="#8A000000" />
</shape>
```

- [ ] **Step 3: Implement one-at-a-time background decoding**

`MosqueGallery` owns a main-thread `Handler`, a single-thread decoder, the two `ImageView`s, three caption views, and a deck index. Decode with:

```java
private Bitmap decode(MosquePhoto photo) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    options.inScaled = false;
    return BitmapFactory.decodeResource(resources, photo.drawableRes, options);
}
```

Catch `RuntimeException` and `OutOfMemoryError`, log `TvHome.MosqueGallery`, and call the next deck entry. Never decode on the main thread.

- [ ] **Step 4: Implement the 60-second lifecycle**

```java
private static final long INTERVAL_MS = 60_000L;
private static final long CROSSFADE_MS = 1_600L;

void start() {
    if (active) return;
    active = true;
    generation++;
    if (decoder == null || decoder.isShutdown()) {
        decoder = Executors.newSingleThreadExecutor();
    }
    showNext(generation);
}

void stop() {
    active = false;
    generation++;
    handler.removeCallbacksAndMessages(null);
    current.animate().cancel();
    next.animate().cancel();
    if (decoder != null) decoder.shutdownNow();
    clearImages();
}
```

After a successful first decode, display it immediately. For later entries, place the bitmap on `next`, fade `next` from 0 to 1 over 1,600ms, swap the two view roles, clear the outgoing drawable reference, then schedule the next selection with `handler.postDelayed(..., INTERVAL_MS)`.

- [ ] **Step 5: Update captions atomically with the incoming photo**

```java
private void setCaption(MosquePhoto photo) {
    name.setText(photo.mosque);
    location.setText(photo.location);
    credit.setText("Photo: " + photo.creator + " · " + photo.license + " · Wikimedia Commons");
}
```

Fade the caption out for 250ms and back in with the incoming image. Keep the full source URL in `MosquePhoto` and `THIRD_PARTY_NOTICES.md`; do not render a long URL on the TV.

- [ ] **Step 6: Compile resources and Java**

Run: `cd launcher && ./gradlew compileDebugJavaWithJavac processDebugResources --no-daemon`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit the player**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/MosqueGallery.java \
  launcher/app/src/main/res/drawable/dream_mosque_scrim.xml \
  launcher/app/src/main/res/layout/dream_clock.xml
git commit -m "feat: render rotating mosque screensaver photos"
```

---

### Task 4: ClockScene Integration and Regression Checks

**Files:**

- Modify: `launcher/app/src/main/java/com/gathin/tvhome/ClockScene.java:39-164`
- Test: `launcher/app/src/test/java/com/gathin/tvhome/MosqueRotationTest.java`

**Interfaces:**

- Consumes: `MosqueGallery` lifecycle from Task 3 and the existing `Config.clockWallpaper` setting.
- Produces: gallery playback in both `ClockDream` and `ClockActivity` when `dashboard_panels=true` and `clock_wallpaper=false`.

- [ ] **Step 1: Add explicit gallery selection state**

Add fields:

```java
private MosqueGallery mosqueGallery;
private boolean useMosqueGallery;
```

In `useHomeWallpaper(Context ctx)`, preserve the existing wallpaper branch and select the gallery only for the widgets build:

```java
if (!cfg.clockWallpaper && dashboardEnabled) {
    useMosqueGallery = true;
    mosqueGallery = new MosqueGallery(content.getRootView(), MosquePhotos.all(), random);
    return;
}
```

The `clock_glow=false` plain-black branch applies only when the gallery is unavailable; do not let it suppress the requested widgets gallery.

- [ ] **Step 2: Wire lifecycle without touching dashboard work**

In `start()`, call `mosqueGallery.start()` before scheduling the existing `tick`. In `stop()`, call `mosqueGallery.stop()` before `handler.removeCallbacksAndMessages(null)`. Preserve the dashboard repository, countdown, Adzan checks, wallpaper generation guard, and the existing 720p card-spacing calculation unchanged.

- [ ] **Step 3: Disable unnecessary glow animation during gallery playback**

Guard `shiftGlow()`:

```java
if (wallpaperShown || useMosqueGallery) return;
```

Keep the initial ambient gradient behind the gallery so a decode failure still has a valid fallback background; the photo and scrim cover it after the first successful decode.

- [ ] **Step 4: Run all JVM tests**

Run: `cd launcher && ./gradlew testDebugUnitTest --no-daemon`

Expected: BUILD SUCCESSFUL with all existing and new tests passing.

- [ ] **Step 5: Build both variants and verify feature isolation**

Run:

```bash
./build.sh all
unzip -l apks/glass-tv-widgets.apk | rg 'mosque_|dream_mosque'
unzip -l apks/glass-tv-fresh.apk | rg 'mosque_|dream_mosque'
```

Expected: the widgets APK contains all 30 photo assets and the fresh APK contains no packaged mosque photos after resource shrinking.

- [ ] **Step 6: Commit integration and APKs**

```bash
git add launcher/app/src/main/java/com/gathin/tvhome/ClockScene.java \
  apks/glass-tv-widgets.apk apks/aqua-tv-home-widgets.apk \
  apks/glass-tv-fresh.apk apks/aqua-tv-home-fresh.apk
git commit -m "feat: integrate mosque gallery with ClockDream"
```

---

### Task 5: Documentation and Connected-TV Verification

**Files:**

- Modify: `README.md:24`
- Modify: `README.md:73-80`
- Verify: `apks/glass-tv-widgets.apk`

**Interfaces:**

- Consumes: the completed APK and source attribution from Tasks 1–4.
- Produces: documented behavior and device evidence that the requested screensaver works at 720p.

- [ ] **Step 1: Document the delivered behavior**

Update the feature bullet and widgets mode description to state:

```markdown
- **Indonesian mosque gallery screensaver.** The widgets build rotates through 30 bundled Wikimedia Commons mosque photographs every minute, works offline, and keeps the clock, prayer, weather, and AQI cards visible. Each image includes its mosque, location, photographer, and license.
```

Mention that a configured `clock_wallpaper` still overrides the bundled gallery.

- [ ] **Step 2: Verify documentation formatting and repository diffs**

Run:

```bash
npx prettier --check README.md THIRD_PARTY_NOTICES.md docs/mosque-gallery-sources.json
git diff --check
git status --short
```

Expected: all formatting checks pass and only intended feature files/APKs remain modified.

- [ ] **Step 3: Install without clearing settings**

Run:

```bash
$HOME/Library/Android/sdk/platform-tools/adb -s 192.168.100.2:5555 install -r -d -g apks/glass-tv-widgets.apk
$HOME/Library/Android/sdk/platform-tools/adb -s 192.168.100.2:5555 shell settings put secure screensaver_components com.gathin.tvhome/.ClockDream
```

Expected: install reports `Success`; selected saver remains `com.gathin.tvhome/.ClockDream`.

- [ ] **Step 4: Start an accelerated visual test and restore settings**

Temporarily set the TV idle timeout to 15 seconds, start from Home, and inspect `dumpsys dreams`. Restore `screen_off_timeout` to `300000` in a shell trap. Capture screenshots immediately and after at least 65 seconds.

Expected in both captures: mosque photo, clock, prayer card, weather card, AQI card, and bottom-right caption all visible with no overlap. Expected after 65 seconds: a different mosque and matching caption.

- [ ] **Step 5: Check process health and lifecycle**

Dismiss the saver, start it again, and inspect:

```bash
adb -s 192.168.100.2:5555 shell dumpsys meminfo com.gathin.tvhome
adb -s 192.168.100.2:5555 logcat -d -s TvHome.MosqueGallery:E AndroidRuntime:E
```

Expected: no crash or decode error, no stale dream after dismissal, and bitmap memory consistent with one or two 1280×720 `RGB_565` images rather than the full 30-photo collection.

- [ ] **Step 6: Run the final quality gate**

Run:

```bash
cd launcher && ./gradlew testDebugUnitTest lintRelease --no-daemon
cd .. && ./build.sh glass-widgets
git diff --check
```

Expected: tests, lint, build, and diff checks all pass.

- [ ] **Step 7: Commit documentation and final verified APKs**

```bash
git add README.md THIRD_PARTY_NOTICES.md apks/glass-tv-widgets.apk apks/aqua-tv-home-widgets.apk
git commit -m "docs: describe mosque screensaver gallery"
```
