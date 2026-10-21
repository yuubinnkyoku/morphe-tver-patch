# Morphe TVer Patch

**日本語** | [English](README.en.md)

Android版TVer向けの非公式[Morphe](https://morphe.software/)パッチ集です。TVerおよびMorpheの公式プロジェクトではありません。

**対応アプリ：TVer 7.2.1**（パッケージ名：`jp.hamitv.hamiand1`、versionCode：`535`）。ほかのバージョンでの動作は未確認です。

## 機能一覧

| 機能 | 状況 | 内容 |
| --- | --- | --- |
| システムPiP | 実機確認済み | 見逃し配信を開いた直後・読み込み中・再生中にホームへ戻ると自動的にPiPへ移行 |
| PiPの誤動作防止 | 実機確認済み | 一時停止中やホーム画面の自動再生プレビューでは自動PiPを行わない |
| Morphe設定 | 実機確認済み | TVerの設定画面で「PiP TEST」ボタンの表示を切替。設定は再起動後も保持 |
| 起動時間の記録 | 実機未検証 | 起動処理の時刻を記録し、Morphe設定内で表示・コピー |
| 広告の抑制 | 実験的 | 一部の広告処理を抑えるパッチ。完全な広告除去は未確認 |
| 解像度別の画質選択 | 実機未検証 | 動画の「画質」を押すと、配信中の映像トラックから 1080p・720p などを列挙。自動にも戻せる |

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
- **起動ログ：** TVerを起動してから「TVerの設定 → Morphe設定 → 起動ログを表示・コピー」を開き、ダイアログの「コピー」を押します。`+`の後の数値は最初の記録からの経過ミリ秒です。記録はアプリのプロセス内だけに保持され、プロセス終了時に消えます。アカウント情報やAPI応答の内容は記録しません。初回の実機確認が必要です。

## 解像度別の画質選択（実験的）

Morpheのパッチ選択画面で **TVer: resolution quality picker (7.2.1)** を有効にしてください。初期状態では無効になっています。

- 通常の見逃し配信で再生画面の **「画質」** を押すと、その動画から取得できた解像度の一覧と「自動」を表示します。未準備の場合はTVer標準の画質メニューに戻ります。
- 「現在の動画の画質」には、再生器が報告する映像解像度を表示します。メニューの候補が1080pでも、実際に1080pで再生されることを保証しません。
- スマートフォンの通常VODに対する「自動・高」の上限を720pから1080pへ変更します。**1080pの映像をサーバーが提供していなければ選べません**。
- 選択は**現在の再生にのみ**適用されます。動画を切り替えた場合の設定保持は未実装です。ライブ・縦型動画・旧再生器では動作を保証しません。
- この機能はTVer 7.2.1の内部クラス構造に依存しています。実機検証前の試験的な実装のため、不具合があれば本パッチだけ無効にしてください。
- 問題を調べる場合は `adb logcat -s MorpheTVerQuality:V '*:S'` で画質関連のエラーを確認できます。

## 今後の予定（未実装）

- [ ] 広告抑制の実機検証・改善
- [x] 高速起動：Morphe設定でアニメーションをスキップすると、ホーム表示前の最大1秒の追加待機も省略（実機未検証）
- [ ] ハプティクス
- [ ] アプリ内ミニプレイヤー
- [ ] バックグラウンド再生
- [ ] ニコニコ風コメント表示
- [ ] YTDLnis、Seal Plusなどのポップアップへ飛ぶボタン

## 開発・ビルド

開発は`dev`ブランチで進めています。GitHub Actionsでパッチバンドルを生成・公開します。

- ビルド：`./gradlew buildAndroid`
- 出力：`patches/build/libs/patches-*.mpp`
- [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
- `patches-list.json`と`patches-bundle.json`はリリース時の自動生成物です。手動編集しないでください。

## 🩹 パッチ一覧

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0](https://github.com/yuubinnkyoku/morphe-tver-patch/releases/tag/v1.1.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;全3パッチ
<details open>
<summary>📦 TVer&nbsp;&nbsp;•&nbsp;&nbsp;3 パッチ</summary>
<br>

**🎯 対応バージョン:**

| 7.2.1 |
| :---: |

| 💊&nbsp;パッチ | 📜&nbsp;説明 | ⚙️&nbsp;オプション |
|----------|----------------|-----------|
| [TVer: 自動ピクチャーインピクチャー (7.2.1)](#tver-android-system-pip-7-2-1) | 見逃し配信を開いた直後・読み込み中・再生中に自動PiPへ移行。一時停止中やホームのプレビューは除外します。 |  |
| [TVer: resolution quality picker (7.2.1)](#tver-resolution-quality-picker-7-2-1) | Choose available video resolutions from a list (Auto, 1080p, 720p, etc.) and allow 1080p on phones for regular VOD. |  |
| [TVer: プレイヤー広告の抑制 (7.2.1)](#tver-suppress-player-ads-7-2-1) | 一部の広告リクエストや再生処理を抑える実験的なパッチです。広告が完全になくなることは保証しません。 |  |

</details>

<!-- PATCHES_END -->

*パッチ一覧は自動生成されます。日本語版では表示名・説明を翻訳しています。*

## ✨ スター数の推移

<a href="https://www.star-history.com/?repos=yuubinnkyoku%2Fmorphe-tver-patch&type=date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date" />
    <img alt="GitHub Star History" src="https://api.star-history.com/chart?repos=yuubinnkyoku/morphe-tver-patch&type=date" />
  </picture>
</a>

## 📜 ライセンス

[GNU General Public License v3.0](LICENSE)で公開しています。
