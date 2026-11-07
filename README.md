# 不用内置浏览器打开 · ExLink.NoBrowser

一个 LSPosed 模块：在微信、QQ、TIM、QQ邮箱、钉钉、百度贴吧等应用里点击链接时，把链接从应用内置浏览器里抢出来，交给你自己的浏览器打开。

测试环境 HyperOS4 · Android 17

基于 [xloger/ExLink](https://github.com/xloger/ExLink) 重写，使用 LSPosed 新一代 **libxposed API 102**，界面用 **Miuix** 重做。

- 包名：`io.github.sol01st.nobrowser`
- 版本：5.0.0（versionCode 32）
- minSdk 26 / targetSdk 36 / compileSdk 37
- Xposed API：`io.github.libxposed:api:102.0.0`
- 仓库：<https://github.com/SoL01St/exlindev>

## 截图

<p align="left">
  <img src="docs/screenshot-home.jpg" width="280" alt="首页" />
  <img src="docs/screenshot-edit.jpg" width="280" alt="规则页" />
</p>

## 特性

- **可选弹窗**：开启「每次询问」时点链接弹出选择框，可选用应用内浏览器还是手机浏览器；关闭后点链接直接交给手机浏览器，不再弹窗
- **实时生效**：改完开关不需要杀应用，下一次点链接就按新配置走（拦截前会检查配置文件时间戳）
- **作用域双向同步**：在 LSPosed 里勾选/取消作用域，模块首页会自动同步开关状态；在模块里开关应用也会写回 LSPosed 作用域
- **规则引擎**：Activity 支持 `*` `?` 通配，不区分大小写，同时匹配「发起跳转的 Activity」和「Intent 指向的目标 Activity」
- **取值方式**：`ExAuto`（自动在 data/extras/Bundle 里找第一个 http 链接）、`ExDat`（只读 Intent.data）、或指定 extras key；取不到时自动回退为全文嗅探
- **白名单**：按域名放行，命中白名单的链接保持应用内打开
- **规则导入 / 导出**：JSON 格式（含格式版本号），可从文件恢复
- **备份与恢复**：立即备份、按时间点自选恢复、长按删除单份，自动只保留最近 5 份
- **运行状态**：设置页顶部直接显示 Root 权限与 LSPosed 模块状态，异常时标红点
- **诊断**：一键导出诊断报告（版本 / 机型 / 系统 / 作用域 / 规则统计 / 最近注入日志）
- **检查更新**：关于页可读取 GitHub 最新 Release 并与当前版本比较
- **下拉刷新**：重新扫描已安装应用，新装应用无需重启模块
- **长按移除**：从首页移除应用时保留其规则，重新添加自动恢复；内置应用会提示不能删除
- **主题**：跟随系统 / 浅色 / 深色（独立页面，点选即时生效）

默认接管：QQ、微信、QQ邮箱、百度贴吧、钉钉、TIM。其他应用点右下角 `+` 添加。

## 安装

1. 从 [Releases](../../releases) 下载 APK 安装
2. 在 LSPosed 中启用模块
3. **首次接管某个应用后，需要在 LSPosed 作用域里对该应用重新勾选一次**（LSPosed 对后写入作用域的应用不会即时注入，这是它的机制）
4. 打开一次模块，授予 root 权限（写入作用域和配置文件需要）

## 使用

- 首页开关：开启即接管，同时写入 LSPosed 作用域；关闭则移除
- 点应用进入规则页：可以编辑拦截规则、白名单、每次询问
- 下拉首页：重新扫描应用列表并同步 LSPosed 作用域
- 长按首页某个应用：从首页移除（规则保留，重新添加自动恢复）
- 设置页：
  - **运行状态**：Root 权限、LSPosed 模块状态（未授权 / 未启用会标红点）
  - **外观 → 主题模式**：跟随系统 / 浅色 / 深色
  - **规则**：导出规则、导入规则、立即备份、从备份恢复（自选时间点，长按删除单份）
  - **诊断**：导出诊断报告
  - **关于**：版本信息、检查更新、GitHub 仓库

## 构建

需要：

- JDK 17
- Android SDK Platform 37、Build-Tools 36.0.0
- Gradle 8.14.x + AGP 8.13.x

```bash
# 1. 生成自己的签名
keytool -genkeypair -keystore keystore/release.jks -alias release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass 你的密码 -keypass 你的密码 \
  -dname "CN=ExLink.NoBrowser, O=Dev, C=CN"

# 2. 把签名信息写进 ~/.gradle/gradle.properties（不要提交到仓库）
cat >> ~/.gradle/gradle.properties <<'EOF'
RELEASE_STORE_FILE=/绝对路径/keystore/release.jks
RELEASE_STORE_PASSWORD=你的密码
RELEASE_KEY_ALIAS=release
RELEASE_KEY_PASSWORD=你的密码
EOF

# 3. 构建
./gradlew assembleRelease
```

签名信息全部通过 gradle 属性读取，仓库里不含任何密钥或密码；读不到这些属性时会自动回退到 debug 签名，clone 下来可直接编译调试。

> 在 ARM 设备上本地构建（用 qemu 跑 aapt2）时，需要在 `gradle.properties` 加一行 `android.aapt2FromMavenOverride=/path/to/aapt2`；x86_64 电脑构建不需要。

## 目录结构

```
app/src/main/java/io/github/sol01st/nobrowser/
├── core/       数据模型、内置规则、URL 处理
├── data/       规则存储、LSPosed 作用域管理、主题偏好、诊断、更新检查
├── hook/       libxposed 入口与拦截逻辑（注入宿主进程，不使用 Compose）
└── ui/         Compose + Miuix 界面（首页 / 规则页 / 设置 / 主题模式 / 关于）
app/src/main/resources/META-INF/xposed/
├── java_init.list   入口类
├── module.prop      模块元信息
└── scope.list       推荐作用域
```

## 已知限制

- **微博**：LSPosed 无法把模块注入微博进程（作用域勾选、重启都试过，日志里没有任何注入记录），所以微博用不了，规则保留但不在默认列表
- **首次接管的应用**需要在 LSPosed 里重新勾选一次作用域才能注入，这是 LSPosed 的缓存机制，模块无法绕过
- 作用域写入和配置发布依赖 root（通过内置的 sqlite3 直接维护 LSPosed 数据库与 `/data/local/tmp/nxb_config.json`）
- 在 LSPosed 的模块列表页开关本模块后返回，列表会按启用状态重排、定位到本模块，这是 LSPosed 自身的行为，模块侧无法控制
- 隐藏桌面图标功能已移除：隐藏后 LSPosed 的「运行」按钮会一起失效（两者依赖同一个 launcher 入口），容易把人锁在外面

## 致谢

- [xloger/ExLink](https://github.com/xloger/ExLink)：原始规则与思路
- [libxposed/api](https://github.com/libxposed/api)：现代 Xposed API
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)：Miuix Compose 组件
- [chrisbanes/haze](https://github.com/chrisbanes/haze)：提示条的毛玻璃（背景模糊）效果
- sqlite3 / zlib / readline / ncurses / libandroid-support：来自 Termux 的 ARM64 构建，用于在设备上维护 LSPosed 数据库

## 第三方组件

`app/src/main/assets/nxbin/` 下的二进制来自 [Termux 仓库](https://github.com/termux/termux-packages) 的 ARM64 构建，用于在设备上直接维护 LSPosed 数据库：

| 文件 | 组件 | 许可证 |
| --- | --- | --- |
| `sqlite3` | SQLite 3 | Public Domain |
| `libz.so.1` | zlib | zlib License |
| `libreadline.so.8` | GNU Readline | GPL v3 |
| `libncursesw.so.6` | ncurses | MIT-like |
| `libtinfo.so.6` | ncurses (terminfo) | MIT-like |
| `libandroid-support.so` | libandroid-support | Apache 2.0 |

其中 GNU Readline 为 GPL v3，重新分发包含它的 APK 时请一并遵守其条款。

## License

MIT
