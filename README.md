# Kyudo Aim Helper — Android POC

[![Android CI](https://github.com/G2454/KyudoAim/actions/workflows/android.yml/badge.svg)](https://github.com/G2454/KyudoAim/actions/workflows/android.yml)
[![Device tests](https://github.com/G2454/KyudoAim/actions/workflows/device-tests.yml/badge.svg)](https://github.com/G2454/KyudoAim/actions/workflows/device-tests.yml)
[![README screenshots](https://github.com/G2454/KyudoAim/actions/workflows/screenshots.yml/badge.svg)](https://github.com/G2454/KyudoAim/actions/workflows/screenshots.yml)

An on-device training helper for kyudo. The phone stands on a tripod in front of the archer and:

- **Form check** — tracks your body live with MediaPipe Pose and checks:
  - arrow level
  - shoulders parallel to the arrow, and hips parallel to the shoulders (the five crosses)
  - bow arm straight
  - head turned to the mato
  - leg spread (ashibumi)

  It follows the stages of hassetsu, times your kai, and can speak short voice cues.
- **Shot log** — after each shot you tap where the arrow landed on the kasumi-mato, or press a miss (front, back, high, low). It shows your tally (○×), where your group is drifting, and form-based advice. It never tells you to "move your aim".
- **Shot review** — your sessions with hit map, average kai and the most common flags. Tap a shot to watch its **video step by step**: a timeline of the five steps (Prepare, Uchiokoshi, Hikiwake, Kai, Hanare · Zanshin), slow motion (0.25× / 0.5×), a short explanation of each step and what the app measured in that shot. **Save to gallery** (Movies/KyudoAim) or **Share** the video.
- **Guide** — a short swipeable guide opens on first launch, and again from the info buttons on Home, Form check and Nerai & setup.
- **Nerai & setup** — your sight reference (half moon / hangetsu by default), voice and camera settings, tolerances, **Show kanji** (off by default: Japanese terms appear in romaji with English meanings), **Record shot videos**, and how to place the camera safely.

Everything runs offline on the phone. Sessions are saved in the app's private storage.

## Screenshots

| Home | Form check | Shot log | Shot review |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01-home.png" width="190" alt="Home screen"> | <img src="docs/screenshots/02-form-check.png" width="190" alt="Form check with pose overlay"> | <img src="docs/screenshots/03-shot-log.png" width="190" alt="Shot log with hit map"> | <img src="docs/screenshots/04-review.png" width="190" alt="Session review"> |

| Shot, step by step | Guide | Nerai & setup |
|:---:|:---:|:---:|
| <img src="docs/screenshots/06-shot-steps.png" width="190" alt="One shot broken into steps with explanations"> | <img src="docs/screenshots/07-guide.png" width="190" alt="How-to guide"> | <img src="docs/screenshots/05-nerai.png" width="190" alt="Nerai and setup"> |

These images are made automatically from demo data (see [Screenshots](#making-the-screenshots) below). The form-check one shows a synthetic pose over a plain backdrop, and the step-by-step one has no video, because the screenshots are rendered without a camera.

## Build and install

1. Install **Android Studio** (Ladybug 2024.2 or newer) with JDK 17+.
2. Open this folder (`KyudoAim`) with **File › Open**. Let Gradle sync. The first build downloads the dependencies and the MediaPipe pose model (~9 MB) into `app/src/main/assets/`.
3. On your phone, turn on **Developer options → USB debugging**, plug it in, and press **Run**.
   Or use **Build › Build App Bundle(s) / APK(s) › Build APK(s)** and copy `app/build/outputs/apk/debug/app-debug.apk` to the phone.

Needs Android 8.0 (API 26) or newer. A mid-range phone from the last few years runs the tracking at 15–30 fps.

If Android Studio offers to update the Gradle plugin or library versions, accepting is fine.

## How to use it in the dojo

1. **Nerai & setup**: pick your sight reference, check the tolerances.
2. Put the phone on a tripod **3–4 m in front of your shai**, at chest height, facing you, square to the shooting line. Your whole body and the bow must be in frame. Never put it on the yamichi or near the azuchi.
3. **Start practice session**. Shoot normally. The app moves through *Prepare → Uchiokoshi → Hikiwake → Kai → Hanare · Zanshin*. It times kai and saves the shot about 1.5 s into zanshin. While **REC** shows, the camera is filming; each shot becomes its own short clip.
4. Tap **Log result** and tap where the arrow hit (or a miss button). The result is attached to the shot the camera detected.
5. **Finish session & review**, then tap a shot to watch it step by step.

### About the shot videos

- The camera records continuously while Form check is open. When a shot ends, that recording is trimmed to the shot (from about 5 s before the bow goes up to the end of zanshin) and the rest is deleted. Long waits between shots are dropped every 90 s.
- Videos have no sound (no microphone permission) and are 720p when the phone supports it.
- They are kept in the app's private storage and deleted with their session. **Save to gallery** copies one to Movies/KyudoAim (Android 10+; on older versions it opens the share sheet). **Share** sends it to any app.
- Some phones can't preview, analyse and record at the same time. Then Form check says so and keeps tracking without video.

You can also use **Shot log** on its own, without the camera, as a simple hit tracker.

## Tests and CI

### What is tested

| Layer | Where | Runs on | What it checks |
|---|---|---|---|
| Logic | `app/src/test/.../pose`, `.../data` | JVM | Angles and flags, including mirror and left-handed setups and missing landmarks. Stage detection with tracking noise, dropouts, back-to-back shots and timeouts. Statistics, terms, and saving and loading, including damaged and old files. |
| ViewModel | `AppViewModelTest` | Robolectric | Sessions, results, undo, deleting, restart persistence, attaching and trimming shot videos, and a full simulated shot from camera frames to a saved shot. |
| Screens | `ui/ScreensBehaviourTest` | Robolectric | Tapping the target, miss buttons, undo, the guide, home navigation, settings rows, opening a shot, and the step-by-step view. |
| Quality rules | `quality/` | JVM / Robolectric | Romaji mode shows no Japanese script on any screen. Every tappable element is at least 44 dp. Every kanji in the code exists in the bundled font. |
| Screenshots | `screenshots/ScreenshotTest` | Robolectric | Every screen renders, and makes the README images. |
| Device | `app/src/androidTest` | Emulator / phone | App launch and every screen, the camera screen with the real camera, pose model and recorder, video trimming on a real MP4, saving to the gallery, and sharing. |

### Running them

```bash
./gradlew testDebugUnitTest        # all JVM + Robolectric tests (no phone needed)
./gradlew koverHtmlReportDebug     # coverage report: app/build/reports/kover/htmlDebug/index.html
./gradlew lintDebug                # Android Lint: app/build/reports/lint-results-debug.html
./gradlew connectedDebugAndroidTest  # device tests, with an emulator running or a phone plugged in
bash ci/android-checks.sh          # everything CI checks, in order
```

### GitHub Actions

Workflows are in `.github/workflows/`. The steps live in `ci/`, so changing them doesn't need a workflow edit.

- **Android CI** (`android.yml`) runs on every push to `main`, on pull requests, and by hand. It runs all JVM tests with coverage, builds the debug APK, runs Android Lint, and checks the minimum coverage (60 % of lines; raise it in `app/build.gradle.kts` as tests grow). The coverage table is on the run's summary page. The APK is under **Artifacts → kyudo-aim-debug-apk**, and the test, coverage and lint reports are under **reports**.
- **Device tests** (`device-tests.yml`) runs the instrumented tests on an Android 11 emulator on every push to `main`, or by hand.
- **README screenshots** (`screenshots.yml`) runs when app code changes on `main`, or by hand (**Actions → README screenshots → Run workflow**). It re-renders the screenshots and commits them to `docs/screenshots/` if they changed.

If a run fails, compiler errors, failed tests and Gradle errors show up as annotations on the run page.

### Making the screenshots

The screenshots come from `app/src/test/.../screenshots/ScreenshotTest.kt`. It renders each screen with the fixed demo data in `DemoData.kt` using [Roborazzi](https://github.com/takahirom/roborazzi).

```bash
./scripts/screenshots.sh              # macOS / Linux: all screens
./scripts/screenshots.sh formCheck    # just one screen
```

```powershell
.\scripts\screenshots.ps1             # Windows
```

The PNGs are saved to `docs/screenshots/`. To add a screen, add a test method that calls `shoot("06-name") { ... }` and a column in the table above.

## How the measurements work (and their limits)

All of these are 2D estimates from one camera facing the archer. They are good for spotting *changes* and *habits*, not exact angles.

| Check | How it's measured | Default |
|---|---|---|
| Arrow level | line from draw wrist to bow wrist | ±4° |
| Shoulders ∥ arrow | shoulder line minus arrow line | ±4° |
| Hips ∥ shoulders | hip line minus shoulder line | ±6° |
| Bow arm | elbow angle on the bow side | ≥ 160° |
| Head turn | nose offset from the ear midpoint (rough estimate) | ≥ 50 % |
| Ashibumi | angle between the legs, hip centre to ankles | 55–65° |
| Kai | time the draw hand stays still at full draw | ≥ 3 s |

The bow hand is found automatically (the wrist farther from the head), so the mirrored front camera also works.

Known limits:
- A hakama hides the legs, so the ashibumi and hip readings can be missing or noisy.
- The phone can't see your actual nerai, which is what your right eye sees. The app works on the form causes of misses instead.
- The stage detection is heuristic. If it misses stages, tune the thresholds in `pose/ShotTracker.kt` (`isKaiPose`, release speed) using your own footage.

Ask your sensei to confirm the tolerances. They are starting points.

## Code map

```
app/src/main/java/com/kyudo/aimhelper/
  MainActivity.kt        navigation, first-launch guide, kanji setting
  AppViewModel.kt        sessions, settings, live pose → metrics → tracker
  VoiceCoach.kt          text-to-speech cues
  pose/PoseEngine.kt     CameraX analyzer → MediaPipe Pose Landmarker (live stream)
  pose/FormMetrics.kt    angles and form flags from the 33 landmarks
  pose/ShotTracker.kt    hassetsu state machine, kai timer, shot records
  pose/Landmarks.kt      landmark indices and geometry helpers
  video/ShotRecorder.kt  CameraX recording, cut into one clip per shot
  video/VideoTools.kt    trim a clip, save to gallery, share
  data/                  models, JSON storage, statistics, Terms (romaji/kanji + step explanations)
  ui/                    Compose screens (Home, FormCheck, ShotLog, Review, ShotDetail, Guide, Nerai)
app/src/test/            JVM + Robolectric tests: logic, ViewModel, screens, quality rules, screenshots
app/src/androidTest/     device tests (emulator / phone)
scripts/                 screenshot scripts (bash + PowerShell)
.github/workflows/       CI (tests + APK) and README screenshots
ci/                      CI scripts (checks, device tests, coverage, annotations) and a copy of the workflows
```

## Credits

Headings and kanji use **Shippori Mincho B1** by the Shippori Mincho Project Authors, under the SIL Open Font License 1.1. The app bundles a subset with only the characters it uses. License: `third_party/fonts/ShipporiMinchoB1-OFL.txt`.

## Next steps (ideas)

- Draw the skeleton over the shot video in Shot review.
- Detect the release (tsurune) and the hit on the target by sound, so hits log themselves.
- A second phone at a safe spot near the target to log hits automatically.
- Export sessions as CSV, and link which form flags go with which misses over time.
