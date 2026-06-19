# FlightBox

A local-only Android video player for kids. Videos come from a folder you
pick on the device. No internet, no accounts, no telemetry.

## v2 player design

The player is **landscape-only**. There is no portrait-mode player, no
rotation toggle, and no system back into a "second screen" mode.

Layout:

```
+-------------------------------------+
|                                     |
|                                     |
|        TOP 2/3 — VIDEO              |
|        (current selection)          |
|                                     |
|                                     |
+-------------------------------------+
|                                     |
|  BOTTOM 1/3 — THUMBNAIL STRIP       |
|  (horizontal swipe to choose)       |
|                                     |
+-------------------------------------+
```

Interaction model (YouTube-Kids style):

1. The bottom strip starts visible.
2. Tapping the **top 2/3** toggles the strip: visible ↔ hidden.
3. **5 seconds** after the last touch anywhere on the screen, the strip
   auto-hides, so the top area gets the whole screen for the video.
4. Tapping a thumbnail in the strip switches the current video.
5. The back-arrow button (top-left of the strip) is the only way to
   leave the player. The system back key does the same.

The MainActivity (folder picker) remains **portrait** because folder
selection is a portrait task. Tapping the **Play** button on
MainActivity launches the player in landscape.

## Status

| Step | Feature | Status |
|---|---|---|
| 0 | Toolchain (Gradle, AGP, SDK, theme, app icon) | done |
| 1 | SAF folder picker + URI persistence | done |
| 2 | Video scanning (DocumentFile tree walk) | done |
| 3 | Landscape-only player (top 2/3 video, bottom 1/3 strip) | done |
| 4 | Tap-to-toggle strip + 5 s auto-hide | done |
| 5 | Thumbnail extraction + cache | done |
| 6 | Polish (immersive mode, back button) | done |

## Environment (verified)

- Android Studio **2026.1.1**
- Android SDK: API 36 / build-tools 36.1.0 + 37.0.0
- JDK 17 (system) or JDK 21 (JBR bundled with Android Studio)

## Build versions pinned in this project

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.9.2 |
| Gradle wrapper | 8.11.1 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 (Android 8.0) |
| Java source/target | 17 |

To open the project:

1. Unzip FlightBoxKids_20260614.zip into a folder. The zip
   intentionally does **not** include `gradle-wrapper.jar`,
   `gradlew`, or `gradlew.bat` (Gmail blocks any zip that contains
   a JAR or a shell script). You need to restore these before
   Android Studio can build:
   - In the project root, run:
     - macOS / Linux:
       ```
       mv gradlew.sh.txt gradlew
       chmod +x gradlew
       ```
     - Windows (PowerShell):
       ```
       ren gradlew.bat.txt gradlew.bat
       ```
2. Launch Android Studio.
3. **File > Open** and pick the project root (the folder containing
   `settings.gradle.kts`).
4. Wait for Gradle sync to finish. The first sync downloads ~500 MB of
   Gradle + AGP + dependencies — give it a few minutes.
5. Pick **Run > Run 'app'**. Pick your physical device from the device
   chooser. You should see "FlightBox" on a dark blue screen.
6. Tap **Choose Video Folder**, grant access to a folder containing
   video files, then tap **Play** — the device rotates to landscape
   and the player opens.

### If Gradle sync complains about JDK

Gradle needs a JDK 17 or newer. The default in Android Studio 2026.1.1
is the bundled JBR (JDK 21).

- **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**
- **Gradle JDK**: pick "jbr-21" (bundled) or "JDK 17" (your system JDK).
