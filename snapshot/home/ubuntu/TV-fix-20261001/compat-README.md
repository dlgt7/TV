# Android 7 / API 24 compatibility verification attempt

**Result: actual API 24 runtime verification was not completed. Zero harness assertions ran on Android.**

All installation, compilation, D8 conversion, and attempted emulator startup occurred in the existing `tv-build` Codespace. No repository files were changed for this harness.

## What succeeded

- Installed official Android emulator 37.2.12.0 (build 16428233) and `system-images;android-24;default;x86_64` revision 8 temporarily.
- Verified KVM access: `KVM (version 12) is installed and usable.` Access used `sudo setpriv --reuid=1000 --regid=1000 --groups=1000,109`, without changing host device permissions or group files.
- Compiled the standalone Java harness and converted the actual production `MediaMatcher.class`, `MediaIdentity.class`, and `MediaIdentity$Kind.class` with SDK build-tools 36.0.0 D8, `--min-api 24`.
- Production classes came from `/workspaces/TV-review-20261001/app/build/intermediates/javac/leanbackArm64_v8aDebug/compileLeanbackArm64_v8aDebugJavaWithJavac/classes`, after the parent's successful current-source Android build. This did not use the legacy `/workspaces/TV` checkout.
- Production class SHA-256:
  - MediaMatcher.class: `6d09baa4d760633d16a9cf177683094682950b985c50628fa1f6d114eb767b68`
  - MediaIdentity.class: `8f02169044308175e8c984180934218911fb1675c5a101ef8d2fa7a9fa347908`
  - Resulting classes.dex: `2cb600c9a825e6e6dd30081b3b860aad4fc35f942edc5505b4991148fb12b74d`

## Why runtime validation stopped

The standard headless emulator launch repeatedly restored a 6 GiB userdata setting despite the isolated AVD's smaller setting and refused to start. Exact observed diagnostic:

```text
FATAL        | Not enough space to create userdata partition. Available: 6006.45 MB at /workspaces/TV-fix-results-20261001/compat/avd/api24.avd, need 7372.80 MB.
```

A final launch explicitly selecting the existing 550 MiB userdata image and a read-only overlay exited after these lines, without exposing a device to adb:

```text
INFO         | Android emulator version 37.2.12.0 (build_id 16428233) (CL:N/A)
INFO         | Graphics backend: gfxstream
INFO         | Found systemPath /workspaces/android-sdk/system-images/android-24/default/x86_64/
```

`adb devices` remained empty; `timeout 45 adb -s emulator-5580 wait-for-device` timed out. No more environments were installed or retries attempted after the parent's final-attempt limit.

## Planned coverage and limits

The retained `CompatMediaMatcherHarness.java` is intended to run via `adb shell` and `dalvikvm`, never as a substitute host-JVM test. It exercises production static regex initialization, title/year/season matching, episode marker scanning, supplementary Han characters, null inputs, and MediaIdentity compatibility/equality. It also includes a historical `\\p{IsHan}` regex probe.

The harness intentionally excludes the full APK, native libraries, UI, Vod overloads, and app-dependent `Trans`; production `normalize()` would use its existing caught-Throwable fallback when `Trans` is unavailable. These remain coverage limitations even if this harness is run successfully later.

**D8 conversion success is not evidence that the code ran successfully on Android 7.** The parent's 198/198 Gradle unit tests and lint are separate checks.

## Cleanup

The isolated emulator/AVD and only the two newly installed SDK packages were removed after logs and small harness artifacts were retained. Pre-existing SDK packages, source/build directories, and the Codespace itself were preserved.
