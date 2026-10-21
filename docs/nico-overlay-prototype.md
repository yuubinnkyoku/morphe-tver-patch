# TVer 7.2.1 — Nico comment overlay prototype

Experimental, OFF by default. This first stage verifies transparent offline WebView overlay and playback-clock synchronization. It is not the complete NCOverlay or a faithful Niconico comment-art renderer.

## Implemented

- Bytecode hook in EpisodePersistentPlayerSurfaceKt: update lambda after the host null check, before p0 is overwritten. Pass the episode host ViewGroup and StateFlow to a bundled extension.
- onRelease hook destroys WebView when the episode view is released.
- Overlay uses local inline HTML (1920x1080 Canvas); no online scripts, third-party requests, protected-video access, DRM changes or FLAG_SECURE modifications.
- Samples PlaybackState every 200 ms (positionMs, phase, rate, isAd, isSeeking, seekGeneration) and interpolates timing in JavaScript.
- Shows looping test comments (scrolling, ue/shita, colors, size). Provides a JS function to accept Niconico v1-shaped thread arrays for a later stage; nothing calls it yet.
- WebView touch handling returns false to preserve TVer's player controls.

## Device test

1. In Morphe Expert mode, select "TVer: Nico comment overlay prototype (7.2.1)" on an original 7.2.1 (versionCode 535) APK. This patch is disabled by default.
2. First apply it alone. Open an episode. Look for the "Morphe Nico Overlay — TEST MODE" watermark and sample text.
3. Test pause/resume, seek, playback speed, rotation, switching episodes and player touch controls.
4. Diagnose with: adb logcat -s MorpheNicoOverlay
5. Then combine with the existing PiP patch; verify PiP behavior separately.

## Not implemented / unverified

- Real Niconico API, Jikkyo historic logs, title matching, settings and commenting
- Bundled @xpadev-net/niconicomments or faithful Flash/HTML5 comment art
- Tested rendering on protected SurfaceView, system PiP or Android hardware
- End-to-end Morphe patching/installing the produced .mpp

The injected Kotlin patch deliberately requires the TVer 7.2.1 method signature and register-count assumption. If these differ it will fail patching instead of silently modifying another method. The Compose/SurfaceControl z-order is still a hardware-test question.

## References

- https://github.com/Midra429/NCOverlay
- https://github.com/xpadev-net/niconicomments
- https://github.com/david419kr/niconico-yt-morphe-patches
- https://github.com/bakwudo/uyu
