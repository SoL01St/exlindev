# 交接说明 · ExLink-NoBrowser 5.0.2（2026-11-14）

给下一个会话/开发者：当前的可用状态、构建方法、以及已知的坑。

## 当前状态

- 版本：`versionCode 34` / `versionName 5.0.2` / `targetSdk 34` / `compileSdk 37`
- 源码：`/data/local/tmp/eta/exlindev`（Linux 侧 `/workspace/exlindev`）
- git 提交：
  - `4449315` 5.0.2: 入口白名单判定 + 模块日志页 + 中转解包 + 扫码放行
  - `8d94c91` 闲鱼白名单清空
- APK 备份：`/data/local/tmp/eta/nxb-5.0.2-stable.apk`、`/sdcard/Download/nxb-backup-5.0.2/`
- 源码备份：`/data/local/tmp/eta/nxb-5.0.2-stable-src.tar.gz`（含 .git，22MB）

## 已完成的功能（5.0.1 → 5.0.2）

1. 判定模型改为「入口白名单」：`LinkRule.scope = caller/target`
2. 模块日志页（设置 → 运行状态 → 模块日志），含日志总开关
3. 外链中转解包：闲鱼 reminderUrl / QQ 邮箱 xmsafejump / 钉钉通用兜底
4. 白名单支持 `=host` 精确匹配
5. 扫码 / 长按识别二维码按「caller + 调用栈」放行（`skip(scan)`）
6. 默认接管 7 个应用：QQ、微信、QQ邮箱、贴吧、钉钉、TIM、闲鱼
7. `RULES_VERSION = 11`（改动 `DefaultRules.kt` 后必须再 +1，否则老配置不刷新）

## 构建方法（这台机器）

```sh
sh /data/local/tmp/eta/build_flindev.sh
```

产出：`/data/local/tmp/eta/exlindev/app/build/outputs/apk/release/app-release.apk`

环境在 chroot `/data/local/tmp/eta/debian`：
- JDK 17：`/usr/lib/jvm/java-17-openjdk-arm64`
- Android SDK：`/opt/android-sdk`（platforms 36/37，build-tools 36.0.0）
- Gradle：**必须是 `/opt/gradle-8.14.3`**

### 踩过的坑（别重犯）

1. **不能用 Gradle 9.x**。`/opt/gradle-9.9.1` 存在，但 AGP 8.13.2 与 Gradle ≥ 9.6 不兼容（报 `InternalProblems` 已移除）。脚本里曾被人改成 9.9.1，编译必挂。
2. **aapt2 必须走 qemu 包装**。ARM 上跑 x86_64 的 aapt2，需要在 `/root/.gradle/gradle.properties` 里有：
   ```
   android.aapt2FromMavenOverride=/opt/android-sdk/build-tools/36.0.0/aapt2
   ```
   那个 aapt2 是调用 `qemu-x86_64-static` 的包装脚本。
3. **签名**同样在 `/root/.gradle/gradle.properties`：
   ```
   RELEASE_STORE_FILE=/project/exlindev/keystore/release.jks
   RELEASE_STORE_PASSWORD=nixiang2026
   RELEASE_KEY_ALIAS=nixiang
   RELEASE_KEY_PASSWORD=nixiang2026
   ```
   keystore 已被 `.gitignore` 忽略。签名 SHA-256 `75542c3e…`，与线上版本一致，可直接覆盖安装。
4. **`/data/adb` 不要强依赖**。KernelSU 下 `mount --bind /data/adb` 时常失败，编译不需要它；脚本里已去掉这步。
5. **配置热更新**：hook 侧每次 `startActivity` 检查 `/data/local/tmp/nxb_config.json` 的 mtime，变了就重载，不用杀进程。

## 下一步计划（本次交接的原因）

想把模块日志页的右上角菜单从「自绘卡片」换成 Miuix 官方组件：

```kotlin
implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")

WindowIconCascadingDropdownMenu(
    entry = DropdownEntry(items = listOf(/* DropdownItem(...) */)),
) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
```

- 组件在 **`miuix-preference`** 里（`miuix-ui` 没有），文档：
  `http://compose-miuix-ui.github.io/miuix/zh_CN/components/windowiconcascadingdropdownmenu`
- `Window*` 系列走 Dialog，不需要 Scaffold；`Overlay*` 系列需要 Scaffold + MiuixPopupUtils
- `DropdownItem` 支持 `summary`（副标题）、`children`（二级，带 chevron）、`selected`
- **风险**：0.9.0 → 0.9.4 有多次 breaking change，现有 `Scaffold / TopAppBar / BasicComponent / SmallTitle / Switch / Card` 调用点可能要改
- 建议：先只升依赖编译一次看破坏面，再换菜单

## 回退方法

```sh
# 源码回到当前可用状态
cd /data/local/tmp/eta/exlindev && git reset --hard 8d94c91 && git clean -fd app/src

# 或从备份解包
cd /data/local/tmp/eta && tar xzf nxb-5.0.2-stable-src.tar.gz

# 装回可用 APK
pm install -r -d /data/local/tmp/eta/nxb-5.0.2-stable.apk
```

## 日志调试

```sh
su -c "logcat -d -s ExLinkNoBrowser:D | tail -n 200"
```

- `skip(no-entry)` 入口没登记 / `skip(target-mismatch)` 目标不匹配
- `skip(no-url)` 取不到链接（带 `keys=` 说明用了哪个字段）
- `skip(scan)` 扫码识别放行
- `unwrap` 中转解包结果 / `relay-bare` 空壳中转页反查
- `intercept` 命中拦截
