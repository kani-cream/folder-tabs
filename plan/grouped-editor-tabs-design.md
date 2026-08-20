# Directory Grouped Editor Tabs - 設計書

- **文書状態**: Draft v0.1
- **作成日**: 2026-08-21
- **対象**: IntelliJ Platform / IntelliJ IDEA 2026.2 系を初期基準とする
- **関連要望**: IJPL-186183
- **実装方針**: 公開APIのみ。`@ApiStatus.Internal`、deprecated API、reflection による内部実装アクセスは禁止する。

---

## 1. 目的

IntelliJ IDEA の標準 Editor Tabs では、開いているファイルを「親ディレクトリ単位」でグループ化して表示できない。

本プラグインは、Editor 上部に独自の2段タブUIを追加し、開いているファイルを親ディレクトリ単位で整理する。

基本UIは次の形とする。

```text
[ users ]  [ orders ]  [ auth ]

controller.go | model.go | service.go
-------------------------------------
Editor
```

上段は **Directory Group Tabs**、下段は **File Tabs** とする。

選択中のファイルが `users/service.go` の場合:

```text
[ users ]  [ orders ]  [ auth ]
   active

controller.go | model.go | service.go
                             active
-------------------------------------
Editor
```

狙いは、ファイル名だけが横一列に並ぶ標準Editor Tabsに対して、**「どのディレクトリのファイルを見ているか」を第一階層として明示すること**である。

---

## 2. 設計原則

### 2.1 公開APIのみ使用する

以下は禁止する。

- `FileEditorManagerEx`
- `EditorWindow`
- Editor Tabs の内部コンテナ実装
- `@ApiStatus.Internal` が付与されたAPI
- deprecated API
- reflection による private/internal メンバーアクセス
- IDE内部コンポーネントのComponent Tree探索に依存する実装

IDE更新に対する耐性と JetBrains Marketplace での配布可能性を優先する。

### 2.2 標準Editor Tabsそのものは改造しない

本プラグインは標準 Editor Tabs の表示順、コンテナ、レイアウトを直接変更しない。

独自UIは `FileEditorManager.addTopComponent(FileEditor, JComponent)` を使用して、FileEditor の上部へ追加する。

### 2.3 言語非依存

PSI、UAST、Language Plugin には依存しない。

グループ化の入力は `VirtualFile` のみとする。

そのため Java / Kotlin / Go / JavaScript / TypeScript / Python など、IDEが開ける通常ファイルを同一方式で扱える設計とする。

### 2.4 グループ識別と表示名を分離する

ディレクトリ名 `users` をグループIDにしてはならない。

```text
hoge/users
huga/users
```

は名前が同じでも別ディレクトリであり、必ず別グループとして扱う。

内部識別には親 `VirtualFile` を使用し、表示名のみを後述の Minimal Unique Path で生成する。

---

## 3. スコープ

### 3.1 v1.0で実現すること

- 開いているファイルを親ディレクトリ単位でグループ化
- 上段にDirectory Group Tabsを表示
- 下段に選択中グループのFile Tabsを表示
- 同名ディレクトリを別グループとして保持
- 同名ディレクトリは最小一意パスで表示
- ファイルのopen / close / selectionに追従
- ディレクトリrename / moveに追従
- グループ選択からファイル選択へのナビゲーション
- ファイルタブ選択で通常のIntelliJ Editorを開く
- Editor split が存在しても破綻しない
- Dark / Light Theme でIntelliJ UIに馴染む表示
- 公開APIのみで実装

### 3.2 v1.0では実現しないこと

- 標準Editor Tabs内部の直接変更
- IDE標準タブのPin状態の取得・変更
- Drag & Dropによるタブ並べ替え
- 任意のユーザー定義グループ
- Git branch / module / packageによるグループ化
- PSIを使った意味的な分類
- AI機能
- ファイル解析
- 標準Editor Tabsの設定をプラグイン側から強制変更

---

## 4. UI仕様

## 4.1 基本構造

```text
┌─────────────────────────────────────────────────────┐
│ [ users ]   [ orders ]   [ auth ]                  │  Directory Group Tabs
├─────────────────────────────────────────────────────┤
│ controller.go | model.go | service.go               │  File Tabs
├─────────────────────────────────────────────────────┤
│                                                     │
│                    Editor                           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

UIはEditor上部へ追加する。

### Directory Group Tabs

- 1グループ = 1つの親ディレクトリ
- 選択中ファイルが属するグループを active 表示
- 表示名は Minimal Unique Path
- Tooltip はフルパス
- 長い名前はUIコンポーネント側で省略可能
- グループ数が幅を超えた場合は overflow UI を使用する

### File Tabs

- active group 直下の開いているファイルだけを表示
- ファイル名を表示
- 選択中ファイルを active 表示
- Tooltip はフルパス
- 同一グループ内では親ディレクトリが同じため、通常はファイル名だけで一意になる

---

## 4.2 表示例

### 通常

```text
project/
├ users/
│ ├ controller.go
│ ├ model.go
│ └ service.go
└ orders/
  ├ controller.go
  └ service.go
```

表示:

```text
[ users ] [ orders ]

controller.go | model.go | service.go
```

### 同名ディレクトリ

```text
project/
├ hoge/
│ └ users/
│   ├ controller.go
│   └ service.go
└ huga/
  └ users/
    ├ model.go
    └ repository.go
```

表示:

```text
[ hoge/users ] [ huga/users ]
```

2つの `users` は絶対に統合しない。

### さらに親も同名

```text
project/
├ aaa/hoge/users/
└ bbb/hoge/users/
```

表示:

```text
[ aaa/hoge/users ] [ bbb/hoge/users ]
```

---

## 5. グループ定義

### 5.1 GroupKey

ファイル `VirtualFile` の immediate parent をグループキーとする。

```kotlin
data class DirectoryGroup(
    val directory: VirtualFile,
    val files: List<VirtualFile>,
    val displayName: String,
)
```

論理的には次の関係となる。

```text
GroupKey(file) = file.parent
```

### 5.2 再帰グループ化はしない

次の構造の場合:

```text
users/
├ controller.go
└ dto/
  └ user.go
```

`controller.go` と `dto/user.go` は別グループとする。

```text
[ users ] [ dto ]
```

「上位の機能ディレクトリを推測する」処理は行わない。

理由:

- 言語・フレームワーク非依存を維持できる
- グループ境界が明確
- PSIやModule構造への依存を避けられる
- ユーザーの予想と実装結果が一致しやすい

---

## 6. Minimal Unique Path

同名ディレクトリが存在した場合、表示名に必要最小限の親パスを追加する。

### 6.1 基本アルゴリズム

対象:

```text
hoge/users
huga/users
orders
```

初期候補:

```text
users
users
orders
```

`users` が衝突するため1階層追加する。

```text
hoge/users
huga/users
orders
```

この時点で一意なので終了する。

### 6.2 擬似コード

```kotlin
fun resolveDisplayNames(directories: List<VirtualFile>): Map<VirtualFile, String> {
    val result = mutableMapOf<VirtualFile, String>()

    directories.groupBy { it.name }.forEach { (_, sameNameDirs) ->
        if (sameNameDirs.size == 1) {
            val dir = sameNameDirs.single()
            result[dir] = dir.name
            return@forEach
        }

        var depth = 2
        while (true) {
            val candidates = sameNameDirs.associateWith { suffixPath(it, depth) }

            if (candidates.values.distinct().size == sameNameDirs.size) {
                result.putAll(candidates)
                break
            }

            depth++
        }
    }

    return result
}
```

実装ではルート到達時のfallbackを持たせる。

### 6.3 パス基準

可能な場合は Project Base Directory を表示上の基準とする。

```text
/project/backend/api/users
/project/backend/admin/users
```

なら、フル絶対パスではなく:

```text
[ api/users ] [ admin/users ]
```

を優先する。

プロジェクト外ファイルでは VFS の presentable path を基準として同様に最小一意化する。

### 6.4 表示セパレータ

UI上のグループ名はOSにかかわらず `/` を使用する。

```text
api/users
```

Windowsでも `api\users` にはしない。

TooltipではIDEが提供する presentable path をそのまま利用してよい。

---

## 7. ソート規則

v1.0では挙動を固定し、設定項目を増やしすぎない。

### Group Tabs

Minimal Unique Path を自然順・大文字小文字非区別で昇順ソートする。

同値時はフルパスをtie-breakerにする。

### File Tabs

ファイル名を自然順・大文字小文字非区別で昇順ソートする。

同値時はフルパスをtie-breakerにする。

例:

```text
file2.go
file9.go
file10.go
```

を期待順とする。

---

## 8. 選択動作

### 8.1 File Tabを選択

```text
File Tab click
    ↓
VirtualFile取得
    ↓
FileEditorManager.openFile(file, true)
    ↓
IDE標準Editorをfocus
    ↓
selectionChanged
    ↓
Header UIを同期
```

プラグイン自身はEditorを実装しない。

### 8.2 Group Tabを選択

Group Tabだけをクリックした場合、Editorを空状態にはしない。

選択対象は次の優先順位とする。

1. そのグループ内で最後に選択していたファイル
2. 履歴がなければソート順の先頭ファイル

例:

```text
users:
  controller.go
  model.go
  service.go   <- last active
```

`orders` から `users` をクリックすると `service.go` を再選択する。

### 8.3 Last Active File

Project Service内にランタイム状態として保持する。

```kotlin
Map<DirectoryKey, VirtualFile>
```

永続化はv1.0では行わない。

IDE再起動時は現在選択中ファイルとソート順から再構築する。

---

## 9. Open / Close / Selection同期

`FileEditorManagerListener.FILE_EDITOR_MANAGER` を購読する。

主要イベント:

- fileOpened
- fileClosed
- selectionChanged

イベント受信後、個別差分を複雑に管理するのではなく、**現在のopen filesから表示Modelを再構築する方式**を基本とする。

```text
IDE Event
   ↓
requestRefresh()
   ↓
coalesce
   ↓
FileEditorManager.getOpenFiles()
   ↓
GroupBuilder
   ↓
MinimalUniquePathResolver
   ↓
GroupedTabsModel
   ↓
UI update on EDT
```

open file数は通常限定的であり、差分更新より完全再構築の方が実装が単純で壊れにくい。

---

## 10. Rename / Move対応

開いているファイルまたは親ディレクトリがrename / moveされた場合に、グループ名と所属先を更新する。

`VirtualFileManager.VFS_CHANGES` の公開Topicを購読し、関連するVFS変更後にModelを再構築する。

例:

```text
hoge/users
    ↓ rename
hoge/accounts
```

表示:

```text
[ users ]
    ↓
[ accounts ]
```

Moveの場合も親 `VirtualFile` を再評価する。

---

## 11. EditorへのUI挿入

### 11.1 使用API

`FileEditorManager.addTopComponent(FileEditor, JComponent)` を使用する。

FileEditorが開かれた時点で、そのEditorに `GroupedTabsPanel` を関連付ける。

```text
FileEditor
   ├ GroupedTabsPanel
   │   ├ DirectoryGroupTabs
   │   └ FileTabs
   └ Editor Component
```

### 11.2 EditorHeaderRegistry

同一Editorへ重複してUIを追加しないため、Registryを持つ。

```kotlin
class EditorHeaderRegistry {
    private val panels = IdentityHashMap<FileEditor, GroupedTabsPanel>()
}
```

FileEditor破棄時には必ず `removeTopComponent()` と dispose を行う。

### 11.3 UIコンポーネント

JetBrains UIガイドに従い、Editor系タブには `JBEditorTabs` を第一候補とする。

独自描画で標準タブを模倣するより、IntelliJ Platform UIコンポーネントを利用する。

ただし、実装時に2026.2 SDK上でpublic API statusを再確認し、Internal依存が発生する場合は公開Swing/JB UIコンポーネントのみで代替する。

---

## 12. 標準Editor Tabsとの関係

本プラグインは標準Editor TabsをAPIから削除・置換しない。

ユーザーが本プラグインをメインのタブUIとして使用する場合は、IDE標準設定の:

```text
Settings
  > Editor
    > General
      > Editor Tabs
        > Tab placement: None
```

を利用できる。

JetBrains自身もTabless UIを正式な利用方法として案内している。

重要な原則:

- プラグインがこのグローバルIDE設定を勝手に変更しない
- 初回案内で「標準タブを残す」「標準タブを非表示」の2方式を説明する
- 標準タブを残した状態でもプラグインは動作する

### 12.1 Tab placement: None の検証

2026.2ではTabless UI自体がサポートされているが、v0.1実装時に次を実機検証する。

- `getOpenFiles()` が期待するopen file集合を返すこと
- `fileOpened` / `fileClosed` / `selectionChanged` が期待通り通知されること
- Split Editorとの組み合わせでModelが欠落しないこと

この検証に失敗した場合でもInternal APIへは逃げず、標準タブ併用モードをfail-safeとする。

---

## 13. Split Editor

Project全体のopen filesは共通Group Modelとして扱う。

ただしEditor Headerは各FileEditorへ個別に設置される。

```text
Project Group Model
       │
       ├ Header A - Split Left
       └ Header B - Split Right
```

各Headerのactive fileは、そのHeaderが属するEditorの表示ファイルを優先する。

Group/File Tabをクリックした場合は、ユーザーが現在操作しているEditor領域へファイルを開くことを基本挙動とする。

Split制御のために `FileEditorManagerEx` / `EditorWindow` は使用しない。

公開APIだけで現在splitへの確実なopenが保証できないケースでは、IDE標準のopen挙動をそのまま採用する。

---

## 14. Project外ファイル

Scratch、設定ファイル、外部ファイルなど、Project Base Directory外の `VirtualFile` も除外しない。

親ディレクトリを持つ場合は通常通りGroup化する。

```text
~/.config/foo/config.json
```

なら、その親ディレクトリをGroup候補にする。

同名衝突時はpresentable pathを使用して最小一意化する。

親が存在しない特殊 `VirtualFile` は:

```text
[ Other ]
```

へフォールバックする。

---

## 15. Close操作

File TabにはClose操作を用意する。

基本処理:

```kotlin
FileEditorManager.getInstance(project).closeFile(file)
```

注意:

`closeFile()` は指定ファイルのEditorを閉じるため、同一ファイルを複数splitで開いている場合はIDE標準の「特定splitだけ閉じる」と完全には一致しない可能性がある。

v1.0では公開API優先のため、この挙動を仕様とする。

対応UI:

- Close button
- Middle click close

Group単位の `Close Group` はv1.0候補に含めるが、MVPには必須としない。

---

## 16. Pin Tab

IDE標準Editor TabのPin操作に必要なAPIの一部は2026.2時点で `@ApiStatus.Internal` である。

したがって本プラグインは、標準Pin状態を取得・変更しない。

v1.0では:

- custom UIにPin状態を表示しない
- Pin/Unpin操作を実装しない
- 独自Pin機能も実装しない

独自Pinを作るとIDE標準のClose/Tab limit semanticsと乖離するためである。

将来、JetBrainsから公開APIが提供された場合のみ再検討する。

---

## 17. 状態モデル

```kotlin
data class GroupedTabsModel(
    val groups: List<DirectoryGroupModel>,
    val selectedFile: VirtualFile?,
)

data class DirectoryGroupModel(
    val directory: VirtualFile?,
    val displayName: String,
    val fullPath: String,
    val files: List<FileTabModel>,
    val selectedFile: VirtualFile?,
)

data class FileTabModel(
    val file: VirtualFile,
    val displayName: String,
    val fullPath: String,
    val selected: Boolean,
)
```

UIは `GroupedTabsModel` のprojectionとして扱い、UIコンポーネント自身に業務状態を持たせすぎない。

---

## 18. コンポーネント構成

```text
GroupedTabsProjectService
├ OpenFilesSnapshotProvider
├ DirectoryGroupBuilder
├ MinimalUniquePathResolver
├ GroupSortPolicy
├ LastActiveFileTracker
├ GroupedTabsModel
└ EditorHeaderRegistry
    └ GroupedTabsPanel
        ├ DirectoryGroupTabsView
        └ FileTabsView
```

### GroupedTabsProjectService

Project単位の中心Service。

責務:

- listener lifecycle
- model再構築
- last active管理
- Editor Header同期

### OpenFilesSnapshotProvider

`FileEditorManager` から現在のopen filesを取得する。

### DirectoryGroupBuilder

`VirtualFile.parent` 単位でグループ化する。

### MinimalUniquePathResolver

同名Group表示名を解決する。

UIには依存させない。

### EditorHeaderRegistry

FileEditorとHeader Panelのライフサイクルを管理する。

### GroupedTabsPanel

Modelを受け取って描画するだけのUI層。

---

## 19. Refresh戦略

イベントごとの複雑な差分処理は避ける。

```text
multiple events
    ↓
requestRefresh
    ↓
1回にcoalesce
    ↓
full snapshot rebuild
```

EDTを長時間占有しないこと。

open files数を `F`、最大パス深度を `D` とすると、主要処理は概ね:

```text
O(F * D)
```

であり、Editor Tab用途として十分小さい。

初期段階では過度なキャッシュを行わない。

---

## 20. Lifecycle / Dispose

Project ServiceはProject lifecycleに従う。

購読には Message Bus Connection を使用し、Project disposeに連動させる。

UI componentはEditorのclose/disposeで確実に解除する。

禁止:

- staticなProject参照
- staticなFileEditor参照
- disposeされないSwing listener
- Projectを跨ぐ共有state

---

## 21. Settings

v1.0の設定項目は最小限にする。

### 必須

- Enable Directory Grouped Tabs: ON/OFF

### v1.0では固定値

- Grouping: Immediate Parent Directory
- Group sort: Alphabetical
- File sort: Alphabetical
- Duplicate group label: Minimal Unique Path

これらを最初から設定可能にしない。

理由は、設定画面と分岐ロジックを増やさず、主目的を明確にするため。

---

## 22. エッジケース

| ケース | 仕様 |
|---|---|
| 同名ディレクトリ | 別Group。Minimal Unique Pathで区別 |
| 同名ファイル | 同一parentには通常共存不可。VFS上で発生する特殊ケースはfull pathをtie-breakerに使用 |
| Project root直下のファイル | Project rootを1Groupとして扱う |
| Project外ファイル | parent単位で通常Group化 |
| parentなし | `Other` Group |
| directory rename | VFS event後に再構築 |
| file move | 新parent Groupへ移動 |
| directory move | Minimal Unique Pathを再計算 |
| file delete | invalid fileをModelから除外 |
| symbolic / virtual filesystem | `VirtualFile` のpresentable pathを尊重 |
| split editor | Project Model共有。各Headerは個別同期 |
| preview tab | open fileとして観測できる範囲で通常扱い |
| pinned tab | 表示・操作しない |
| Light/Dark切替 | JetBrains UI component/themeに追従 |

---

## 23. エラー方針

本機能はナビゲーション補助であり、Editor本体を壊してはならない。

fail-openではなく **UI拡張部分だけをfail-safeに無効化する**。

例:

- Header追加失敗 → 標準Editorはそのまま利用可能
- 不正 `VirtualFile` → 対象ファイルだけ除外
- path解決失敗 → `directory.name` または `Other` にfallback
- listener例外 → IDE操作をブロックしない

Internal APIへfallbackして機能を維持することは禁止する。

---

## 24. テスト戦略

### 24.1 Unit Test

#### MinimalUniquePathResolver

最低限:

```text
users
orders
```

```text
hoge/users
huga/users
```

```text
aaa/hoge/users
bbb/hoge/users
```

```text
moduleA/src/main/users
moduleB/src/main/users
```

プロジェクト外パスも含める。

#### DirectoryGroupBuilder

- 同一parent
- 異なるparent
- parentなし
- invalid VirtualFile

#### Sort

- alphabetic
- case difference
- numeric suffix
- tie-breaker

### 24.2 Platform Test

- file openでGroup追加
- file closeでGroup削除
- selected file変更でactive更新
- 最後のfileをcloseするとGroup消滅
- directory rename
- file move
- project closeでlistener/panel解放

### 24.3 Manual UI Test

- Light theme
- Dark theme
- 125% / 150% UI scale
- 長いディレクトリ名
- 10+ groups
- 20+ files in one group
- Editor split
- Tab placement: Top
- Tab placement: None
- Scratch / project外file

### 24.4 Compatibility

- IntelliJ Plugin VerifierをCIで実行
- Internal API usage検出を0件にする
- deprecated API usageを0件にする

---

## 25. 実装フェーズ

ステップ数を増やしすぎず、3段階で完了させる。

### v0.1 - Core

- Project Service
- `FileEditorManagerListener`
- open files snapshot
- parent directory grouping
- Minimal Unique Path
- deterministic sort
- `addTopComponent`
- Directory Group Tabs
- File Tabs
- file selection
- group selection + last active
- basic unit tests

**完了条件**:

```text
[ users ] [ orders ]
controller.go | model.go | service.go
```

がEditor直上に表示され、実際のEditor切替まで動く。

### v0.5 - Sync & UX

- file close
- middle click close
- VFS rename / move追従
- overflow
- tooltip
- split editor確認
- Tab placement None確認
- Light/Dark theme確認

**完了条件**:

通常利用で標準Editor Tabsの代替ナビゲーションとして使える。

### v1.0 - Stabilization

- Settings ON/OFF
- lifecycle/dispose精査
- edge case対応
- UI polish
- accessibility確認
- Plugin Verifier
- Marketplace用metadata/documentation

**完了条件**:

Internal / deprecated API 0件、主要操作でUI同期の破綻なし。

---

## 26. 受け入れ基準

以下をすべて満たした時点でv1.0とする。

### Grouping

- [ ] 同一parentのopen filesが同一Groupになる
- [ ] 異なるparentは同名でも別Groupになる
- [ ] `hoge/users` と `huga/users` が別Groupになる
- [ ] 同名GroupはMinimal Unique Pathで識別できる

### Navigation

- [ ] Group clickで対象Groupへ切り替わる
- [ ] File clickで通常Editorが開く
- [ ] Group復帰時にlast active fileを復元する

### Sync

- [ ] file openに追従
- [ ] file closeに追従
- [ ] selection変更に追従
- [ ] renameに追従
- [ ] moveに追従

### UI

- [ ] Editor上部へ表示される
- [ ] Light/Darkで視認性に問題がない
- [ ] 長いlabelでレイアウト崩壊しない
- [ ] overflow時も全Group/Fileへ到達できる

### Safety

- [ ] Internal API不使用
- [ ] deprecated API不使用
- [ ] reflection不使用
- [ ] Editor本体の操作を妨害しない
- [ ] Project close後に参照/leakを残さない

---

## 27. API使用方針

初期実装で中心となる公開API:

```text
com.intellij.openapi.fileEditor.FileEditorManager
com.intellij.openapi.fileEditor.FileEditorManagerListener
com.intellij.openapi.fileEditor.FileEditor
com.intellij.openapi.vfs.VirtualFile
com.intellij.openapi.vfs.VirtualFileManager
com.intellij.openapi.vfs.newvfs.BulkFileListener
com.intellij.util.messages.MessageBus
```

UIではJetBrains Platformの公開UI componentを優先する。

特に次の操作を利用する。

```text
FileEditorManager.getInstance(project)
FileEditorManager.getOpenFiles()
FileEditorManager.openFile(...)
FileEditorManager.closeFile(...)
FileEditorManager.addTopComponent(...)
FileEditorManager.removeTopComponent(...)
FileEditorManagerListener.FILE_EDITOR_MANAGER
```

実装時にSDK上のannotationを必ず確認し、公開状態が変化していた場合は設計を見直す。

---

## 28. 明示的な禁止API / 禁止アプローチ

次のコードへ依存してはならない。

```text
com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
EditorWindow
EditorComposite implementation
EditorTabs implementation
JBTabs implementation internals
```

また、標準Editor TabsのSwing componentを探索して:

```text
findComponent(...)
setVisible(false)
remove(...)
```

のように非表示・差し替えする手法は禁止する。

短期的に動いてもIDE内部構造の変更で破綻するためである。

---

## 29. 技術的な成立性

本設計の成立性は次の公開機能に依存する。

1. `FileEditorManager` はopen files、file open/close、FileEditor上部component追加を公開APIとして提供している。
2. `FileEditorManagerListener` はProject-Level Listenerとして公開されている。
3. IntelliJ Platform UIガイドではEditor系tabsに `JBEditorTabs` を使用する方針が示されている。
4. IntelliJ IDEA自体がEditor Tabを非表示にするTabless UIを正式に提供している。

したがって、標準Editor Tabs内部を書き換えず、独自Group TabsをEditor直上へ追加する方式を採用する。

---

## 30. 参考資料

- JetBrains IntelliJ Platform Plugin SDK - FileEditorManager source/API
  - https://github.com/JetBrains/intellij-community/blob/master/platform/analysis-api/src/com/intellij/openapi/fileEditor/FileEditorManager.java
- JetBrains IntelliJ Platform Plugin SDK - Extension Point and Listener List
  - https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html
- JetBrains IntelliJ Platform UI Guidelines - Tabs
  - https://plugins.jetbrains.com/docs/intellij/tabs.html
- JetBrains Guide - Tabless UI in any JetBrains IDE
  - https://www.jetbrains.com/guide/tips/tabless-ui/
- IntelliJ IDEA Help - Editor Tabs
  - https://www.jetbrains.com/help/idea/settings-editor-tabs.html
- Related request
  - https://youtrack.jetbrains.com/issue/IJPL-186183

---

## 31. 最終設計判断

本プラグインは **「標準Editor Tabsをハックしてフォルダグループを追加するプラグイン」ではない**。

次の構造を公開APIだけで構築する。

```text
                   Open VirtualFiles
                         │
                         ▼
               Parent Directory Grouping
                         │
                         ▼
               Minimal Unique Path
                         │
                         ▼
              ┌─────────────────────┐
              │ [users] [orders]    │
              ├─────────────────────┤
              │ a.go | b.go | c.go  │
              └─────────────────────┘
                         │
                         ▼
               IntelliJ FileEditor
```

同名ディレクトリはフルパス上で別物として保持し、**表示時だけ必要最小限の親パスを追加する**。

```text
hoge/users  → [ hoge/users ]
huga/users  → [ huga/users ]
```

これを本プラグインの中核仕様として固定する。
