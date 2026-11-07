package io.github.sol01st.nobrowser.core

/**
 * 内置规则。
 *
 * frequent=true 的应用会作为默认项出现在「应用」列表里；
 * frequent=false 的仅作为模板，用户通过 + 号添加时继承这些规则。
 */
object DefaultRules {

    private const val GENERIC_RULES = """
        { "activityName": "*WebView*", "extrasKey": "ExAuto" },
        { "activityName": "*Browser*", "extrasKey": "ExAuto" },
        { "activityName": "*WebActivity*", "extrasKey": "ExAuto" },
        { "activityName": "ExDatAll", "extrasKey": "ExAuto" }
    """

    const val JSON = """
{
  "list": [
    {
      "appName": "QQ",
      "packageName": "com.tencent.mobileqq",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [ { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url" } ],
      "whiteUrl": [ "qzone.qq.com", "qun.qq.com", "jq.qq.com", "mqq.tenpay.com", "mp.qq.com", "yundong.qq.com" ]
    },
    {
      "appName": "微信",
      "packageName": "com.tencent.mm",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.mm.ui.LauncherUI", "extrasKey": "rawUrl" },
        { "activityName": "*MMWebViewUI", "extrasKey": "ExAuto" }
      ],
      "whiteUrl": [ "open.weixin.qq.com", "weixin.qq.com" ]
    },
    {
      "appName": "QQ邮箱",
      "packageName": "com.tencent.androidqqmail",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.tencent.qqmail.activity.webviewexplorer.*", "extrasKey": "ExAuto" },
        { "activityName": "com.tencent.qqmail.attachment.activity.QQBrowserHandleActivity", "extrasKey": "ExAuto" },
        { "activityName": "com.tencent.qqmail.attachment.activity.WebViewPreviewActivity", "extrasKey": "ExAuto" },
        { "activityName": "com.tencent.qqmail.ftn.activity.FtnWebViewActivity", "extrasKey": "ExAuto" },
        { "activityName": "com.tencent.qqmail.compose.timecapsule2.TimeCapsuleH5Activity", "extrasKey": "ExAuto" }
      ],
      "whiteUrl": [ "mail.qq.com", "exmail.qq.com", "w.mail.qq.com" ]
    },
    {
      "appName": "微博",
      "packageName": "com.sina.weibo",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.sina.weibo.feed.HomeListActivity", "extrasKey": "com_sina_weibo_weibobrowser_url" },
        { "activityName": "com.sina.weibo.weiyou.DMSingleChatActivity", "extrasKey": "com_sina_weibo_weibobrowser_url" },
        { "activityName": "com.sina.weibo.page.NewCardListActivity", "extrasKey": "com_sina_weibo_weibobrowser_url" },
        { "activityName": "com.sina.weibo.feed.DetailWeiboActivity", "extrasKey": "com_sina_weibo_weibobrowser_url" },
        { "activityName": "com.sina.weibo.feed.HomeListActivity", "extrasKey": "ExDat" },
        { "activityName": "com.sina.weibo.feed.DetailWeiboActivity", "extrasKey": "ExDat" }
      ],
      "whiteUrl": [ "card.weibo.com" ]
    },
    {
      "appName": "百度贴吧",
      "packageName": "com.baidu.tieba",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.baidu.tieba.imMessageCenter.im.chat.PersonalChatActivity", "extrasKey": "tag_url" },
        { "activityName": "com.baidu.tieba.pb.pb.main.PbActivity", "extrasKey": "tag_url" }
      ],
      "whiteUrl": []
    },
    {
      "appName": "钉钉",
      "packageName": "com.alibaba.android.rimet",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [
        { "activityName": "com.alibaba.android.rimet.biz.BrowserActivity", "extrasKey": "ExAuto" },
        { "activityName": "*WebView*", "extrasKey": "ExAuto" },
        { "activityName": "*Browser*", "extrasKey": "ExAuto" }
      ],
      "whiteUrl": [ "login.dingtalk.com" ]
    },
    {
      "appName": "哔哩哔哩",
      "packageName": "tv.danmaku.bili",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "bilibili.com", "b23.tv" ]
    },
    {
      "appName": "小红书",
      "packageName": "com.xingin.xhs",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": []
    },
    {
      "appName": "TIM",
      "packageName": "com.tencent.tim",
      "isUse": true, "isTest": false, "isUserBuild": false, "frequent": true,
      "askEachTime": true,
      "rules": [ { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url" } ],
      "whiteUrl": [ "qzone.qq.com", "qun.qq.com", "jq.qq.com", "mqq.tenpay.com", "mp.qq.com" ]
    },
    {
      "appName": "QQ国际版",
      "packageName": "com.tencent.mobileqqi",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url" } ],
      "whiteUrl": []
    },
    {
      "appName": "QQ轻聊版",
      "packageName": "com.tencent.qqlite",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ { "activityName": "com.tencent.mobileqq.activity.QQBrowserDelegationActivity", "extrasKey": "url" } ],
      "whiteUrl": []
    },
    {
      "appName": "知乎",
      "packageName": "com.zhihu.android",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "zhihu.com" ]
    },
    {
      "appName": "抖音",
      "packageName": "com.ss.android.ugc.aweme",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": []
    },
    {
      "appName": "淘宝",
      "packageName": "com.taobao.taobao",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "taobao.com", "tmall.com" ]
    },
    {
      "appName": "京东",
      "packageName": "com.jingdong.app.mall",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "jd.com" ]
    },
    {
      "appName": "闲鱼",
      "packageName": "com.taobao.idlefish",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "goofish.com" ]
    },
    {
      "appName": "支付宝",
      "packageName": "com.eg.android.AlipayGphone",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "alipay.com" ]
    },
    {
      "appName": "美团",
      "packageName": "com.sankuai.meituan",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "meituan.com" ]
    },
    {
      "appName": "快手",
      "packageName": "com.smile.gifmaker",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "kuaishou.com" ]
    },
    {
      "appName": "酷安",
      "packageName": "com.coolapk.market",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "coolapk.com" ]
    },
    {
      "appName": "网易云音乐",
      "packageName": "com.netease.cloudmusic",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "music.163.com" ]
    },
    {
      "appName": "豆瓣",
      "packageName": "com.douban.frodo",
      "isUse": false, "isTest": false, "isUserBuild": false, "frequent": false,
      "askEachTime": true,
      "rules": [ $GENERIC_RULES ],
      "whiteUrl": [ "douban.com" ]
    }
  ]
}
"""
}
