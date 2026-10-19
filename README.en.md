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

## Roadmap (not implemented)

- [ ] Verify and improve client-side ad suppression (pre-roll and mid-roll).
- [ ] Option to bypass the startup animation.
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
> **[v1.0.1](https://github.com/yuubinnkyoku/morphe-tver-patch/releases/tag/v1.0.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 TVer&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 7.2.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [TVer: Android system PiP (7.2.1)](#tver-android-system-pip-7-2-1) | Automatically enter system PiP for replay episodes; excludes paused episodes and home previews. |  |
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
