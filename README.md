# Meowdoku Solver Overlay — no Android Studio needed

Floating 🐱 Solve button that screenshots the game, solves on-device,
and floats the cats over the board for 10s (tap to dismiss).

No OpenCV, no NDK — pure Kotlin. Handles 8/9/10 grids.

## Get the APK (no Studio)

1. Go to github.com → New repository → upload this `MeowdokuOverlay` folder
   (web UI: Add file → Upload files, drag everything).
2. Open the repo → Actions tab → `Build debug APK` → Run workflow
   (auto-runs on push too).
3. When green, download artifact `meowdoku-solver-apk` → `app-debug.apk`.
4. Send to phone, install (allow unknown apps).

## Use

1. Open `Meowdoku Solver` app.
2. `1) Allow display over apps` → enable.
3. `2) Allow capture + start button` → allow. Floating 🐱 Solve appears.
4. Open Meowdoku, arrange board visible, tap 🐱 Solve.
5. Cats overlay the board for 10s. Tap overlay to hide early.

## Notes

- Screen-capture consent is per reboot; reopen solver app after restart.
- If overlay misaligns on your device, tell me your screen size and I'll tune `findBoard`.
- Solver logic is a port of `meowdoku_solver.py` in this folder's parent.
