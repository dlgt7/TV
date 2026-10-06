# FongMi MPV native bundle

`mpv-android-ffmpeg9-native.zip` comes from the successful
`FongMi/mpv-android` workflow run `31346106984` at commit `99a60ad214`.

- FFmpeg ref: `release-9.0-fongmi`
- Architectures: `arm64-v8a`, `armeabi-v7a`
- Archive SHA-256: `d3f22c64ab90a1476b489c2b4f06de8296672497f2998161efb4d906f9a4a66c`
- The app keeps the compatible `classes.jar`, assets, and extended
  `libplayer.so` JNI bridge from `app/libs/mpv-android-lib-v0.0.3.aar`.
- All other native libraries are taken from the FFmpeg 9 build.

The `mpv` Gradle module assembles these inputs without committing another
large derived AAR.
