# cordova-plugin-rotomroot

Cordova plugin that detects whether an Android device is rooted. No third-party dependencies: the checks are plain Java, run on Cordova's thread pool, and return a Promise.

> **Root detection on the device can be bypassed.** Tools such as Magisk DenyList, Zygisk + Shamiko or KernelSU hide every signal this plugin looks for. Use it to flag the common case or to adjust UX. For anything security-critical (payments, anti-fraud, DRM), verify device integrity on your server with the [Play Integrity API](https://developer.android.com/google/play/integrity).

## Install

```bash
cordova plugin add cordova-plugin-rotomroot
```

From a local checkout:

```bash
cordova plugin add /path/to/cordova-plugin-rotomroot
```

Requires `cordova-android` 12 or newer (minSdk 24). Android only.

## Usage

```js
document.addEventListener('deviceready', async () => {
    if (await RotomRoot.isRooted()) {
        // show a warning, limit features, report to your backend, ...
    }

    const report = await RotomRoot.check();
    // {
    //   isRooted: false,
    //   checks: { suBinary, suExec, rootApps, rwSystem, magisk, testKeys, dangerousProps, busybox }
    // }
});
```

TypeScript declarations ship in `types/index.d.ts` (`RotomRootPlugin`, `RootReport`, `RootChecks`).

### API

| Method | Resolves to |
|---|---|
| `RotomRoot.isRooted()` | `boolean`: true when any **strong** signal is set |
| `RotomRoot.check()` | `RootReport`: `isRooted` plus every individual check |

Both reject with an `Error` if the native side fails.

## Checks

| Key | Strength | What it looks for |
|---|---|---|
| `suBinary` | strong | an `su` file in about 20 well-known directories or in any `PATH` entry |
| `suExec` | strong | `which su` resolves |
| `rootApps` | strong | an installed root manager: Magisk (and forks), KernelSU, APatch, SuperSU, Superuser, KingRoot, ... |
| `rwSystem` | strong | `/system`, `/vendor`, `/product`, ... (or `/` on system-as-root) mounted `rw` in `/proc/self/mounts` |
| `magisk` | strong | Magisk / KernelSU / APatch files (`/debug_ramdisk/magisk`, `/sbin/.magisk`, `/data/adb/...`, ...) or their mounts (source `magisk`/`KSU`/`APatch`, or a mount under `/.magisk`) |
| `testKeys` | weak | `Build.TAGS` contains `test-keys` |
| `dangerousProps` | weak | `userdebug`/`eng` build, `ro.debuggable=1` or `ro.secure=0` |
| `busybox` | weak | a `busybox` binary |

Weak signals show up on some custom ROMs, OEM builds and emulators, so they appear in the report but never set `isRooted` by themselves. Combine them however your app needs.

### Notes

- **Package visibility (Android 11+).** `plugin.xml` declares each root-manager package in a `<queries>` block, so `rootApps` works without `QUERY_ALL_PACKAGES`, which needs Play policy approval. If you add packages to `RootChecker.ROOT_PACKAGES`, add them to `plugin.xml` too.
- **SELinux hides things from apps.** On recent Android versions an app can't read `ro.debuggable`/`ro.secure`, or stat an `su` that's only usable from `adb shell` (for example `/system/xbin/su` on `userdebug` emulator images). The plugin reports what an *app* can see. An emulator image like that reports `isRooted: false` with `dangerousProps: true`.
- Shell commands (`which`, `getprop`) time out after 2 seconds and never block the UI thread.

## Tested results

Android 16 (API 36) Google APIs x86_64 emulator, `userdebug` image, example app:

| Device state | `isRooted` | Signals that fired |
|---|---|---|
| Stock image | `false` | `dangerousProps` |
| Magisk 26.4 / 30.6 (via [rootAVD](https://gitlab.com/newbit/rootAVD)) | **`true`** | `suBinary`, `suExec`, `rootApps`, `magisk`, `dangerousProps` |
| Magisk 30.6 + Zygisk + DenyList on the app | **`true`** | `rootApps`, `dangerousProps` |
| ... and the Magisk app hidden / removed | `false` | `dangerousProps` |

The last row is the expected bypass: DenyList removes Magisk's mounts and `su` from the app's process, and a hidden Magisk app has a random package name. That's why integrity checks that matter should be verified on a server.

## OutSystems 11

An O11 Mobile plugin module, **`RottomrootPlugin`**, wraps this plugin. It was built with the Service Studio MCP and checked on a MABS 12 build (see [proof/o11](proof/o11/README.md)).

**Extensibility Configurations.** O11 uses the Cordova-based schema. Service Studio flags it as "deprecated", but the universal `buildConfigurations` schema is ODC-only: an O11 MABS 12 build ignores it and leaves the plugin out.

```json
{
    "plugin": {
        "url": "https://github.com/maspandd/cordova-plugin-rotomroot.git#v0.1.0"
    }
}
```

**Public client actions** follow the Plugin template's conventions: outputs `Success` + `Error`, using `CommonPlugin.Error`.

| Action | Outputs | Notes |
|---|---|---|
| `CheckRottomrootPlugin` | `IsAvailable`, `Error` | `window.RotomRoot` is defined in the native app |
| `IsRooted` | `IsRooted`, `Success`, `Error` | Calls `RotomRoot.isRooted()` |
| `CheckRoot` | `Report` (`RootReport`), `Success`, `Error` | Calls `RotomRoot.check()`; `Report.Checks` holds the 8 signals |

Error codes: `1` cordova not defined, `2` plugin not loaded (the native build doesn't include it), `3` the native check failed.

**Using it in an app:**
1. Add a dependency on `RottomrootPlugin` (`CheckRoot`, `RootReport`, `RootChecks`).
2. Call `CheckRoot` from a screen's **On Ready** or a button, and check `Success` before reading `Report`.
3. Generate a new native build. Changes to extensibility only take effect in a new build, and consumers marked "outdated" need a refresh and republish first.

## Development

```bash
npm install
npm test          # jest (JS wrapper) + plain-JVM tests for RootChecker
```

`RootChecker` reaches the device only through the `RootChecker.Env` interface, so its logic is tested on a plain JVM with a fake environment (`tests/java`). `RotomRoot.AndroidEnv` is the real implementation.

### Example app

`example/` is a small Cordova app that shows the report on screen and logs it to logcat (`RotomRoot report {...}`):

```bash
cd example
cordova platform add android
cordova plugin add .. --link
cordova run android
```

## License

MIT
