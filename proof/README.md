# RotomRoot proof of detection

Captured 2026-10-15 on an Android 16 (API 36) emulator: `sdk_gphone64_x86_64`, Google APIs x86_64, `userdebug` / `dev-keys`. The device was rooted with **Magisk 30.6** via [rootAVD](https://gitlab.com/newbit/rootAVD), with Zygisk enabled. Each screenshot is the `example/` app calling `RotomRoot.check()`. The time next to the verdict is when that check ran.

- **Video:** [rotomroot-proof.mp4](rotomroot-proof.mp4). A 2 min 10 s screen recording of steps 1–4 below, made with `adb shell screenrecord` on the device.
- **Log:** [capture-log.txt](capture-log.txt). Timestamped commands run from the host, with the plugin's JSON report for each state taken from logcat.

| # | Device state | Verdict | Signals that fired | Screenshot |
|---|---|---|---|---|
| 0 | Stock image, no Magisk (captured earlier, 13:11) | Not rooted | `dangerousProps` (weak, from the userdebug image) | [00-stock-not-rooted.png](00-stock-not-rooted.png) |
| 1 | Magisk 30.6 installed: *Installed 30.6, Zygisk Yes, Ramdisk Yes* | — | — | [01-magisk-installed.png](01-magisk-installed.png) |
| 2 | Rooted, example app **not** on DenyList | **Rooted** | `suBinary`, `suExec`, `rootApps`, `magisk`, `dangerousProps` | [02-rooted-detected.png](02-rooted-detected.png) |
| 3 | Rooted, Zygisk + **DenyList** on the example app | **Rooted** | `rootApps`, `dangerousProps` | [03-denylist-rootapps-only.png](03-denylist-rootapps-only.png) |
| 4 | Rooted, DenyList + Magisk app removed (same effect as Magisk's *Hide the Magisk app*) | Not rooted | `dangerousProps` | [04-denylist-hidden-bypassed.png](04-denylist-hidden-bypassed.png) |

## What it shows

- **Real root is detected.** With Magisk active, four independent strong signals fire (state 2).
- **DenyList removes most signals.** Magisk unmounts `su` and its `/debug_ramdisk` mounts from the app's process, so only the installed Magisk app gives it away (state 3).
- **A fully hidden root is not detected.** In state 4 `su -c id` still returns `uid=0(root)` from the host (see the log), but the app sees nothing. This is the known limit of checks that run on the device, and why security-critical decisions should rely on the Play Integrity API verified on a server.

The "Requires additional setup" dialog in screenshot 1 is Magisk's own prompt. It was left open, because accepting it reboots the device.

## Reproduce

Setup steps are in the main [README](../README.md#tested-results). The capture drove the emulator with `adb`:

```
magisk --denylist rm|add com.rotomroot.example     # toggle DenyList
pm uninstall com.topjohnwu.magisk                   # simulate a hidden Magisk app
am start -n com.rotomroot.example/.MainActivity     # run the check
```
