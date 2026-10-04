# libass Android runtime

The `subtitle-ass` module downloads `io.github.peerless2012:ass-kt:0.5.1` from Maven
Central and verifies its SHA-256 (`1051212faf98ef956e06992b002d43cfdc81b6c60dcd32662e8d3ff52d584d65`).
It uses the existing MPV C++ runtime instead of packaging a duplicate copy.

The Java playback, dual track selection, font attachment extraction and rendering
integration under `player/subtitle` and `player/track` are implemented in this app.
No unpublished FongMi AAR or reconstructed proprietary API is required.

- Android wrapper (MIT): https://github.com/peerless2012/libass-android
- Native build sources and dependency licenses: https://github.com/peerless2012/libass-cmake
- libass (ISC): https://github.com/libass/libass
- Exact published wrapper sources: https://repo.maven.apache.org/maven2/io/github/peerless2012/ass-kt/0.5.1/ass-kt-0.5.1-sources.jar

The runtime includes libass and its font/shaping dependencies. Their original
licenses apply; native sources/build instructions are provided by the linked
projects. Rebuild or replace the Maven artifact through `subtitle-ass/build.gradle`,
updating its pinned checksum when deliberately upgrading.
