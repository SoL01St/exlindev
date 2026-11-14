package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeProgressive
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.sol01st.nobrowser.core.LinkRule
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

import android.util.LruCache
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 统一文本组件，显式行高避免大字号下被裁切。 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = if (color == Color.Unspecified) MiuixTheme.colorScheme.onSurface else color,
            fontSize = fontSize,
            lineHeight = fontSize * 1.45f,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Start,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}

/** 点击类操作的统一触感反馈。 */
@Composable
fun rememberTapHaptics(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/** 二级页列表的底部留白：只留系统导航栏高度 + 一点呼吸空间，不再叠加底栏占位。 */
@Composable
fun listBottomPadding(extra: Dp = 12.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + extra

/** 首页底栏总高度（不含系统导航栏 inset）。底栏现在是浮层，列表底部 padding 要让它让开。 */
val AppBottomBarHeight = 64.dp

/** 卡片描边透明度：深色下必须更明显，否则卡片和背景分不开。 */
@Composable
fun cardOutlineColor() = MiuixTheme.colorScheme.onSurface.copy(
    alpha = if (LocalDarkTheme.current) 0.16f else 0.07f,
)

/** 统一卡片容器：Miuix Card + 外层描边（Card 会裁掉自己的描边，所以套一层 Box）。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NxbCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactive = onClick != null || onLongClick != null
    // 浅色下给卡片一层很浅的外投影，让卡片从浅灰背景上「浮」起来；
    // 深色主题不用，否则投影在暗底上会显脏。
    val cardShadow = if (LocalDarkTheme.current) {
        Modifier
    } else {
        Modifier.shadow(
            elevation = 3.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black,
            spotColor = Color.Black,
        )
    }
    Box(
        modifier
            .then(cardShadow)
            .clip(shape)
            .border(0.5.dp, cardOutlineColor(), shape)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            content = content,
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    NxbCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick,
        content = content,
    )
}

/** HyperOS 风格分组卡片：一张卡片内放多行，行间用 GroupDivider 分隔。 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    NxbCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun GroupDivider(startIndent: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = 16.dp),
        thickness = 0.5.dp,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

/** 进程内图标缓存：LazyColumn 反复回收时不再重复走 PackageManager。 */
private val IconCache = object : LruCache<String, ImageBitmap>(128) {}

@Composable
fun AppIcon(pkg: String, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    var bitmap by remember(pkg) { mutableStateOf(IconCache.get(pkg)) }
    LaunchedEffect(pkg) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
                }.getOrNull()?.also { IconCache.put(pkg, it) }
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MiuixTheme.colorScheme.secondaryVariant),
        )
    }
}

/** 统一的对话框容器：交给 Miuix WindowDialog，遮罩、动画、返回手势都由组件处理。 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(content = content)
    }
}

@Composable
fun DialogAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val tap = rememberTapHaptics()
    TextButton(
        text = text,
        onClick = {
            tap()
            onClick()
        },
        minWidth = 64.dp,
        colors = if (primary) {
            ButtonDefaults.textButtonColorsPrimary()
        } else {
            ButtonDefaults.textButtonColors()
        },
    )
}

@Composable
private fun FieldBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun InputDialog(
    title: String,
    placeholder: String = "",
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AppDialog(onDismiss) {
        AppText(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        FieldBox(text, placeholder) { text = it }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogAction("取消", onClick = onDismiss)
            Spacer(Modifier.width(4.dp))
            DialogAction("确定", primary = true, onClick = { if (text.isNotBlank()) onConfirm(text.trim()) })
        }
    }
}

/** 规则的完整编辑对话框：匹配对象 + Activity 通配 + 取值方式。 */
@Composable
fun RuleEditDialog(
    initialActivity: String,
    initialKey: String,
    initialScope: String = LinkRule.SCOPE_TARGET,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var activity by remember { mutableStateOf(initialActivity) }
    var key by remember { mutableStateOf(initialKey) }
    var scope by remember { mutableStateOf(initialScope) }
    AppDialog(onDismiss) {
        AppText("编辑规则", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        AppText(
            "匹配对象",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ScopeChoice(
                text = "入口",
                selected = scope == LinkRule.SCOPE_CALLER,
                modifier = Modifier.weight(1f),
            ) { scope = LinkRule.SCOPE_CALLER }
            ScopeChoice(
                text = "目标",
                selected = scope == LinkRule.SCOPE_TARGET,
                modifier = Modifier.weight(1f),
            ) { scope = LinkRule.SCOPE_TARGET }
        }
        Spacer(Modifier.height(6.dp))
        AppText(
            "入口 = 只有这些页面里点出去的链接才拦（推荐）；目标 = 按打开的网页 Activity 拦。",
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(12.dp))
        AppText(
            "Activity（支持 * 通配）",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        FieldBox(activity, "Activity 类名") { activity = it }
        Spacer(Modifier.height(12.dp))
        AppText(
            "取值方式（ExAuto / ExDat / extras key）",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        FieldBox(key, "ExAuto 或 extras key") { key = it }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogAction("取消", onClick = onDismiss)
            Spacer(Modifier.width(4.dp))
            DialogAction(
                "保存",
                primary = true,
                onClick = {
                    if (activity.isNotBlank() && key.isNotBlank()) {
                        onConfirm(activity.trim(), key.trim(), scope)
                    }
                },
            )
        }
    }
}

/** 规则作用域的单选项：RadioButton + 文字，整行可点。 */
@Composable
private fun ScopeChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        AppText(text, fontSize = 14.sp, maxLines = 1)
    }
}

/** 紧凑确认框：一行说明 + 一个确认按钮，不占屏。 */
@Composable
fun CompactConfirmDialog(
    message: String,
    confirmText: String = "删除",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            AppText(message, fontSize = 15.sp, maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                DialogAction(confirmText, primary = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
fun SimpleDialog(
    title: String,
    text: String,
    confirmText: String = "知道了",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = onDismiss,
) {
    AppDialog(onDismiss) {
        AppText(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            text.split("\n\n").forEach { block ->
                AppText(
                    block,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogAction(confirmText, primary = true, onClick = onConfirm)
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        AppText(value, fontSize = 13.sp, color = valueColor)
    }
}

/**
 * 折叠比例：列表首项滚过 [threshold] 后为 1，用于大标题 → 左上角小标题的过渡。
 */
@Composable
fun rememberCollapseFraction(state: LazyListState, threshold: Dp = 96.dp): Float {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val fraction = remember(state, thresholdPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        }
    }
    return fraction.value
}


/** 固定大标题栏：26sp 左对齐，返回键在左侧底部；有副标题时自动加高。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val barHeight = if (subtitle.isNullOrBlank()) 48.dp else 72.dp
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
            .height(barHeight)
            .padding(start = if (navigationIcon != null) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { navigationIcon() }
        }
        Column(Modifier.weight(1f)) {
            AppText(
                title,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                AppText(
                    subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        if (actions != null) {
            actions()
        }
    }
}

/** 折叠顶栏：50dp 展开 / 52dp 折叠，大标题淡出上移，背景走 haze 毛玻璃。 */
@Composable
fun NxbTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    collapse: Float,
    hazeState: HazeState,
) {
    val expanded = if (subtitle.isNullOrBlank()) 50.dp else 68.dp
    val collapsed = 52.dp
    val contentHeight = expanded + (collapsed - expanded) * collapse
    val bg = MiuixTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .height(contentHeight)
            .background(bg.copy(alpha = 0.86f))
            .hazeEffect(state = hazeState) {
                blurRadius = 24.dp
                backgroundColor = bg.copy(alpha = 0.45f)
                tints = listOf(HazeTint(bg.copy(alpha = 0.45f)))
                noiseFactor = 0.02f
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    ) {
        Column(
            Modifier
                .padding(top = 8.dp)
                .padding(start = if (navigationIcon != null) 56.dp else 20.dp, end = 20.dp)
                .graphicsLayer {
                    alpha = 1f - collapse
                    translationY = -16.dp.toPx() * collapse
                },
        ) {
            AppText(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (!subtitle.isNullOrBlank()) {
                AppText(
                    subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .height(collapsed)
                .padding(start = if (navigationIcon != null) 4.dp else 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { navigationIcon() }
            }
            AppText(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .alpha(collapse),
            )
            if (actions != null) actions()
        }
    }
}
