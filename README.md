# Kyudo Aim Helper — Android POC

An on-device training helper for kyudo. The phone stands on a tripod in front of the archer and:

- **Form check** — tracks your body live with MediaPipe Pose and checks:
  - arrow level
  - shoulders parallel to the arrow, and hips parallel to the shoulders (the five crosses)
  - bow arm straight
  - head turned to the mato
  - leg spread (ashibumi)

  It follows the stages of hassetsu, times your kai, and can speak short voice cues.
- **Shot log** — after each shot you tap where the arrow landed on the kasumi-mato, or press a miss (front, back, high, low). It shows your tally (○×), where your group is drifting, and form-based advice. It never tells you to "move your aim".
- **Shot review** — your sessions with hit map, average kai, the most common flags, and each shot's measurements.
- **Nerai & setup** — your sight reference (half moon / hangetsu by default), voice and camera settings, tolerances, and how to place the camera safely.

Everything runs offline on the phone. Sessions are saved in the app's private storage.

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
3. **Start practice session**. Shoot normally. The app moves through *Prepare → Uchiokoshi → Hikiwake → Kai → Zanshin*. It times kai and saves the shot about 1.5 s into zanshin.
4. Tap **Log result** and tap where the arrow hit (or a miss button). The result is attached to the shot the camera detected.
5. **Finish session & review** to see the summary.

You can also use **Shot log** on its own, without the camera, as a simple hit tracker.

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
  MainActivity.kt        navigation between the 5 screens
  AppViewModel.kt        sessions, settings, live pose → metrics → tracker
  VoiceCoach.kt          text-to-speech cues
  pose/PoseEngine.kt     CameraX analyzer → MediaPipe Pose Landmarker (live stream)
  pose/FormMetrics.kt    angles and form flags from the 33 landmarks
  pose/ShotTracker.kt    hassetsu state machine, kai timer, shot records
  pose/Landmarks.kt      landmark indices and geometry helpers
  data/                  models, JSON storage, statistics
  ui/                    Compose screens (Home, FormCheck, ShotLog, Review, Nerai)
app/src/test/            unit tests for the metric maths (run: ./gradlew test)
```

## Next steps (ideas)

- Record each shot as a video and replay it in slow motion with the skeleton overlay.
- Detect the release (tsurune) and the hit on the target by sound, so hits log themselves.
- A second phone at a safe spot near the target to log hits automatically.
- Export sessions as CSV, and link which form flags go with which misses over time.
