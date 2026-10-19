# TVer 7.2.1 — experimental Morphe patches

Unofficial TVer patches for testing Android picture-in-picture (PiP) and client-side ad suppression. Not affiliated with TVer or Morphe.

## ❓ About

Targets **`jp.hamitv.hamiand1` version `7.2.1` (versionCode `535`)**.

- **TVer: suppress player ads**: attempts to skip pre-roll scheduling and client-side player ad hooks. Some ads may remain or playback may break.
- **TVer: Android system PiP**: opts video activities in to Android PiP and attempts to enter it when leaving an active video screen. TVer may still pause playback.

**Status: experimental prerelease.** GitHub Actions compile verification passed, but successful APK patching, installation, ad suppression and background PiP playback have **not** been confirmed on a device.

### Prerelease testing

1. In Morphe, add the [patch source](https://morphe.software/add-source?github=yuubinnkyoku/morphe-tver-patch) and enable prereleases for this source.
2. Select the original TVer 7.2.1 APK and choose either or both experimental patches.
3. Verify that the patched app installs, starts, and can play an ordinary video.
4. For PiP, start video playback and use Home to check whether Android enters its system floating video window.
5. For ad suppression, check pre-roll and mid-roll separately; neither is guaranteed to be removed.

**Be prepared to restore the original app if patching fails, playback stalls, or the app crashes.**

Development happens on the `dev` branch. The template release workflow publishes `feat:`/`fix:` changes; `chore:` commits only verify compilation.

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=yuubinnkyoku/morphe-tver-patch

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.13](https://github.com/yuubinnkyoku/morphe-tver-patch/releases/tag/v1.0.0-dev.13)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 XYZ app&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.0.0 | 1.0.2 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Example Patch](#example-patch) | Example patch to start with. |  |

</details>

<details open>
<summary>📦 jp.hamitv.hamiand1&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 7.2.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [TVer: Android system PiP (7.2.1)](#tver-android-system-pip-7-2-1) | Try system PiP from TVer video screen, with a Home audio playback fallback. |  |
| [TVer: suppress player ads (7.2.1)](#tver-suppress-player-ads-7-2-1) | Attempt to skip pre-roll ads and disable the player's client-side ad hooks. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

yuubinnkyoku TVer patches are licensed under the [GNU General Public License v3.0](LICENSE)
