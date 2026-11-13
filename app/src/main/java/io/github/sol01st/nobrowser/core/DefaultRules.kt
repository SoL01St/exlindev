package io.github.sol01st.nobrowser.core

/**
 * 内置规则：首页默认接管的 7 个应用。
 *
 * 判定思路是「默认放行，只拦登记过的入口」：
 * - scope=caller（入口白名单）：只有这些**发起方**页面里跳出去的链接才进入判定。
 *   扫一扫、钱包账单、服务栏/生活服务、小程序这些必须在应用内完成的，没有登记 → 天然放行，
 *   不需要再维护排除名单。
 * - scope=target（目标容器）：作为附加限制（配了就必须命中）或暂未录到发起方时的精确兜底。
 *   全部写具体类名，不使用 *WebView* / *WebActivity* 这类通配，避免把原生页面一起拦掉。
 *
 * 取值：ExDat = intent.data；ExAuto = data 优先，取不到时在网页容器里嗅探；
 * 其它值按 extras / bundle 的精确 key 取，取不到再退化到 intent.data。
 *
 * 白名单：`qq.com` 匹配自身与子域；`=mail.qq.com` 只匹配该域名本身。
 *
 * 改动本文件后记得提升 RuleStore.RULES_VERSION，老用户下次启动会自动刷新内置规则。
 */
object DefaultRules {

    const val JSON = """
{
  "list": [
    {
      "appName": "QQ",
      "packageName": "com.tencent.mobileqq",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url", "scope": "target" },
        { "activityName": "com.tencent.mobileqq.activity.QQBrowserActivity", "extrasKey": "url", "scope": "target" }
      ],
      "whiteUrl": [ "qzone.qq.com", "qun.qq.com", "jq.qq.com", "mqq.tenpay.com", "mp.qq.com", "yundong.qq.com", "ptlogin2.qq.com", "docs.qq.com", "pay.qq.com" ]
    },
    {
      "appName": "微信",
      "packageName": "com.tencent.mm",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.mm.ui.chatting.ChattingUI", "extrasKey": "rawUrl", "scope": "caller" },
        { "activityName": "com.tencent.mm.ui.LauncherUI", "extrasKey": "rawUrl", "scope": "caller" },
        { "activityName": "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI", "extrasKey": "rawUrl", "scope": "caller" },
        { "activityName": "com.tencent.mm.plugin.sns.ui.SnsCommentDetailUI", "extrasKey": "rawUrl", "scope": "caller" },
        { "activityName": "com.tencent.mm.plugin.sns.ui.SnsBrowseUI", "extrasKey": "rawUrl", "scope": "caller" },
        { "activityName": "*WebViewUI", "extrasKey": "ExDat", "scope": "target" }
      ],
      "whiteUrl": [ "open.weixin.qq.com", "weixin.qq.com", "tenpay.com", "servicewechat.com", "wechatpay.cn" ]
    },
    {
      "appName": "QQ邮箱",
      "packageName": "com.tencent.androidqqmail",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.qqmail.activity.readmail.ReadMailActivity", "extrasKey": "ExAuto", "scope": "caller" },
        { "activityName": "com.tencent.qqmail.activity.webviewexplorer.*", "extrasKey": "ExAuto", "scope": "target" },
        { "activityName": "com.tencent.qqmail.attachment.activity.QQBrowserHandleActivity", "extrasKey": "ExAuto", "scope": "target" },
        { "activityName": "com.tencent.qqmail.attachment.activity.WebViewPreviewActivity", "extrasKey": "ExAuto", "scope": "target" },
        { "activityName": "com.tencent.qqmail.ftn.activity.FtnWebViewActivity", "extrasKey": "ExAuto", "scope": "target" }
      ],
      "whiteUrl": [ "=mail.qq.com", "exmail.qq.com", "w.mail.qq.com" ]
    },
    {
      "appName": "百度贴吧",
      "packageName": "com.baidu.tieba",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.baidu.tieba.pb.pb.main.PbActivity", "extrasKey": "ExAuto", "scope": "caller" },
        { "activityName": "com.baidu.tieba.imMessageCenter.im.chat.PersonalChatActivity", "extrasKey": "ExAuto", "scope": "caller" },
        { "activityName": "com.baidu.tbadk.browser.TBWebContainerActivity", "extrasKey": "ExDat", "scope": "target" }
      ],
      "whiteUrl": []
    },
    {
      "appName": "钉钉",
      "packageName": "com.alibaba.android.rimet",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.alibaba.android.dingtalkim.activities.ChatMsgActivity", "extrasKey": "ExAuto", "scope": "caller" },
        { "activityName": "com.alibaba.lightapp.runtime.activity.CommonWebViewActivity", "extrasKey": "ExDat", "scope": "target" },
        { "activityName": "com.alibaba.android.dingtalkim.views.immerse.ImmerseContainerActivityV2", "extrasKey": "ExDat", "scope": "target" },
        { "activityName": "com.alibaba.android.rimet.biz.BrowserActivity", "extrasKey": "ExDat", "scope": "target" }
      ],
      "whiteUrl": [ "aliwork.com" ]
    },
    {
      "appName": "TIM",
      "packageName": "com.tencent.tim",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url", "scope": "target" },
        { "activityName": "com.tencent.mobileqq.activity.QQBrowserActivity", "extrasKey": "url", "scope": "target" }
      ],
      "whiteUrl": [ "qzone.qq.com", "qun.qq.com", "jq.qq.com", "mqq.tenpay.com", "mp.qq.com", "ptlogin2.qq.com", "docs.qq.com", "pay.qq.com" ]
    },
    {
      "appName": "闲鱼",
      "packageName": "com.taobao.idlefish",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.idlefish.flutterbridge.flutterboost.boost.FishFlutterBoostActivity", "extrasKey": "ExAuto", "scope": "caller" },
        { "activityName": "com.taobao.idlefish.webview.WebHybridActivity", "extrasKey": "ExDat", "scope": "target" },
        { "activityName": "com.taobao.idlefish.webview.WebHybridPopActivity", "extrasKey": "ExDat", "scope": "target" },
        { "activityName": "com.taobao.idlefish.webview.SystemWebViewActivity", "extrasKey": "ExDat", "scope": "target" },
        { "activityName": "com.taobao.idlefish.goosefish.GooseFishWebActivity", "extrasKey": "ExDat", "scope": "target" }
      ],
      "whiteUrl": []
    }
  ]
}
"""
}
