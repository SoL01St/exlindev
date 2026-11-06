package io.github.sol01st.nobrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
fun AppIcon(pkg: String, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val bitmap = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
        }.getOrNull()
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@Composable
fun DialogAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
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
