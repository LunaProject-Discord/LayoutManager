# Layout Manager

Named tool window layouts for JetBrains Rider, like **Window | Layouts** in other JetBrains IDEs.

[日本語](#日本語)

## Features

- Save the current tool window layout under a name and switch between layouts at any time.
- Keep layouts shared by all solutions or specific to one solution.
- Restore the active layout, save changes into it, rename or delete layouts, or go back to the default layout.
- Reopen each solution with the tool window layout it had when it was closed.
- Rider's **Window | Layout Settings** is integrated into the layout menu (optional).
- English and Japanese user interface.

The actions are under **Window | Layouts**, the options under **Settings | Tools | Layout Manager**.

### Layout Manager Advanced

An optional, separate plugin (in [`advanced`](advanced)) with the features that rely on internal IDE API, which
JetBrains Marketplace does not accept. It is distributed here on GitHub:

- A precise layout engine that also restores the order of tool window buttons and floating window bounds.
- The layout menu of other JetBrains IDEs, selectable instead of Layout Manager's.

Install it with **Install Layout Manager Advanced…** in **Settings | Tools | Layout Manager**. After a confirmation,
this adds the plugin repository [`updatePlugins.xml`](updatePlugins.xml) to **Settings | Plugins | Manage Plugin
Repositories**, so the IDE installs it and offers its updates. Internal API can change in any IDE update, so it may
stop working until it is updated; Layout Manager itself keeps working without it.

## Requirements

JetBrains Rider 2026.1 to 2026.3 (builds 261 to 263).

## Installation

Install **Layout Manager** from JetBrains Marketplace (**Settings | Plugins | Marketplace**), or download the
plugin ZIP and use **Settings | Plugins | ⚙ | Install Plugin from Disk…**.

## Building

JDK 25 is provisioned by Gradle toolchains.

```bash
./gradlew buildPlugin            # build/distributions/LayoutManagerPlugin-<version>.zip
./gradlew :advanced:buildPlugin  # advanced/build/distributions/LayoutManagerAdvanced-<version>.zip
./gradlew runIde                 # run a sandboxed Rider with the plugin
./gradlew verifyPlugin           # IntelliJ Plugin Verifier; fails on any internal API usage
./gradlew selfTest               # in-IDE self-test, with and without Layout Manager Advanced (four Rider launches)
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

### 1. Create a signing key and certificate

The plugin signer supports ECDSA, RSA and DSA keys (not Ed25519). These steps create an ECDSA key on the P-384
curve, protected by a password, in `~/.jetbrains-signing`. Keep that folder and the password private and backed up:
later versions must be signed with the same key. OpenSSL is included in Git for Windows.

Windows PowerShell (OpenSSL is not on its `PATH`, so it is called by its full path):

```powershell
$openssl = 'C:\Program Files\Git\ucrt64\bin\openssl.exe'
New-Item -ItemType Directory -Force ~\.jetbrains-signing | Out-Null
Set-Location ~\.jetbrains-signing
& $openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-384 -aes-256-cbc -out private_encrypted.pem
& $openssl req -key private_encrypted.pem -new -x509 -days 3650 -sha384 -out chain.crt
```

Git Bash:

```bash
mkdir -p ~/.jetbrains-signing && cd ~/.jetbrains-signing
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-384 -aes-256-cbc -out private_encrypted.pem
openssl req -key private_encrypted.pem -new -x509 -days 3650 -sha384 -out chain.crt
```

`genpkey` asks for the key password twice; `req` asks for it again and for the certificate details (for example
`Luna Project` as the organization). In Git Bash, do not pass the details with `-subj "/CN=…"`: the shell rewrites the
leading `/` into a Windows path.

### 2. Sign the plugin

Set the variables in the same terminal, then sign. Replace `<password>` with the key password.

```powershell
$env:CERTIFICATE_CHAIN = Get-Content -Raw ~\.jetbrains-signing\chain.crt
$env:PRIVATE_KEY = Get-Content -Raw ~\.jetbrains-signing\private_encrypted.pem
$env:PRIVATE_KEY_PASSWORD = '<password>'
.\gradlew.bat signPlugin
```

```bash
export CERTIFICATE_CHAIN="$(cat ~/.jetbrains-signing/chain.crt)"
export PRIVATE_KEY="$(cat ~/.jetbrains-signing/private_encrypted.pem)"
export PRIVATE_KEY_PASSWORD='<password>'
./gradlew signPlugin
```

The result is `build/distributions/LayoutManagerPlugin-<version>-signed.zip`. To check the signature, run
`verifyPluginSignature` afterwards as a separate command (Gradle rejects running both tasks in one command).
Sign Layout Manager Advanced the same way with `:advanced:signPlugin`
(`advanced/build/distributions/LayoutManagerAdvanced-<version>-signed.zip`).

### 3. Publish

1. Upload the **first** version by hand on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/add):
   the signed ZIP, license LGPL 2.1, source code `https://github.com/LunaProject-Discord/LayoutManager`.
2. Create a token under **My Tokens** in your Marketplace profile and set it as `PUBLISH_TOKEN`.
3. Publish later versions with `publishPlugin` (signs, verifies and uploads), with the variables above set.

Layout Manager Advanced is released on GitHub instead:

1. Create a release with the tag `advanced-v<version>` and attach the signed ZIP.
2. Update `url`, `version` and `idea-version` in [`updatePlugins.xml`](updatePlugins.xml) and push it to `main`:
   installed copies are offered the update from there.

See also [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

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
- 英語と日本語の表示に対応しています。

操作は **ウィンドウ | レイアウト**、設定は **設定 | ツール | Layout Manager** にあります。

#### Layout Manager Advanced

JetBrains Marketplace では認められない IDE の内部 API を使う機能をまとめた、オプションの別プラグインです ([`advanced`](advanced))。
GitHub で配布しています。

- ツールウィンドウのボタンの並び順やフローティングウィンドウの位置まで復元する、正確なレイアウトエンジン。
- Layout Manager のメニューの代わりに選べる、他の JetBrains IDE のレイアウトメニュー。

**設定 | ツール | Layout Manager** の **Layout Manager Advanced をインストール…** からインストールします。確認のあと、
プラグインリポジトリ [`updatePlugins.xml`](updatePlugins.xml) を **設定 | プラグイン | プラグインリポジトリの管理** に追加し、
IDE がインストールと更新の通知を行います。内部 API は IDE の更新で変わることがあるため、更新版が出るまで動作しなくなる可能性があります。
その場合も Layout Manager 自体は動作します。

### 動作環境

JetBrains Rider 2026.1〜2026.3 (ビルド 261〜263)

### インストール

JetBrains Marketplace (**設定 | プラグイン | Marketplace**) から **Layout Manager** をインストールするか、プラグインの
ZIP をダウンロードして **設定 | プラグイン | ⚙ | ディスクからプラグインをインストール…** を使用してください。

### ビルド

JDK 25 は Gradle のツールチェーンで自動的に用意されます。

```bash
./gradlew buildPlugin            # build/distributions/LayoutManagerPlugin-<バージョン>.zip
./gradlew :advanced:buildPlugin  # advanced/build/distributions/LayoutManagerAdvanced-<バージョン>.zip
./gradlew runIde                 # プラグインを入れたテスト用の Rider を起動
./gradlew verifyPlugin           # IntelliJ Plugin Verifier (内部 API を使っていると失敗)
./gradlew selfTest               # IDE 内の自己テスト。Layout Manager Advanced なし・ありの両方 (Rider を4回起動)
```

プラグインは、対応する最も古い Rider (`platformVersion`、JetBrains の Maven リポジトリからダウンロード) に対してビルドし、
その版と最新の版 (`platformVersionLatest`) で検証します。インストール済みの Rider でビルド・実行する場合は
`-PriderLocalPath=<Rider のインストール先>` を指定してください。この場合はその Rider の Java のバージョン向けにビルドされるため、
開発用であり、公開には使えません。

### 署名と公開

秘密の情報は環境変数からのみ読み込み、リポジトリには保存しません (変数の一覧は英語版の表を参照)。

#### 1. 署名用の鍵と証明書を作る

署名ツールが対応している鍵は ECDSA、RSA、DSA です (Ed25519 には対応していません)。以下の手順では、パスワードで保護した
P-384 曲線の ECDSA 鍵を `~/.jetbrains-signing` に作ります。このフォルダーとパスワードは他人に渡さず、控えを取っておいてください。
以後の更新も同じ鍵で署名する必要があります。OpenSSL は Git for Windows に含まれています。

Windows PowerShell (OpenSSL が `PATH` にないため、フルパスで呼び出します):

```powershell
$openssl = 'C:\Program Files\Git\ucrt64\bin\openssl.exe'
New-Item -ItemType Directory -Force ~\.jetbrains-signing | Out-Null
Set-Location ~\.jetbrains-signing
& $openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-384 -aes-256-cbc -out private_encrypted.pem
& $openssl req -key private_encrypted.pem -new -x509 -days 3650 -sha384 -out chain.crt
```

Git Bash:

```bash
mkdir -p ~/.jetbrains-signing && cd ~/.jetbrains-signing
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-384 -aes-256-cbc -out private_encrypted.pem
openssl req -key private_encrypted.pem -new -x509 -days 3650 -sha384 -out chain.crt
```

`genpkey` では鍵のパスワードを2回、`req` ではそのパスワードと証明書の情報 (組織名に `Luna Project` など) を聞かれます。
Git Bash では `-subj "/CN=…"` で情報を渡さないでください。先頭の `/` が Windows のパスに書き換えられてエラーになります。

#### 2. 署名する

同じターミナルで環境変数を設定してから署名します。`<パスワード>` は鍵のパスワードに置き換えてください。

```powershell
$env:CERTIFICATE_CHAIN = Get-Content -Raw ~\.jetbrains-signing\chain.crt
$env:PRIVATE_KEY = Get-Content -Raw ~\.jetbrains-signing\private_encrypted.pem
$env:PRIVATE_KEY_PASSWORD = '<パスワード>'
.\gradlew.bat signPlugin
```

```bash
export CERTIFICATE_CHAIN="$(cat ~/.jetbrains-signing/chain.crt)"
export PRIVATE_KEY="$(cat ~/.jetbrains-signing/private_encrypted.pem)"
export PRIVATE_KEY_PASSWORD='<パスワード>'
./gradlew signPlugin
```

`build/distributions/LayoutManagerPlugin-<バージョン>-signed.zip` ができます。署名を確認する場合は、続けて
`verifyPluginSignature` を別のコマンドとして実行してください (1つのコマンドで両方を実行すると Gradle がエラーにします)。
Layout Manager Advanced も同じ方法で `:advanced:signPlugin` を実行して署名します
(`advanced/build/distributions/LayoutManagerAdvanced-<バージョン>-signed.zip`)。

#### 3. 公開する

1. **最初の**バージョンは [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/add) から手動でアップロードします。
   署名済みの ZIP をアップロードし、ライセンスに LGPL 2.1、ソースコードに `https://github.com/LunaProject-Discord/LayoutManager` を指定します。
2. Marketplace のプロフィールの **My Tokens** でトークンを作成し、`PUBLISH_TOKEN` に設定します。
3. 2回目以降は、上の環境変数を設定したうえで `publishPlugin` を実行すると公開できます (署名・検証・アップロードを行います)。

Layout Manager Advanced は GitHub で公開します。

1. タグ `advanced-v<バージョン>` でリリースを作り、署名済みの ZIP を添付します。
2. [`updatePlugins.xml`](updatePlugins.xml) の `url`、`version`、`idea-version` を更新して `main` に push します。
   インストール済みの環境には、ここから更新が通知されます。

[Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html) も参照してください。

### ライセンス

[GNU Lesser General Public License v2.1](LICENSE)
