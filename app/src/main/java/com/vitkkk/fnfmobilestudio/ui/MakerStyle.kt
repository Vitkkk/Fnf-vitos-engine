package com.vitkkk.fnfmobilestudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object MakerPalette {
    val Background = Color(0xFF30386D)
    val BackgroundDeep = Color(0xFF22284F)
    val Panel = Color(0xFF243D49)
    val PanelDark = Color(0xFF1B303A)
    val PanelAlt = Color(0xFF2D355F)
    val Header = Color(0xFF62A8B3)
    val HeaderDark = Color(0xFF4D8E9A)
    val Button = Color(0xFFC5D8F2)
    val ButtonText = Color(0xFF35526B)
    val Accent = Color(0xFF536FC4)
    val AccentSoft = Color(0xFF8798E9)
    val Add = Color(0xFF69B34B)
    val Danger = Color(0xFFC74B4D)
    val Warning = Color(0xFFE0B33A)
    val White = Color(0xFFF6F8FF)
    val Muted = Color(0xFFB8C7D6)
    val Grid = Color(0xFF556296)
    val GridStrong = Color(0xFF99A6E1)
}

enum class MakerButtonTone { NORMAL, ACCENT, ADD, DANGER, DARK }

@Composable
internal fun MakerRoot(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MakerPalette.Background),
        content = content
    )
}

@Composable
internal fun MakerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            MakerSquareButton("⌂", onClick = onBack, size = 54.dp, tone = MakerButtonTone.ACCENT)
        }
        Box(
            Modifier
                .weight(1f)
                .height(54.dp)
                .background(MakerPalette.Header, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                title.uppercase(),
                color = MakerPalette.White,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                maxLines = 1
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
internal fun MakerSectionHeader(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(MakerPalette.Header, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(vertical = 7.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.uppercase(),
            color = MakerPalette.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun MakerPanel(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .background(MakerPalette.Panel, RoundedCornerShape(8.dp))
            .border(2.dp, MakerPalette.PanelAlt, RoundedCornerShape(8.dp))
    ) {
        if (title != null) MakerSectionHeader(title)
        Column(
            Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
internal fun MakerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MakerButtonTone = MakerButtonTone.NORMAL,
    enabled: Boolean = true
) {
    val (bg, fg) = when (tone) {
        MakerButtonTone.NORMAL -> MakerPalette.Button to MakerPalette.ButtonText
        MakerButtonTone.ACCENT -> MakerPalette.Accent to MakerPalette.White
        MakerButtonTone.ADD -> MakerPalette.Add to MakerPalette.White
        MakerButtonTone.DANGER -> MakerPalette.Danger to MakerPalette.White
        MakerButtonTone.DARK -> MakerPalette.PanelDark to MakerPalette.White
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = bg.copy(alpha = 0.38f),
            disabledContentColor = fg.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun MakerSquareButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    tone: MakerButtonTone = MakerButtonTone.NORMAL,
    enabled: Boolean = true
) {
    MakerButton(
        text = text,
        onClick = onClick,
        modifier = modifier.size(size),
        tone = tone,
        enabled = enabled
    )
}

@Composable
internal fun MakerTabRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (index == selected) MakerPalette.Header else MakerPalette.PanelAlt,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.uppercase(),
                    color = if (index == selected) MakerPalette.White else MakerPalette.Muted,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun MakerValueStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    bigArrows: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) MakerSectionHeader(label)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MakerPalette.PanelDark, RoundedCornerShape(7.dp))
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MakerSquareButton(if (bigArrows) "◀" else "−", onMinus, size = 44.dp)
            Text(
                value,
                modifier = Modifier.weight(1f),
                color = MakerPalette.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            MakerSquareButton(if (bigArrows) "▶" else "+", onPlus, size = 44.dp)
        }
    }
}

@Composable
internal fun MakerBottomHome(onHome: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .height(58.dp)
            .background(MakerPalette.Button, RoundedCornerShape(8.dp))
            .clickable(onClick = onHome),
        contentAlignment = Alignment.Center
    ) {
        Text("⌂", fontSize = 34.sp, fontWeight = FontWeight.Black, color = MakerPalette.ButtonText)
    }
}

@Composable
internal fun MakerLabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MakerPalette.PanelDark, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), color = MakerPalette.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(value, color = MakerPalette.White, fontWeight = FontWeight.Black)
    }
}
