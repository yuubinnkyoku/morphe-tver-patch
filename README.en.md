# Morphe TVer patches

[日本語](README.md) | **English**

Unofficial [Morphe](https://morphe.software/) patches for TVer Android. Not affiliated with TVer or Morphe.

**Supported APK:** `jp.hamitv.hamiand1` — **TVer 7.2.1** (versionCode **535**). Other app versions are not verified.

## Features and current status

| Feature | Status | Notes |
| --- | --- | --- |
| System picture-in-picture (PiP) | **Device-tested** | Automatic PiP on a replay episode, including immediately after opening it and while buffering. |
| PiP exclusions | **Device-tested** | No automatic PiP for a paused episode or home-screen auto-preview. |
| Morphe settings | **Device-tested** | `Morphe設定` on TVer's settings screen toggles the `PiP TEST` overlay. The preference survives app restarts. |
| Startup timing log | **Not yet device-tested** | Logs startup milestones and exposes an in-app view/copy dialog. |
| Client-side player ad suppression | **Experimental** | Attempts to disable pre-roll requests and some ad hooks. Complete ad removal is not established. |

PiP was tested on an Android device through **v1.0.0-dev.17**, and Morphe settings through **v1.0.0-dev.18**. Functionality on other Android devices and TVer video types is not guaranteed. CI success alone does not constitute an on-device test.

## Installation

1. Add this [Morphe patch source](https://morphe.software/add-source?github=yuubinnkyoku/morphe-tver-patch); enable prereleases.
2. Select an **original TVer 7.2.1** APK (package `jp.hamitv.hamiand1`, versionCode 535).
3. Apply the desired patches, install the resulting APK, and check playback and settings.

Patch bundles are published on [GitHub Releases](https://github.com/yuubinnkyoku/morphe-tver-patch/releases); patched TVer APKs are not distributed here. Keep a way to restore the original app, because patches can cause startup or playback failures. Observe applicable terms and laws.

## PiP and Morphe settings

- **Replay opened / buffering / playing:** Home gesture enters system PiP.
- **Replay paused:** Automatic PiP is blocked.
- **Home auto-playing preview:** Automatic PiP is blocked.
- **PiP TEST button:** Visible by default; go to **TVer settings → Morphe設定 → PiP TESTボタンを表示** to turn it off. This setting changes only the manual test button, not automatic PiP.
- **Startup log:** Open **TVer settings → Morphe設定 → 起動ログを表示・コピー**, then tap **コピー** to copy the timing milestones. Values after `+` are milliseconds since the first recorded event. Logs are kept in process memory and disappear when the process ends; no account identifiers or API response bodies are included. Device verification is pending.

## Roadmap (not implemented)

- [ ] Verify and improve client-side ad suppression (pre-roll and mid-roll).
- [x] Fast startup: the Morphe setting skips the startup animation and up to 1 second of extra home landing grace (device testing pending).
- [ ] Configurable haptic feedback.
- [ ] In-app miniplayer.
- [ ] Optional Nico Nico–style timed comments overlay.

## Developing

Development takes place on the `dev` branch. CI builds and publishes prerelease patch bundles after qualifying commits.

- Build: `./gradlew buildAndroid`
- Output: `patches/build/libs/patches-*.mpp`
- Desktop patcher: [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
- Automatically generated release metadata (`patches-list.json`, `patches-bundle.json`) must not be edited manually.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0](https://github.com/yuubinnkyoku/morphe-tver-patch/releases/tag/v1.1.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 TVer&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 7.2.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [TVer: Android system PiP (7.2.1)](#tver-android-system-pip-7-2-1) | Automatically enter system PiP for replay episodes; excludes paused episodes and home previews. |  |
| [TVer: resolution quality picker (7.2.1)](#tver-resolution-quality-picker-7-2-1) | Choose available video resolutions from a list (Auto, 1080p, 720p, etc.) and allow 1080p on phones for regular VOD. |  |
| [TVer: suppress player ads (7.2.1)](#tver-suppress-player-ads-7-2-1) | Experimentally suppress selected player ad requests and hooks; full ad blocking is not guaranteed. |  |

</details>

<!-- PATCHES_END -->

## ✨ GitHub Star History

<a href="https://www.star-history.com/?repos=yuubinnkyoku%2Fmorphe-tver-patch&type=date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date" />
    <img alt="GitHub Star History" src="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date" />
  </picture>
</a>

## 📜 License

yuubinnkyoku TVer patches are licensed under the [GNU General Public License v3.0](LICENSE)

## Resolution-based video quality picker (experimental)

Enable **TVer: resolution quality picker (7.2.1)** in Morphe (off by default). In the regular VOD player, the existing **Quality** button opens a list of renditions actually reported by the playback SDK (for example 1080p / 720p) plus **Auto**. When the rendition list is not available, the stock TVer quality menu remains accessible.

The patch lifts the phone's regular-VOD AUTO/HIGH 720p resolution cap to 1080p, but **it cannot provide 1080p when the server does not offer it**. The displayed current resolution is reported by the playback state. Manual selection is per playback, not a persistent global preference. Live, short-form and legacy players are not guaranteed to work. This is **not yet device-tested**; disable this patch if it causes playback problems.

