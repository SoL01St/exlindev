package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
val AppBottomBarHeight = 76.dp

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

/** 统一的对话框容器：圆角卡片 + 统一内边距，替代 Miuix Card 以避免内容被裁。 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 直接用 Animatable + graphicsLayer：不翻转可见态、不触发布局测量，只改绘制属性，
    // 弹窗首帧即为动画起点，避免「先重组一次再开始动画」造成的卡顿。
    val scale = remember { Animatable(0.94f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, tween(durationMillis = 150, easing = LinearOutSlowInEasing)) }
        launch { fade.animateTo(1f, tween(durationMillis = 110, easing = LinearOutSlowInEasing)) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = fade.value
                }
                .clip(RoundedCornerShape(26.dp))
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f))
                .border(
                    0.5.dp,
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(26.dp),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@Composable
fun DialogAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val tap = rememberTapHaptics()
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        AppText(
            text,
            fontSize = 15.sp,
            fontWeight = if (primary) FontWeight.Medium else null,
            color = if (primary) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

@Composable
private fun FieldBox(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.secondaryVariant)
            .border(
                1.dp,
                MiuixTheme.colorScheme.onSurface.copy(
                    alpha = if (LocalDarkTheme.current) 0.30f else 0.20f,
                ),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    AppText(
                        placeholder,
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                inner()
            },
        )
    }
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

/** 规则的完整编辑对话框：Activity 通配 + 取值方式。 */
@Composable
fun RuleEditDialog(
    initialActivity: String,
    initialKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var activity by remember { mutableStateOf(initialActivity) }
    var key by remember { mutableStateOf(initialKey) }
    AppDialog(onDismiss) {
        AppText("编辑规则", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        AppText(
            "Activity（支持 * 通配）",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        FieldBox(activity, "例如 *WebView*") { activity = it }
        Spacer(Modifier.height(12.dp))
        AppText(
            "取值方式（ExAuto / ExDat / extras key）",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        FieldBox(key, "例如 ExAuto 或 rawUrl") { key = it }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogAction("取消", onClick = onDismiss)
            Spacer(Modifier.width(4.dp))
            DialogAction(
                "保存",
                primary = true,
                onClick = {
                    if (activity.isNotBlank() && key.isNotBlank()) {
                        onConfirm(activity.trim(), key.trim())
                    }
                },
            )
        }
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(
                    0.5.dp,
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(24.dp),
                )
                .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        ) {
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
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(color))
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


/** 固定大标题栏：不折叠，26sp 粗体贴底；有副标题时自动加高。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val barHeight = if (subtitle == null) 46.dp else 66.dp
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
            .height(barHeight)
            .padding(start = if (navigationIcon != null) 8.dp else 20.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (navigationIcon != null) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.BottomCenter) { navigationIcon() }
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
    }
}

/** 常态栏内容高度（不含状态栏），供内容顶部留白使用。 */
val HeaderBarHeight: Dp = 42.dp

/** 顶部常态栏：小标题左对齐，随折叠比例淡入；不透明底色，避免滚动正文透上来与标题重叠。 */
@Composable
fun SmallHeaderBar(
    title: String,
    alpha: Float,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(
                start = if (navigationIcon != null) 0.dp else 20.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
        }
        AppText(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .alpha(alpha),
        )
    }
}

/** 列表首项：左对齐大标题块，向上滚出屏幕。 */
@Composable
fun LargeTitleBlock(
    title: String,
    subtitle: String? = null,
    startPadding: Dp = 20.dp,
    alpha: Float = 1f,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(start = startPadding, end = 20.dp, top = 4.dp, bottom = 14.dp),
    ) {
        AppText(title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            AppText(
                subtitle,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
