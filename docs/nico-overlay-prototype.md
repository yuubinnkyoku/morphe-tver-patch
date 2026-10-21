# TVer 7.2.1 — Nico comment overlay prototype

Experimental, OFF by default. This first stage verifies transparent offline WebView overlay and playback-clock synchronization. It is not the complete NCOverlay or a faithful Niconico comment-art renderer.

## Implemented

- Bytecode hook in EpisodePersistentPlayerSurfaceKt: update lambda after the host null check, before p0 is overwritten. Pass the episode host ViewGroup and StateFlow to a bundled extension.
- onRelease hook destroys WebView when the episode view is released.
- Overlay uses local inline HTML (1920x1080 Canvas); no online scripts, third-party requests, protected-video access, DRM changes or FLAG_SECURE modifications.
- Samples PlaybackState every 200 ms (positionMs, phase, rate, isAd, isSeeking, seekGeneration) and interpolates timing in JavaScript.
- Shows looping test comments (scrolling, ue/shita, colors, size). Provides a JS function to accept Niconico v1-shaped thread arrays for a later stage; nothing calls it yet.
- WebView touch handling returns false to preserve TVer's player controls.

## Verified prototype behavior and clock regression

- The initial experimental .mpp was successfully applied to TVer 7.2.1 on Android 16, and sample comments were visible above video. This validates the basic patching and WebView stacking path on the tested device.
- The initial clock implementation visibly jerked: comments shifted right in small increments and then jumped left when TVer's reported position advanced.
- Commit `8c7e64f` fixes this in source: repeated stale StateFlow position samples no longer reset the JavaScript clock, and real position changes use monotonic correction with explicit seek handling.
- Run `node scripts/test-nico-clock.mjs` to test repeated stale samples, fresh samples, pause/resume, 2x speed, seeks and ad masking. The Release workflow runs this check before semantic-release.
- **This clock fix has not yet been revalidated on-device.** Repatch with a prerelease containing `8c7e64f` and compare comment movement against the original prototype.

## TVer's in-app Morphe settings (experimental)

The Nico overlay patch now depends on the PiP patch, which supplies TVer's existing `Morphe設定` entry. In TVer's Settings, open `Morphe設定`. The existing settings dialog scrolls and includes:

- `コメント表示を有効にする`: show/hide test comments without repatching (default ON).
- `動画上にFPSを表示する`: show/hide the small live FPS badge (default ON).
- `FPS計測ログを表示・コピー`: inspect the recent FPS statistics and copy all of them to the clipboard.

The last twelve 5-second samples are saved in SharedPreferences after each window so they are readable in settings **even after leaving playback or restarting TVer**. Logging continues when the FPS badge is hidden. Turning off comments stops their Canvas rendering; the frame-timing probe remains running. The video HUD itself still offers tap-to-show/copy and long-press copy.

**Scope:** These are separate runtime toggles for the experimental comment view and FPS HUD, not a Morphe patch selection toggle. To remove the injected code entirely, disable the Nico overlay patch when repatching.

## Device test

1. In Morphe Expert mode, select "TVer: Nico comment overlay prototype (7.2.1)" on an original 7.2.1 (versionCode 535) APK. This patch is disabled by default.
2. First apply it alone. Open an episode. Look for the "Morphe Nico Overlay — TEST MODE" watermark and sample text.
3. Test pause/resume, seek, playback speed, rotation, switching episodes and player touch controls.
4. Diagnose with: adb logcat -s MorpheNicoOverlay
5. Then combine with the existing PiP patch; verify PiP behavior separately.

## Not implemented / unverified

- Real Niconico API, Jikkyo historic logs, title matching, settings and commenting
- Bundled @xpadev-net/niconicomments or faithful Flash/HTML5 comment art
- Cross-device secure SurfaceView composition and Android system PiP overlay rendering are unverified
- On-device smoothness of the updated playback clock is not yet verified

The injected Kotlin patch deliberately requires the TVer 7.2.1 method signature and register-count assumption. If these differ it will fail patching instead of silently modifying another method. The Compose/SurfaceControl z-order is still a hardware-test question.

## References

- https://github.com/Midra429/NCOverlay
- https://github.com/xpadev-net/niconicomments
- https://github.com/david419kr/niconico-yt-morphe-patches
- https://github.com/bakwudo/uyu

## Installing from GitHub Releases

A commit on the dev branch alone is not sufficient for Morphe Manager. The release workflow must publish a prerelease with a downloadable `patches-<version>.mpp` asset. Check the GitHub Releases page and select the newest prerelease **published after this prototype**; an older bundle cannot contain this patch.

In Morphe Manager, enable prerelease patch sources, update `yuubinnkyoku/morphe-tver-patch`, and choose the experimental comment overlay patch manually. The patch starts disabled by default.

A successful Gradle `buildAndroid` job only confirms compilation. APK patching and on-device WebView composition still require testing.
