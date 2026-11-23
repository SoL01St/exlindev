# 交接说明 · ExLink-NoBrowser（5.0.4 开发中）

给下一个会话 / 开发者的状态说明：现在能跑什么、怎么构建、哪些坑已经踩过、接下来该动哪里。

> 本文档不含任何密钥。签名口令在构建机的 `~/.gradle/gradle.properties`，不在仓库里，也不要写进来。

## 一句话

LSPosed 模块 `io.github.sol01st.nobrowser`：拦掉应用内「用内置浏览器打开」的行为，把链接交给你自己选的外部浏览器。UI 已整套换成 **Miuix 0.9.5-beta01 的玻璃组件**。

## 当前状态（以此为起点，别重复劳动）

- 版本：`versionCode 36` / `versionName 5.0.4` / `minSdk 33` / `targetSdk 34` / `compileSdk 37`（`compileSdkMinor 2`）。
- **版本号按用户要求冻结在 5.0.4**：正式发版前所有改动只提交、不升号、不打 tag、不发 GitHub Release。
- 最新 tag 仍是 `35-5.0.3`；`main` 本地领先 `origin/main` 19 个提交，`origin` 停在 `87ca806`。
- 近期两个关键提交：
  - `6386637` 顶栏玻璃模糊 + 底栏换官方 `GlassNavigationBar`
  - `489d11f` 顶栏按钮 / 对话框 / 下拉 / 菜单换玻璃组件（8 files, +160/−118）

## 构建环境（本机，ARM64 + KernelSU）

| 项 | 值 |
| --- | --- |
| 源码（Linux 侧） | `/workspace/exlindev` |
| 源码（宿主侧，同一个目录） | `/data/local/tmp/eta/exlindev` |
| 构建副本（chroot 内） | `/project/exlindev` = 宿主 `/data/local/tmp/eta/debian/project/exlindev` |
| chroot 根 | `/data/local/tmp/eta/debian` |
| JDK | `/usr/lib/jvm/java-17-openjdk-arm64`（17） |
| Android SDK | `/opt/android-sdk`（platforms `android-37.2`、build-tools `36.0.0`） |
| Gradle | `/opt/gradle-9.9.2`（wrapper 也指向 9.9.2） |
| AGP / Kotlin / CMP | 9.1.1 / 2.4.20 / 1.12.2 |
| Miuix | `0.9.5-beta01`：`miuix-ui`、`miuix-preference`、`miuix-icons`、`miuix-glass`、`miuix-blur`、`miuix-material` |
| 构建脚本 | `/data/local/tmp/eta/build_flindev.sh` = `/workspace/build_flindev.sh` |

AGP 9 需要两个兼容开关（已在仓库 `gradle.properties` 里）：`android.builtInKotlin=false`、`android.newDsl=false`。这两个开关 AGP 10 会移除，到时候必须迁到 AGP 内建 Kotlin。

## 构建步骤（两步，缺一不可）

```sh
# 1) 同步源码到 chroot 内（外层 bind mount 在 chroot 里不可见，必须先 rsync）
rsync -a --delete --exclude build --exclude .gradle --exclude .git --exclude .kotlin \
      --exclude '.upgrade-backup-*' --exclude '*.apk' \
      /workspace/exlindev/ /workspace/debian/project/exlindev/

# 2) 编译（必须用 Android 侧终端，Linux 环境里跑不了 chroot + java）
sh /data/local/tmp/eta/build_flindev.sh
```

产出：`/data/local/tmp/eta/exlindev/app/build/outputs/apk/release/app-release.apk`，单次约 1 分 50 秒～2 分 10 秒（`terminal` 单次上限 180s 刚好够）。装机：`pm install -r <apk>`，然后 `dumpsys package io.github.sol01st.nobrowser | grep -E 'versionCode|versionName'` 确认真的是新包。

**忘了 rsync 就会编译旧代码**——这是本项目最常见的假故障。

### 构建坑

1. **Gradle 9 在 KernelSU chroot 里报 `Mount point not found`**：JDK 的 `LinuxFileStore` 找不到 `/` 的挂载条目。脚本里已经处理：先 `mount -t proc proc $CHROOT/proc`，再在 chroot 内 `mount --bind / /`。自己手敲命令时这两步必须都在。
2. **aapt2 走 qemu 包装**：`~/.gradle/gradle.properties` 里的 `android.aapt2FromMavenOverride` 指向 SDK 的 aapt2 包装脚本，别删。
3. **签名读不到就回退 debug 签名**（`app/build.gradle.kts` 里做了判断），所以别人 clone 下来也能直接编，只是不能覆盖安装线上版本。
4. **Gradle 发行包用镜像**：services.gradle.org 直连约 100 KB/s，`https://mirrors.cloud.tencent.com/gradle/` 约 4.5 MB/s。
5. `isMinifyEnabled = true` 且 `proguard-android-optimize.txt` 带 `-assumenosideeffects`，**release 包里所有 `Log.*` 都会被删掉**，见「调试」。

## UI 架构（改之前必读，否则一改就塌一片）

- **`ui/Components.kt` 的 `NxbPageScaffold(title, scrollBehavior, largeTitle, subtitle, fallbackBarHeight, navigationIcon, actions, overlay, content)`** 是所有页面的统一外壳，六个页面（首页 / 设置 / 添加 / 编辑 / 关于 / 日志）都用它。改布局先改这里。
- **顶栏不在布局流里**：`Scaffold(contentWindowInsets = WindowInsets(0))` 里是 `Box { Box(layerBackdrop){ 页面内容 } ; GlassTopAppBar(浮层) }`。内容从屏幕顶端开始画，靠顶栏 `contentModifier.onSizeChanged` 实测高度给列表留 `contentPadding.top`（fallback 首页 196dp、二级页 186dp）。
  → 这也是 0.9.4 时代玻璃模糊一直失败的根因：那时候顶栏放在 `Scaffold.topBar` 槽里，内容排在顶栏下方，采样层永远是空的。
- **两份采样层，别合并**：
  - `rememberPageBlurBackdrop()` → 供 `GlassTopAppBar` 采样，只录内容层、不含顶栏自身。
  - `barBackdrop` → 供 `GlassNavigationBar` 采样，整页内容 `drawRect(background) + drawContent()`，底栏 / FAB / Snackbar 在层外。
  - 任一采样层把自己也录进去就会出自采样鬼影。
- **`LocalGlassBackdrop`**（`staticCompositionLocalOf<Backdrop?>`，`AppRoot` 提供）：弹层（对话框、下拉、菜单）靠它拿采样源。
- **`NxbPageScaffold` 的 `overlay` 槽**：渲染在 `layerBackdrop` 录制层**外面**，专给玻璃弹层用——在里面才能采样到页面，又不会把弹层自己录进采样层。
- **底栏 + FAB + Snackbar 放在 `Scaffold` 的 content `Box` 内**（底栏 `zIndex(1f)`、Snackbar `zIndex(2f)`），不是放在 Scaffold 外面。原因：`GlassDialog` 走 Compose 覆盖层（由 Scaffold 的 popup host 渲染，`// Measure Popups first (highest z-index)`），放在外面的底栏会压在对话框上。曾经就是这么挡住「立即备份」列表的。
- **折叠**：`MiuixScrollBehavior` + 首页 `PullToRefresh(topAppBarScrollBehavior)` / 二级页 `LazyColumn.nestedScroll`。不要再额外给列表挂 `nestedScroll`，会重复消费导致滚不动。

## 已经踩过的坑（结论直接照用）

1. **`GlassIconDropdownMenu` 在本项目不能用**：它把 `anchorContent` 复制进弹层当 morph 起点，副本上的 `Modifier.glassPopupAnchor` 会执行 `onGloballyPositioned` 并把 anchor 坐标覆盖成左上角，菜单就飞到屏幕左上。改用 `GlassPopup(anchorBounds = 自己记的 Rect, backdrop)` + `GlassPopupItem`，锚点用 `Modifier.onGloballyPositioned { boundsInWindow() }` 记录（注意 `import androidx.compose.ui.layout.boundsInWindow`，漏了就 Unresolved reference）。
2. **`GlassNavigationBar` 内部强制 `fillMaxWidth`**，要紧凑必须给固定宽度，现在是 `Modifier.width(220.dp)`。
3. **`GlassNavigationBar` 传 `backdrop` 会让胶囊四周糊一圈灰黑**：浮层不在采样层里，坐标对不上。现在的底栏玻璃靠 `barBackdrop`，不要临时改成别的层。
4. **不要用 `GlassFloatingActionButton` 或官方 `FloatingActionButton` 做右下角 + 号**：前者会多画一份材质出现蓝色重影；后者的 `Surface` 背景会盖住 `Modifier.border`，深色下圆钮和卡片直接融成一片。现在是自绘 `Box(60dp, CircleShape)`：`shadow(4dp) + clip + background(surfaceContainer) + border(0.5dp, cardOutlineColor())`。
5. **深色 `background` 必须压成纯黑**（`Theme.kt`：`darkColorScheme().copy(background = Color.Black)`）。`GlassTopAppBar` 的 progressive band 固定混 `surface@30%`（Miuix 深色 `surface = #000000`），底色是 Miuix 默认 `#242424` 时会在深灰底上压出一道更黑的长条。
6. **大标题不能当 LazyColumn 的首项否则不折叠**：它必须是列表外的顶层元素，走 `TopAppBar(largeTitle = …)`。
7. **`GlassDialogButton` 里的文字颜色要显式给** `top.yukonga.miuix.kmp.theme.LocalContentColor.current`，否则 Primary 蓝底上对比度不够。
8. **限制 `GroupDivider` / `cardOutlineColor` 的透明度**：现在分别是 0.05 与 0.10/0.04，加深会显得脏。

## 调试技巧

- **release 包没有 `Log.*` 输出**（见上面构建坑 5）。要看状态就弹 Toast 或者写到界面 / SharedPreferences 里，别指望 logcat 里的 `Log.i`。
- **截图坐标会被缩放**：`read_image` 显示的尺寸可能小于真实分辨率（例如 924×2048 实际是 1156×2510），直接拿目测坐标 `input tap` 会点空。先读 PNG 头确认真实尺寸：`od -An -tu1 -j16 -N8 xxx.png`。
- 深浅色切换：`cmd uimode night yes` / `no`。截图放在 `/storage/emulated/0/audit/`。
- 验证安装版本：`dumpsys package io.github.sol01st.nobrowser | grep -E 'versionCode|versionName'`。
- Miuix 0.9.5 源码（查 API 比猜快）解包在 `/workspace/mx095/src/{miuix-ui,miuix-glass,miuix-blur}/`。

## 待办 / 未决

1. **未滚动时顶栏 `GlassIconButton` 几乎看不出来**：`buttonFill` 是白（浅色）/ `#2C2C2C`（深色），画在本来就接近白的 band 上只剩一圈淡描边，滚到内容下面浮起后才明显。这是官方默认行为，是否调（加透明度 / 加深描边）等产品决定。
2. **展开态首帧兜底高度**（首页 196dp、二级页 186dp）在 `onSizeChanged` 回填前会有一帧跳变，用户说「后面再说」。
3. `GlassBottomSheet` 还没用；只有把「添加应用」改成底部升起面板时才需要。
4. 仓库根的 `.upgrade-backup-20261123/`（升级 Gradle 9 之前的完整备份）可以删了，已无引用。

## 接手第一步

1. 确认拿到的是同一台设备（KernelSU + LSPosed，模块已安装并勾选作用域）。
2. `cd /workspace/exlindev && git log --oneline -3` 确认 HEAD 跟文档对得上。
3. 按「构建步骤」跑一遍，确认 `BUILD_FLINDEV_OK`。
4. 装机后至少看四个界面：首页（折叠 / 展开 / 长按菜单）、设置（玻璃下拉、备份对话框）、日志页（三点菜单）、关于页（返回按钮）。
