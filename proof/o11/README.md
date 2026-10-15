# OutSystems 11 wrapper: proof of detection

Captured 2026-10-15 on the same Android 16 (API 36) emulator as the [Cordova proof](../README.md): `userdebug`, rooted with **Magisk 30.6**, Zygisk on. The app is **RotomRoot Sample** (`net.osacademy.dev.RotomRootSample`, versionCode 2). It was built by MABS 12 (cordova-android 14.0.1) from two O11 Mobile modules:

- **`RottomrootPlugin`**: the wrapper module. Its Extensibility Configurations pull `cordova-plugin-rotomroot` at the `v0.1.0` tag, and it exposes `CheckRottomrootPlugin`, `IsRooted` and `CheckRoot`, plus the structures `RootReport` and `RootChecks`.
- **`RotomRootSample`**: a demo app. Its `Home` screen calls `CheckRoot` when it opens and lists every signal. The time shown next to the verdict is when that check ran.

The APK was checked before install. It contains the plugin's JS module, `com.rotomroot.plugin.*` in its dex files, the `RotomRoot` feature in `config.xml`, and the root-app `<queries>` in the manifest.

- **Video:** [rotomroot-o11-proof.mp4](rotomroot-o11-proof.mp4). It's 1 min 13 s and records all three states.
- **Log:** [capture-log.txt](capture-log.txt). It lists the `adb`/`magisk` commands, the launch time for each run, and the on-screen text read through `uiautomator`.

| # | Device state | Verdict | Signals that fired | Screenshot |
|---|---|---|---|---|
| 1 | Rooted, app **not** on DenyList | **Rooted** | `su binary`, `which su`, `Root apps`, `Magisk / KSU`, `Debug build` | [01-o11-rooted-detected.png](01-o11-rooted-detected.png) |
| 2 | Rooted, Zygisk + **DenyList** on the app | **Rooted** | `Root apps`, `Debug build` | [02-o11-denylist-rootapps-only.png](02-o11-denylist-rootapps-only.png) |
| 3 | Rooted, DenyList + Magisk app removed (same effect as *Hide the Magisk app*) | Not rooted | `Debug build` (weak) | [03-o11-denylist-hidden-bypassed.png](03-o11-denylist-hidden-bypassed.png) |

These match the Cordova example app state for state. The wrapper adds no loss: whatever the native plugin can see reaches the OutSystems app unchanged. State 3 is the same known limit as before. `su -c id` still returns `uid=0(root)` from the host while the app sees nothing.
