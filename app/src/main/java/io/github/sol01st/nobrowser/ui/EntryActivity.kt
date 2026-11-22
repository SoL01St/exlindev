package io.github.sol01st.nobrowser.ui

/**
 * 唯一对外的入口 Activity。
 *
 * manifest 里它带 `android.intent.action.MAIN` +
 * `de.robv.android.xposed.category.MODULE_SETTINGS`，也就是 LSPosed 管理器
 * 解析「模块设置 / 右下角打开」时找的那个组件（见框架的 getSettingsIntent，
 * 解析顺序：MODULE_SETTINGS → CATEGORY_INFO → CATEGORY_LAUNCHER）。
 *
 * 桌面图标（`.Launcher` alias）也指向这里，所以隐藏桌面图标之后
 * LSPosed 里的入口不受影响。真正的界面仍是 [MainActivity]，这里只是转发入口。
 */
class EntryActivity : MainActivity()
