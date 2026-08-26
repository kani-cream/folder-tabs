# Folder Tabs - GitHub Actions CI 設計書

- **文書状態**: Draft v0.1
- **作成日**: 2026-08-21
- **対象**: `kani-cream/folder-tabs`
- **対象IDE基準**: IntelliJ Platform / IntelliJ IDEA 2026.2+
- **関連文書**: `plan/grouped-editor-tabs-design.md`
- **基本方針**: リリース・Marketplace公開は手動。GitHub Actionsは品質保証と互換性監視に限定する。

---

## 1. 目的

Folder Tabsでは、JetBrains Marketplaceへの公開そのものはCIから行わない。

一方で、PRおよびmainへ入るコードについては、可能な限りGitHub Actions上で再現可能な検証を行い、次を継続的に担保する。

- ソースコードがコンパイルできる
- Unit / Platform Testが通る
- IntelliJ Platform Gradle Pluginのプロジェクト構成が妥当
- Plugin ZIPを生成できる
- Plugin構造が妥当
- 対象IDEでPlugin Verifierが通る
- Deprecated / Experimental / Internal / Scheduled-for-removal APIを使用していない
- テスト失敗・Verifier失敗時に原因調査用Artifactが残る
- IDE更新によって、昨日まで通っていたPluginが将来壊れる兆候を定期的に検出できる

CIの責務は **build / test / verify / compatibility monitoring** までとする。

---

## 2. 明示的にCIの責務外とするもの

以下はGitHub Actionsから実行しない。

- JetBrains Marketplaceへのpublish
- `publishPlugin`
- Git tag作成
- GitHub Release作成
- バージョン番号更新
- CHANGELOG確定
- Marketplace channel切替
- Marketplace tokenを使う処理

CI Repository Secretsに `MARKETPLACE_TOKEN` を登録することも前提にしない。

リリースは開発者がローカルまたは別途定める手動手順で行う。

---

## 3. 既存プラグインCIから採用する知見

### 3.1 Repo Lens

Repo Lensの `build.yml` は、main push / PRで以下を実行している。

- disk cleanup
- JDK setup
- Gradle cache
- build / test
- Plugin Verifier
- distribution upload

一方、`release.yml` はtag pushでMarketplace publishまで行う。

Folder Tabsでは **build.yml側の考え方のみ採用し、release.yml型の自動公開は採用しない。**

### 3.2 Flow Lens

Flow Lensからは次を採用する。

- `concurrency` + `cancel-in-progress`
- draft PRを無駄に実行しない
- docs / planのみの変更では重いCIを実行しない
- test failure時にtest reportをArtifactへ保存
- Plugin Verifier reportをArtifactへ保存
- IntelliJ distribution / Gradle transformsによるrunner disk不足対策

Flow LensではActionsコストを抑えるためPlugin Verifierをmanual側へ寄せているが、Folder Tabsはpublic repositoryであり、かつStable Public API Onlyを重要な品質条件としている。

そのためFolder Tabsでは **PR / mainでもPlugin Verifierを必須ゲートにする。**

### 3.3 Settings Jump

Settings JumpはPR / mainでbuild + test + Plugin Verifierをまとめて実行しており、Folder Tabsの通常CIに最も近い。

Folder Tabsではこれを基礎にしつつ、次を追加する。

- API stability failure levelsを明示
- Project Configuration検証
- Plugin Structure検証
- Scheduled compatibility check
- distribution生成確認と公開処理の分離
- JDK 25

---

## 4. JDK / Build Environment

### 4.1 Java 25

Folder TabsはIntelliJ Platform 2026.2を最低基準とするため、CIでは **JDK 25** を使用する。

```text
JDK: Temurin 25
Runner: ubuntu-latest
```

Repo Lens / Flow Lens / Settings JumpのJDK 21設定は、それぞれ主に2026.1以前を対象としているため、そのままコピーしない。

Gradle側のToolchainも2026.2基準に合わせる。

```kotlin
kotlin {
    jvmToolchain(25)
}
```

### 4.2 GitHub Actions

初期採用:

```text
actions/checkout@v4
actions/setup-java@v4
gradle/actions/setup-gradle@v4
actions/upload-artifact@v4
```

Actionのmajor version更新はDependabot等で別途検知可能とするが、CI設計は特定minor versionへ依存しない。

### 4.3 Permissions

通常CIはread-onlyとする。

```yaml
permissions:
  contents: read
```

CIからrepositoryへcommit / tag / releaseを書き込む権限は与えない。

---

## 5. Workflow構成

次の2 Workflowを基本とする。

```text
.github/workflows/
├ ci.yml
└ compatibility.yml
```

### `ci.yml`

日常開発の必須品質ゲート。

対象:

- Pull Request
- main push
- workflow_dispatch

### `compatibility.yml`

IDE / Plugin Verifier側の変化を検出する定期監視。

対象:

- schedule
- workflow_dispatch

Marketplace publish workflowは作成しない。

---

## 6. ci.yml - Trigger設計

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
    paths-ignore:
      - '**.md'
      - 'plan/**'
      - 'docs/**'
      - '.gitignore'
  push:
    branches: [main]
    paths-ignore:
      - '**.md'
      - 'plan/**'
      - 'docs/**'
      - '.gitignore'
  workflow_dispatch:
```

### 理由

コード・Gradle・plugin.xml・workflowを変更した場合はCIを実行する。

設計書やREADMEだけの変更ではIntelliJ distributionをダウンロードする必要がないためskipする。

`workflow_dispatch` は、リリース前などに任意のcommitでフルチェックを再実行する用途とする。

---

## 7. concurrency

同一PRで新しいcommitがpushされた場合、古いrunは停止する。

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

Plugin Verifierは重いため、古いcommitの検証を最後まで継続する価値は低い。

---

## 8. ci.yml - Job構成

初期段階では複数runnerへ分割せず、**1つのQuality Gate job内で順番に実行する。**

```text
quality-gate
├ Environment setup
├ Project configuration verification
├ Build & tests
├ Plugin distribution build
├ Plugin structure verification
├ Plugin Verifier
└ Diagnostic artifacts
```

理由:

- IntelliJ distributionの再downloadを避ける
- Gradle cache / extracted dependenciesを同一runnerで再利用する
- 小規模プラグインなのでjob分割による並列化メリットが小さい
- 失敗箇所をActions画面上のstep単位で識別できる

将来test時間が大きくなった場合のみjob分割を検討する。

---

## 9. Runner disk対策

既存3プラグインと同様、IntelliJ distribution / Plugin Verifier IDE / Gradle transformsによりGitHub-hosted runnerのdiskを大きく消費する。

CI開始時に不要toolchainを削除する。

```bash
sudo rm -rf /usr/share/dotnet /usr/local/lib/android /opt/ghc \
  /usr/local/.ghcup /opt/hostedtoolcache/CodeQL
sudo docker image prune --all --force
df -h /
```

Gradle cacheではre-derivableなtransformsを保存対象から外す。

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    gradle-home-cache-excludes: |
      caches/**/transforms
```

---

## 10. Quality Gate詳細

### 10.1 Project Configuration

最初にIntelliJ Platform Gradle Pluginの構成検査を行う。

```bash
./gradlew verifyPluginProjectConfiguration --no-daemon --stacktrace
```

目的:

- target IntelliJ Platform設定の異常
- Kotlin / Java / Gradle設定の不整合
- plugin project configuration warning / error

このtaskの警告を理由なくmuteしない。

### 10.2 Build & Test

```bash
./gradlew build --no-daemon --stacktrace
```

最低限次を含む。

- Kotlin / Java compile
- unit tests
- IntelliJ Platform tests
- resource processing
- plugin module build

Folder TabsではUIロジックそのものよりModel / path resolution / event同期のテストをCIで厚くする。

### 10.3 Distribution Build

```bash
./gradlew buildPlugin --no-daemon --stacktrace
```

Marketplaceへuploadはしないが、**Marketplaceへ投入可能なZIPを生成できること自体は毎回検証する。**

さらにZIPが実在することを明示的に確認する。

```bash
test -n "$(find plugin/build/distributions -name '*.zip' -print -quit)"
```

### 10.4 Plugin Structure

```bash
./gradlew verifyPluginStructure --no-daemon --stacktrace
```

plugin archive / descriptor構造上の問題をPlugin Verifierとは別に早期検出する。

### 10.5 Plugin Verifier

```bash
./gradlew :plugin:verifyPlugin --no-daemon --stacktrace
```

Folder TabsではPlugin Verifierを **PR必須ゲート** とする。

---

## 11. Plugin Verifier - Failure Policy

`plan/grouped-editor-tabs-design.md` の Stable Public API Only 方針をCIで強制する。

`plugin/build.gradle.kts` では `failureLevel` を明示する。

少なくとも以下を0件必須とする。

```text
COMPATIBILITY_PROBLEMS
DEPRECATED_API_USAGES
SCHEDULED_FOR_REMOVAL_API_USAGES
EXPERIMENTAL_API_USAGES
INTERNAL_API_USAGES
OVERRIDE_ONLY_API_USAGES
NON_EXTENDABLE_API_USAGES
MISSING_DEPENDENCIES
INVALID_PLUGIN
```

### 11.1 INTERNAL_API_USAGES の例外（許可リスト）

2026-08-26 追記: `INTERNAL_API_USAGES` だけは verifier の `failureLevel` ではなく、
`plugin/build.gradle.kts` の `checkInternalApiUsages` タスク（`verifyPlugin` の finalizer）で強制する。
verifier 自身には内部 API 使用を個別に除外する手段がない（`ignoredProblemsFile` は互換性問題専用）ため。

- 判定対象は verifier レポートの `internal-api-usages.txt`。各行が
  `plugin/verifier-internal-api-allowlist.txt` のいずれかの正規表現に一致しなければ fail。
- 許可リストの項目がどの使用にも一致しなくなった場合も fail（古い例外を残さない）。
- 許可リストはレビュー済み例外のみ。現在の唯一の例外: 選択タブを標準エディタタブと同じ
  アクティブ色（青い下線）で描くために必要な `JBTabsImpl.isActiveTabs` のオーバーライド（`TabStrip`）。

概念例:

```kotlin
pluginVerification {
    failureLevel = listOf(
        VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
        VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
        VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
        VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
        VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
        VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
        VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
    )

    ides {
        recommended()
    }
}
```

実装時は採用中のIntelliJ Platform Gradle Pluginが提供するenum名をコンパイルで確認する。

`COMPATIBILITY_WARNINGS` は初期段階ではfail条件に含めず、reportで確認する。

理由は、warningまでCI failureにするとJetBrains側の軽微なwarning追加で開発を不必要に停止させる可能性があるため。

ただしDeprecated / Experimental / Internal等はwarning扱いであっても0件を必須とする。

---

## 12. Verifier対象IDE

通常CIは `recommended()` を基本とする。

```kotlin
pluginVerification {
    ides {
        recommended()
    }
}
```

最低サポートが2026.2であるため、古い2024.x / 2025.xを互換性のためだけに検証対象へ追加しない。

原則:

1. minimum supported 2026.2 lineを必ず含む
2. 現行最新stableを可能な範囲で含む
3. 次期IDEを検証可能になったらScheduled Compatibility Checkで早期検出する

Verifier IDE範囲を増やすためにproduction codeへversion branchを追加することはしない。

---

## 13. Test Report Artifact

テスト失敗時はログだけに依存しない。

```yaml
- name: Upload test reports
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: test-reports
    path: |
      **/build/reports/tests/**
      **/build/test-results/**
    if-no-files-found: ignore
    retention-days: 7
```

GitHub Actions log取得に問題があっても、JUnit XML / HTML reportから原因を追える状態にする。

---

## 14. Plugin Verifier Report Artifact

Verifier reportは成功時も保存する。

```yaml
- name: Upload verifier reports
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: plugin-verifier-reports
    path: plugin/build/reports/pluginVerifier/**
    if-no-files-found: ignore
    retention-days: 14
```

CIがgreenでも、warning増加の確認やIDE更新前後の比較に利用できる。

---

## 15. Distribution Artifact

通常PRではZIPをuploadしない。

`buildPlugin` とZIP存在確認のみ行う。

理由:

- PRごとにbinary artifactを保持する必要がない
- Artifact storageを消費する
- CIの目的はreleaseではなくbuildabilityの証明

ただし `workflow_dispatch` では、手動確認用candidateとしてZIPをArtifactへ保存してよい。

```yaml
- name: Upload candidate distribution
  if: github.event_name == 'workflow_dispatch'
  uses: actions/upload-artifact@v4
  with:
    name: folder-tabs-candidate
    path: plugin/build/distributions/*.zip
    if-no-files-found: error
    retention-days: 7
```

これはMarketplace公開ではなく単なるCI成果物である。

---

## 16. Draft PR

Draft PRでは重いCIを走らせない。

```yaml
if: github.event_name != 'pull_request' || github.event.pull_request.draft == false
```

`ready_for_review` になった時点で必須CIを開始する。

ただし、開発中でも必要なら `workflow_dispatch` で任意に実行できる。

---

## 17. Timeout

通常Quality Gate:

```text
60 minutes
```

を初期値とする。

Plugin Verifierが複数IDEをdownloadするため、30分ではnetwork状況によって不安定になる可能性がある。

実績が十分短ければ後から45分へ縮める。

Timeoutを延ばしてtest hangを隠すことはしない。

---

## 18. compatibility.yml

### 18.1 目的

コード変更がなくてもJetBrains側では次が変化する。

- 新しいIDE patch release
- 次期IDE release / EAP
- API annotation変更
- Plugin Verifier更新
- IntelliJ Platform dependency metadata更新

そのためPR CIだけでは「将来壊れる兆候」を検出できない。

定期Compatibility Workflowで検出する。

### 18.2 Schedule

初期値は週1回とする。

```yaml
on:
  schedule:
    - cron: '0 18 * * 0'
  workflow_dispatch:
```

これは日曜18:00 UTC、JSTでは月曜03:00頃に相当する。

時刻自体に意味はなく、GitHub側のschedule遅延は許容する。

### 18.3 実行内容

```text
checkout
JDK 25
Gradle setup
build
buildPlugin
verifyPlugin
upload verifier report
```

通常CIと同じStable Public API gateを適用する。

`recommended()` が新しい対象IDEを選択するようになった場合、それをコード変更なしで検出できる。

次期IDE / EAPを明示的に追加する場合は、通常PR CIではなくまずcompatibility workflow側へ追加する。

EAPの一時的不具合で通常開発を完全停止させないためである。

---

## 19. Scheduled Check失敗時の扱い

Scheduled Compatibility Checkが失敗しても自動修正・自動publishは行わない。

失敗内容を分類する。

```text
A. 現行サポートIDEでfailure
   -> mainの品質問題。優先修正。

B. 次期stableでfailure
   -> 次回IDE対応として修正。

C. EAPのみfailure
   -> API変更を調査。正式releaseまで監視。

D. Network / download failure
   -> rerun。production codeは変更しない。
```

Plugin Verifierを通すためだけにInternal / Deprecated APIへfallbackすることは禁止する。

---

## 20. CI Fail条件

次のいずれかでCI failureとする。

- Gradle project configuration error
- compile failure
- unit test failure
- platform test failure
- distribution ZIP生成失敗
- plugin structure error
- compatibility problem
- deprecated API usage >= 1
- scheduled-for-removal API usage >= 1
- experimental API usage >= 1
- internal API usage >= 1
- OverrideOnly / NonExtendable contract violation >= 1
- missing dependency
- invalid plugin

`continue-on-error: true` でこれらを握りつぶさない。

---

## 21. CIでFailさせないもの

初期段階では次はreport / warningとして扱う。

- Plugin Verifier compatibility warning
- 次期EAPのみの不具合
- cosmetic warning

ただしwarning内容が将来の破壊変更を示す場合はIssue化し、必要に応じてfailure policyへ昇格する。

---

## 22. Branch Protection推奨

mainをPR運用する場合、GitHub Branch Protection / RulesetでCIをRequired Checkにする。

必須check名例:

```text
CI / Quality Gate
```

これによりCI failureのままmainへmergeされることを防ぐ。

設計書変更だけのPRは`paths-ignore`によりcheckが作成されない可能性があるため、Required Check設定時はGitHubのskip semanticsを確認する。

必要ならdocs-only変更でも軽量jobだけ成功させる構成へ変更する。

---

## 23. Workflow Security

CIでは外部から渡されるsecretを必要としない。

原則:

- `permissions: contents: read`
- Marketplace tokenなし
- repository write permissionなし
- PR由来コードにsecretを渡さない
- `pull_request_target` を使用しない
- third-party Action追加は必要最小限
- Actionはmajor versionを固定

公開repositoryであるため、Fork PRでも安全にbuild/testできる構成を維持する。

---

## 24. キャッシュ方針

Gradle official Actionのcacheを利用する。

キャッシュ対象を過度に広げない。

特にIDE extracted transformsは除外する。

理由:

- disk / cache容量が大きい
- 再生成可能
- Repo Lens / Flow Lensでdisk pressureの原因になった実績がある

CI不具合の調査時にはcache無効runを行えるようにする。

---

## 25. buildSearchableOptions

Folder TabsがSettings / Configurableを追加しない段階では、`buildSearchableOptions = false` を検討する。

Settings画面を追加した場合は、そのConfigurableが検索対象になる必要性を確認して有効化する。

単にCIを速くするために必要なsearchable options生成を無条件でskipしてはならない。

---

## 26. Headless Test方針

Folder TabsはSwing / Editor Header UIを扱うが、可能な限りModel / ControllerをUIから分離し、通常のheadless testで検証する。

CIで特にテストする対象:

- Minimal Unique Path
- directory grouping
- deterministic sort
- open / close eventによるmodel更新
- selection synchronization
- rename / move後の再構築
- invalid VirtualFile fallback
- lifecycle / dispose

Pixel単位のUI renderingをCIの必須条件にはしない。

Light / Dark / Scale / Split Editor等のvisual確認はManual UI Testとして残す。

---

## 27. UI Testを初期CIへ入れない理由

IntelliJのUI automationは通常testより重く、display / focus / timing依存でflakyになりやすい。

Folder Tabs v0.1では、CI品質を上げるためにまず次を優先する。

1. Model unit test
2. IntelliJ Platform integration test
3. Plugin Verifier
4. Manual UI test

UI test導入は、手動で繰り返し発生する回帰が見つかった時点で追加する。

「CI項目数を増やす」ことより「失敗が信頼できるCI」を優先する。

---

## 28. 実装時のci.yml概形

```yaml
name: CI

on:
  push:
    branches: [main]
    paths-ignore:
      - '**.md'
      - 'plan/**'
      - 'docs/**'
      - '.gitignore'
  pull_request:
    types: [opened, synchronize, reopened, ready_for_review]
    paths-ignore:
      - '**.md'
      - 'plan/**'
      - 'docs/**'
      - '.gitignore'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  quality-gate:
    name: Quality Gate
    if: github.event_name != 'pull_request' || github.event.pull_request.draft == false
    runs-on: ubuntu-latest
    timeout-minutes: 60

    steps:
      - name: Free disk space
        run: |
          sudo rm -rf /usr/share/dotnet /usr/local/lib/android /opt/ghc \
            /usr/local/.ghcup /opt/hostedtoolcache/CodeQL
          sudo docker image prune --all --force
          df -h /

      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-home-cache-excludes: |
            caches/**/transforms

      - name: Verify project configuration
        run: ./gradlew verifyPluginProjectConfiguration --no-daemon --stacktrace

      - name: Build and test
        run: ./gradlew build --no-daemon --stacktrace

      - name: Build plugin distribution
        run: ./gradlew buildPlugin --no-daemon --stacktrace

      - name: Verify plugin structure
        run: ./gradlew verifyPluginStructure --no-daemon --stacktrace

      - name: Assert distribution exists
        run: test -n "$(find plugin/build/distributions -name '*.zip' -print -quit)"

      - name: Plugin Verifier
        run: ./gradlew :plugin:verifyPlugin --no-daemon --stacktrace

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            **/build/reports/tests/**
            **/build/test-results/**
          if-no-files-found: ignore
          retention-days: 7

      - name: Upload verifier reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: plugin-verifier-reports
          path: plugin/build/reports/pluginVerifier/**
          if-no-files-found: ignore
          retention-days: 14

      - name: Upload candidate distribution
        if: github.event_name == 'workflow_dispatch'
        uses: actions/upload-artifact@v4
        with:
          name: folder-tabs-candidate
          path: plugin/build/distributions/*.zip
          if-no-files-found: error
          retention-days: 7
```

これは設計上の概形であり、実装時は実際のmodule構成・Gradle task dependencyを確認して不要な重複taskを整理する。

---

## 29. compatibility.yml概形

```yaml
name: Compatibility

on:
  schedule:
    - cron: '0 18 * * 0'
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ${{ github.workflow }}
  cancel-in-progress: true

jobs:
  verify:
    name: Scheduled Plugin Verification
    runs-on: ubuntu-latest
    timeout-minutes: 60

    steps:
      - name: Free disk space
        run: |
          sudo rm -rf /usr/share/dotnet /usr/local/lib/android /opt/ghc \
            /usr/local/.ghcup /opt/hostedtoolcache/CodeQL
          sudo docker image prune --all --force
          df -h /

      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-home-cache-excludes: |
            caches/**/transforms

      - name: Build and test
        run: ./gradlew build --no-daemon --stacktrace

      - name: Plugin Verifier
        run: ./gradlew :plugin:verifyPlugin --no-daemon --stacktrace

      - name: Upload verifier reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: scheduled-plugin-verifier-reports
          path: plugin/build/reports/pluginVerifier/**
          if-no-files-found: ignore
          retention-days: 14
```

---

## 30. リリース前手動確認との関係

リリース前は次の順序を想定する。

```text
1. PR CI green
2. main CI green
3. workflow_dispatchでcandidate build
4. Plugin Verifier report確認
5. candidate ZIPを必要に応じて実機確認
6. version / change notesを確定
7. 開発者が手動でMarketplace公開
```

CIが公開処理を行わないことで、誤tag / token設定 / workflow誤作動による意図しないMarketplace公開を防ぐ。

---

## 31. v1.0 CI受け入れ基準

Folder Tabs v1.0では最低限次を満たす。

### Workflow

- [ ] PRでCIが実行される
- [ ] main pushでCIが実行される
- [ ] docs / planのみでは重いCIをskipできる
- [ ] superseded runがcancelされる
- [ ] draft PRで重いCIを実行しない
- [ ] workflow_dispatchで手動再検証できる
- [ ] weekly compatibility checkが存在する

### Build / Test

- [ ] JDK 25でbuild成功
- [ ] `verifyPluginProjectConfiguration` 成功
- [ ] 全test成功
- [ ] `buildPlugin` 成功
- [ ] distribution ZIPが生成される
- [ ] `verifyPluginStructure` 成功

### API Stability

- [ ] Compatibility problems 0
- [ ] Deprecated API usages 0
- [ ] Scheduled-for-removal API usages 0
- [ ] Experimental API usages 0
- [ ] Internal API usages 0
- [ ] OverrideOnly violations 0
- [ ] NonExtendable violations 0
- [ ] Missing dependencies 0
- [ ] Invalid plugin problems 0

### Diagnostics

- [ ] test failure時にtest reportを取得可能
- [ ] Plugin Verifier reportを成功/失敗に関係なく取得可能
- [ ] manual run時にcandidate ZIPを取得可能

### Release Safety

- [ ] CIにMarketplace publish stepが存在しない
- [ ] CIにMarketplace tokenを必要としない
- [ ] CIにrepository write permissionを与えない

---

## 32. 最終設計判断

Folder TabsのGitHub Actionsは、**自動リリース基盤ではなく、mainへ入るコードの品質証明基盤**とする。

```text
Pull Request / main
        │
        ▼
Project Configuration
        │
        ▼
Build + Test
        │
        ▼
Build Plugin ZIP
        │
        ▼
Plugin Structure
        │
        ▼
Plugin Verifier
        │
        ├── Deprecated = 0
        ├── Experimental = 0
        ├── Internal = 0
        └── Compatibility Problems = 0
        │
        ▼
      GREEN
```

別系統で週次Compatibility Checkを実行し、JetBrains側のIDE / API変更をコード変更前に検出する。

公開は常に人間の明示操作で行い、GitHub ActionsからMarketplaceへ自動publishしない。
