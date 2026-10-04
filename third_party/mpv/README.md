# FongMi MPV native bundle

`mpv-android-ffmpeg9-native.zip` comes from the successful
`FongMi/mpv-android` workflow run `31346106984` at commit `99a60ad214`.

- FFmpeg ref: `release-9.0-fongmi`
- Architectures: `arm64-v8a`, `armeabi-v7a`
- Archive SHA-256: `d3f22c64ab90a1476b489c2b4f06de8296672497f2998161efb4d906f9a4a66c`
- The app keeps `classes.jar` and assets from
  `app/libs/mpv-android-lib-v0.0.3.aar`.
- The extended JNI bridge is rebuilt for FFmpeg 9, as described below.
- All other native libraries are taken from the FFmpeg 9 build.

The `mpv` Gradle module assembles these inputs without committing another
large derived AAR.

## Extended JNI bridge

The original AAR's `libplayer.so` imports FFmpeg 8 symbols such as
`av_jni_set_java_vm@LIBAVCODEC_62`. The FFmpeg 9 bundle exports
`av_jni_set_java_vm@LIBAVCODEC_63`, so mixing these binaries fails during
`MPVLib` initialization on Android. Rewriting symbol versions is insufficient:
the fast thumbnail code also accesses FFmpeg structures whose ABI changes
between major versions.

`mpv-android-ffmpeg9-bridge.zip` contains only the rebuilt `libplayer.so` for
`arm64-v8a` and `armeabi-v7a`. It preserves all 23 original JNI entry points,
including node properties, node commands, fast thumbnails, and thumbnail cache
management. The stock bridge in the runtime archive lacks several of these
APIs and is excluded by `mpv/build.gradle`.

- Bridge source: [Aryan447/mpvlibAndroid v0.0.3](https://github.com/Aryan447/mpvlibAndroid/tree/5d658fe77b607db7f6e4141770fe7861c888b1e0/app/src/main/jni),
  commit `5d658fe77b607db7f6e4141770fe7861c888b1e0`.
  The `.cpp` and `.h` files in `bridge/` are unchanged except for trailing
  whitespace normalization; the local Android
  makefiles link them against the bundled runtime. See `bridge/LICENSE` (MIT).
- FFmpeg 9 public headers: `FongMi/FFmpeg` commit
  `03d9533176e98bb9fbf569c1f34968e73e948dd9` on `release-9.0-fongmi`,
  preceding the runtime build; libavcodec/libavformat 63, libavutil 61,
  libswscale 10.
- MPV public header: `FongMi/mpv` commit
  `cca559b41ceb0bb7731cf6ef2e1f33276cd30c42`, matching the runtime's embedded
  `v0.41.0-dev-gcca559b41` version.
- Toolchain: official Android NDK r29 (`29.0.14206865`), Android API 24,
  shared libc++, with 16 KiB page alignment for arm64.
- Bridge archive SHA-256:
  `29b1ef4c8d0b48475c1b5dba464d46157d7ec2e13ce913a67fcca93e4b2c20d6`.

Rebuild on Linux with Python 3, Make, tar, and NDK r29:

```bash
python3 third_party/mpv/build_bridge.py --ndk /path/to/android-ndk-r29
python3 third_party/mpv/verify_bridge.py
```

The build script verifies the downloaded header sources with SHA-256,
configures headers for each target ABI, and compiles only the JNI bridge.
Intermediate files go under `mpv/build/native-bridge/`. The normal Gradle build
uses the checked-in bridge archive and does not download or compile native code.

`verify_bridge.py` checks both architectures against the original AAR's JNI
exports and the bundled runtime's versioned media symbols. As a regression
check, the original AAR bridge is rejected with its unresolved FFmpeg 8 imports.
These ELF checks complement the Android playback and thumbnail smoke tests;
they do not replace device testing.
