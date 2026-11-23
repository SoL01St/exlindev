package io.github.sol01st.nobrowser.ui

import top.yukonga.miuix.kmp.basic.ScrollBehavior
import androidx.compose.foundation.Image
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

import android.util.LruCache
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole

/**
 * 页面玻璃采样源：玻璃对话框 / 玻璃弹层用它采样背后那层内容。
 *
 * 由 AppRoot 提供（录制整页、不含浮层自身）；不在录制层里时为 null，
 * glass 组件会自动退回不透明材质，避免"把自己录进去"的自采样。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

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


/** 卡片描边透明度：深色下必须更明显，否则卡片和背景分不开。 */
@Composable
fun cardOutlineColor() = MiuixTheme.colorScheme.onSurface.copy(
    alpha = if (LocalDarkTheme.current) 0.10f else 0.04f,
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
            elevation = 1.dp,
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
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
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

/** 统一的对话框容器：交给 Miuix GlassDialog，玻璃材质、遮罩、动画、返回手势都由组件处理。 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = LocalGlassBackdrop.current,
    ) {
        Column(content = content)
    }
}

@Composable
fun DialogAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val tap = rememberTapHaptics()
    GlassDialogButton(
        onClick = {
            tap()
            onClick()
        },
        role = if (primary) GlassDialogButtonRole.Primary else GlassDialogButtonRole.Normal,
        minWidth = 60.dp,
        insideMargin = PaddingValues(horizontal = 20.dp),
    ) {
        AppText(text, fontSize = 15.sp, color = LocalContentColor.current)
    }
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
    AppDialog(onDismiss) {
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
 * 顶栏：内容区左侧大标题，上滑后折叠成中间小标题。
 *
 * 用的是 Miuix 官方 [TopAppBar]（KernelSU 的顶栏也是这套：TopAppBar + MiuixScrollBehavior），
 * 折叠比例、标题切换、弹簧动画全在组件内部，调用方只负责把 scrollBehavior 接到滚动容器上。
 *
 * 结构上顶栏**不在布局流里**：调用方把它当浮层叠在内容之上（见各页的 `Box` 叠层），
 * 内容从屏幕顶端开始画、靠实测的 `barHeight` 留白。这样滚动时内容会从顶栏底下穿过去 ——
 * 后面换成 `GlassTopAppBar` + `progressiveBlur` 时才有东西可采样。
 */
@Composable
fun NxbPageScaffold(
    title: String,
    scrollBehavior: ScrollBehavior,
    largeTitle: String = title,
    subtitle: String = "",
    fallbackBarHeight: Dp = 186.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** 玻璃弹层槽位：画在采样层**外面**，否则弹层会把自己录进背景（自采样发白）。参数是页面采样源。 */
    overlay: (@Composable BoxScope.(Backdrop?) -> Unit)? = null,
    content: @Composable (Dp) -> Unit,
) {
    // 顶栏自己的采样源：只录内容层（不含顶栏自身，否则玻璃会把自己录进去成鬼影）
    val backdrop = rememberPageBlurBackdrop()
    var barHeightPx by remember { mutableIntStateOf(0) }
    val measured = with(LocalDensity.current) { barHeightPx.toDp() }
    val barHeight = if (barHeightPx > 0) measured else fallbackBarHeight

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            // 内容全屏：滚动时从顶栏底下穿过，玻璃才有东西可采样
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                content(barHeight)
            }
            GlassTopAppBar(
                title = title,
                largeTitle = largeTitle,
                subtitle = subtitle,
                backdrop = backdrop,
                progressiveBlur = true,
                scrollBehavior = scrollBehavior,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                // 测**前景**高度（不含背景带向外延伸的 bandOverhang），内容留白靠它
                contentModifier = Modifier.onSizeChanged { barHeightPx = it.height },
                navigationIcon = { navigationIcon?.invoke() },
                actions = { actions?.invoke(this) },
            )
            // 弹层在 layerBackdrop 之外：采样的是页面内容，不是它自己
            overlay?.invoke(this, backdrop)
        }
    }
}

/**
 * 页面级模糊采样层：只录不透明底色 + 内容。
 *
 * `drawRect(surface)` 先铺不透明底：录制内容若有透明区域，模糊会把颜色扩散到透明像素、
 * 出现色块（官方指南要求）。运行时不支持着色器时返回 null，玻璃顶栏自动退回不透明底色。
 */
@Composable
private fun rememberPageBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val surface = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
}
