# Layout Manager

Named tool window layouts for JetBrains Rider, like **Window | Layouts** in other JetBrains IDEs.

[日本語](#日本語)

## Features

- Save the current tool window layout under a name and switch between layouts at any time.
- Keep layouts shared by all solutions or specific to one solution.
- Restore the active layout, save changes into it, rename or delete layouts, or go back to the default layout.
- Reopen each solution with the tool window layout it had when it was closed.
- Rider's **Window | Layout Settings** is integrated into the layout menu (optional).
- Prefer the menu of other JetBrains IDEs? Switch to it in the settings.
- English and Japanese user interface.

The actions are under **Window | Layouts**, the options under **Settings | Tools | Layout Manager**.

## Requirements

JetBrains Rider 2026.1 to 2026.3 (builds 261 to 263).

## Installation

Install **Layout Manager** from JetBrains Marketplace (**Settings | Plugins | Marketplace**), or download the
plugin ZIP and use **Settings | Plugins | ⚙ | Install Plugin from Disk…**.

## Building

JDK 25 is provisioned by Gradle toolchains.

```bash
./gradlew buildPlugin      # build/distributions/LayoutManagerPlugin-<version>.zip
./gradlew runIde           # run a sandboxed Rider with the plugin
./gradlew verifyPlugin     # IntelliJ Plugin Verifier
./gradlew selfTest         # in-IDE self-test (two Rider launches, about a minute and a half)
```

The plugin is built against the oldest supported Rider (`platformVersion`, downloaded from the JetBrains Maven
repository) and verified against it and the latest one (`platformVersionLatest`). To build and run against a locally
installed Rider instead, pass `-PriderLocalPath=<Rider install directory>`; such builds target that Rider's Java
version and are for development only, not for release.

## Signing and publishing

The build reads secrets from environment variables only; nothing is stored in the repository.

| Variable | Used by | Content |
|---|---|---|
| `CERTIFICATE_CHAIN` | `signPlugin` | Certificate chain (PEM) |
| `PRIVATE_KEY` | `signPlugin` | Private key (PEM) |
| `PRIVATE_KEY_PASSWORD` | `signPlugin` | Password of the private key |
| `PUBLISH_TOKEN` | `publishPlugin` | JetBrains Marketplace token |

1. Create a signing certificate as described in
   [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).
2. Upload the **first** version by hand on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/add):
   build it with `./gradlew signPlugin` and upload `build/distributions/LayoutManagerPlugin-<version>-signed.zip`.
3. Create a token under **My Tokens** in your Marketplace profile.
4. Publish later versions with `./gradlew publishPlugin` (signs, verifies and uploads).

## License

[GNU Lesser General Public License v2.1](LICENSE)

---

## 日本語

JetBrains Rider に、他の JetBrains IDE の **ウィンドウ | レイアウト** と同様の、名前付きツールウィンドウレイアウトを追加するプラグインです。

### 機能

- 現在のツールウィンドウのレイアウトに名前を付けて保存し、いつでも切り替えられます。
- レイアウトは、すべてのソリューションで共通にするか、ソリューションごとに保存するかを選べます。
- 使用中のレイアウトの復元、変更の保存、名前の変更、削除、デフォルトのレイアウトへの切り替えができます。
- ソリューションを開くと、前回閉じたときのレイアウトを復元します。
- Rider の **ウィンドウ | レイアウト設定** をレイアウトメニューに統合します (オプション)。
- 他の JetBrains IDE と同じメニューを使いたい場合は、設定で切り替えられます。
- 英語と日本語の表示に対応しています。

操作は **ウィンドウ | レイアウト**、設定は **設定 | ツール | Layout Manager** にあります。

### 動作環境

JetBrains Rider 2026.1〜2026.3 (ビルド 261〜263)

### インストール

JetBrains Marketplace (**設定 | プラグイン | Marketplace**) から **Layout Manager** をインストールするか、プラグインの
ZIP をダウンロードして **設定 | プラグイン | ⚙ | ディスクからプラグインをインストール…** を使用してください。

### ビルド

JDK 25 は Gradle のツールチェーンで自動的に用意されます。

```bash
./gradlew buildPlugin      # build/distributions/LayoutManagerPlugin-<バージョン>.zip
./gradlew runIde           # プラグインを入れたテスト用の Rider を起動
./gradlew verifyPlugin     # IntelliJ Plugin Verifier
./gradlew selfTest         # IDE 内の自己テスト (Rider を2回起動、約1分半)
```

プラグインは、対応する最も古い Rider (`platformVersion`、JetBrains の Maven リポジトリからダウンロード) に対してビルドし、
その版と最新の版 (`platformVersionLatest`) で検証します。インストール済みの Rider でビルド・実行する場合は
`-PriderLocalPath=<Rider のインストール先>` を指定してください。この場合はその Rider の Java のバージョン向けにビルドされるため、
開発用であり、公開には使えません。

### 署名と公開

秘密の情報は環境変数からのみ読み込み、リポジトリには保存しません (変数の一覧は英語版の表を参照)。

1. [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) の手順で署名用の証明書を作成します。
2. **最初の**バージョンは [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/add) から手動でアップロードします。
   `./gradlew signPlugin` でビルドし、`build/distributions/LayoutManagerPlugin-<バージョン>-signed.zip` をアップロードしてください。
3. Marketplace のプロフィールの **My Tokens** でトークンを作成します。
4. 2回目以降は `./gradlew publishPlugin` で公開できます (署名・検証・アップロードを行います)。

### ライセンス

[GNU Lesser General Public License v2.1](LICENSE)
