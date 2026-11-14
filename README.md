# STABLEX — Precision. Stability. Performance.

Native, offline Android gaming-session utility for BGMI and PUBG Mobile.

## Status
Source prototype. An automatically built debug APK can be downloaded from GitHub Actions if the build succeeds. Device installation, game launch and thermal behavior still require testing on a physical phone. No claims of measured FPS improvement.

## Features in this build
- Dark Compose UI; Performance, Balanced, and Thermal Protection profiles stored locally with Jetpack DataStore
- BGMI/PUBG package presence and one-tap user-initiated game launch
- Optional DND with user authorization, pre-change policy journal and restoration on STOP or subsequent app opening
- System thermal status monitoring during a user-initiated session; 90-second recovery hysteresis
- Offline OS connectivity-type and display-refresh readout
- Ongoing session notification with STOP & RESTORE action
- No INTERNET permission, accounts, tracking, root, input injection or game file modification

## Not implemented
- Continuous background game autostart detection
- Overlay or game FPS collection
- CPU/GPU controls, frame-rate forcing, recoil, aim, or touch automation
- External ping, server latency/jitter, and direct FPS improvement

## Build
GitHub Actions > Build STABLEX APK > latest green run > Artifacts > STABLEX-APK > extract app-debug.apk. This is a debug/test artifact and not a signed production release.

If the repository is public and that is unintended, change Settings > General > Danger Zone > Change repository visibility > Private.
