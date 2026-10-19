# Morphe TVer パッチ

**日本語** | [English](README.en.md)

Android版TVer向けの非公式[Morphe](https://morphe.software/)パッチ集です。TVerおよびMorpheの公式プロジェクトではありません。

**対応アプリ：TVer 7.2.1**（パッケージ名：`jp.hamitv.hamiand1`、versionCode：`535`）。ほかのバージョンでの動作は未確認です。

## 機能一覧

| 機能 | 状況 | 内容 |
| --- | --- | --- |
| システムPiP | 実機確認済み | 見逃し配信を開いた直後・読み込み中・再生中にホームへ戻ると自動的にPiPへ移行 |
| PiPの誤動作防止 | 実機確認済み | 一時停止中やホーム画面の自動再生プレビューでは自動PiPを行わない |
| Morphe設定 | 実機確認済み | TVerの設定画面で「PiP TEST」ボタンの表示を切替。設定は再起動後も保持 |
| 広告の抑制 | 実験的 | 一部の広告処理を抑えるパッチ。完全な広告除去は未確認 |

PiPは`v1.0.0-dev.17`まで、Morphe設定は`v1.0.0-dev.18`までの実機テストで動作を確認しています。すべての端末・動画での動作を保証するものではありません。

## 導入方法

[**➕ Morpheにパッチソースを追加**](https://morphe.software/add-source?github=yuubinnkyoku/morphe-tver-patch)

1. 上のリンクでMorpheにパッチソースを追加します。開発版を使う場合はプレリリースも有効にしてください。
2. 対応するオリジナルのTVer 7.2.1（`jp.hamitv.hamiand1`、versionCode `535`）のAPKを用意します。
3. Morpheで必要なパッチを選んで適用し、インストールします。
4. アプリの起動・動画再生・PiP・設定画面を確認してください。

パッチバンドルは[GitHub Releases](https://github.com/yuubinnkyoku/morphe-tver-patch/releases)で配布しています。改変済みTVer APKは配布していません。問題が起きた場合に元のアプリへ戻せるようにしてください。関連する規約や法令にも従ってください。

## PiPとMorphe設定

- **動画を開いた直後・読み込み中・再生中：** ホーム操作でシステムPiPに移行
- **一時停止中・ホームのプレビュー再生中：** 自動PiPにならない
- **PiP TESTボタン：** 初期状態では表示。「TVerの設定 → Morphe設定 → PiP TESTボタンを表示」から変更可能。自動PiPの動作は変わりません。

## 今後の予定（未実装）

- [ ] 広告抑制の実機検証・改善
- [ ] 起動アニメーションのスキップ
- [ ] ハプティクス
- [ ] アプリ内ミニプレイヤー
- [ ] ニコニコ風コメント表示

## 開発・ビルド

開発は`dev`ブランチで進めています。GitHub Actionsでパッチバンドルを生成・公開します。

- ビルド：`./gradlew buildAndroid`
- 出力：`patches/build/libs/patches-*.mpp`
- [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
- `patches-list.json`と`patches-bundle.json`はリリース時の自動生成物です。手動編集しないでください。

## 🩹 パッチ一覧

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/yuubinnkyoku/morphe-tver-patch/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;全3パッチ
<details open>
<summary>📦 XYZ app&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 対応バージョン:**

| 2.0.0 | 1.0.2 |
| :---: | :---: |

| 💊&nbsp;パッチ | 📜&nbsp;説明 | ⚙️&nbsp;オプション |
|----------|----------------|-----------|
| [Example Patch](#example-patch) | Example patch to start with. |  |

</details>

<details open>
<summary>📦 jp.hamitv.hamiand1&nbsp;&nbsp;•&nbsp;&nbsp;2 パッチ</summary>
<br>

**🎯 対応バージョン:**

| 7.2.1 |
| :---: |

| 💊&nbsp;パッチ | 📜&nbsp;説明 | ⚙️&nbsp;オプション |
|----------|----------------|-----------|
| [TVer: Android system PiP (7.2.1)](#tver-android-system-pip-7-2-1) | Try system PiP from TVer video screen, with a Home audio playback fallback. |  |
| [TVer: suppress player ads (7.2.1)](#tver-suppress-player-ads-7-2-1) | Attempt to skip pre-roll ads and disable the player's client-side ad hooks. |  |

</details>

<!-- PATCHES_END -->

*パッチ名・説明はメタデータを元に自動生成されるため、一部英語のままです。*

## ✨ スター数の推移

<p align="center">
    <img src="https://starchart.cc/yuubinnkyoku/morphe-tver-patch.svg?variant=light" alt="GitHub Starの推移" width="640" height="320" />
</p>

## 📜 ライセンス

[GNU General Public License v3.0](LICENSE)で公開しています。
