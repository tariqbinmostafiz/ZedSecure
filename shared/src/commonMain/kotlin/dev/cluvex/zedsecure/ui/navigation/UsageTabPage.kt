package dev.cluvex.zedsecure.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.domain.usage.UsageBucket
import dev.cluvex.zedsecure.domain.usage.UsageCalendar
import dev.cluvex.zedsecure.domain.usage.UsageClock
import dev.cluvex.zedsecure.domain.usage.UsageLedger
import dev.cluvex.zedsecure.domain.usage.UsageRange
import dev.cluvex.zedsecure.domain.usage.UsageStats
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_cancel
import dev.cluvex.zedsecure.shared.resources.action_clear
import dev.cluvex.zedsecure.shared.resources.usage_clear
import dev.cluvex.zedsecure.shared.resources.usage_clear_confirm
import dev.cluvex.zedsecure.shared.resources.usage_day
import dev.cluvex.zedsecure.shared.resources.usage_download
import dev.cluvex.zedsecure.shared.resources.usage_empty
import dev.cluvex.zedsecure.shared.resources.usage_month
import dev.cluvex.zedsecure.shared.resources.usage_note
import dev.cluvex.zedsecure.shared.resources.usage_this_month
import dev.cluvex.zedsecure.shared.resources.usage_this_week
import dev.cluvex.zedsecure.shared.resources.usage_title
import dev.cluvex.zedsecure.shared.resources.usage_today
import dev.cluvex.zedsecure.shared.resources.usage_total
import dev.cluvex.zedsecure.shared.resources.usage_upload
import dev.cluvex.zedsecure.shared.resources.usage_week
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import dev.cluvex.zedsecure.ui.settings.SettingsArts
import dev.cluvex.zedsecure.ui.settings.SettingsHeader
import org.jetbrains.compose.resources.stringResource

@Composable
fun UsageTabPage(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val days by UsageLedger.days.collectAsState()
    var range by rememberSaveable { mutableStateOf(UsageRange.Day.name) }
    val current = UsageRange.valueOf(range)
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val today = remember(days) { UsageClock.todayEpochDay() }
    val buckets = remember(days, current, today) { UsageStats.series(days, current, today) }
    val now = buckets.last()
    val scroll = rememberScrollState()

    Column(modifier.fillMaxSize().padding(contentPadding)) {
        Spacer(Modifier.height(8.dp))
        SettingsHeader(title = stringResource(Res.string.usage_title), art = SettingsArts.Monitor, scroll = scroll)
        Column(
            Modifier.weight(1f).fillMaxWidth().appVerticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val labels = listOf(
                UsageRange.Day to stringResource(Res.string.usage_day),
                UsageRange.Week to stringResource(Res.string.usage_week),
                UsageRange.Month to stringResource(Res.string.usage_month),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = current == value,
                        onClick = { range = value.name },
                        shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                    ) { Text(label) }
                }
            }

            val periodTitle = when (current) {
                UsageRange.Day -> stringResource(Res.string.usage_today)
                UsageRange.Week -> stringResource(Res.string.usage_this_week)
                UsageRange.Month -> stringResource(Res.string.usage_this_month)
            }
            SummaryCard(periodTitle, now)

            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(MaterialTheme.colorScheme.primary, stringResource(Res.string.usage_download))
                    Legend(MaterialTheme.colorScheme.tertiary, stringResource(Res.string.usage_upload))
                }
                Spacer(Modifier.height(8.dp))
                val peak = buckets.maxOf { it.total }
                Text(
                    formatBytes(peak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                UsageChart(buckets, selected) { selected = it }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    buckets.forEach { b ->
                        Text(
                            shortLabel(b),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                selected?.let { i ->
                    val b = buckets[i]
                    Spacer(Modifier.height(10.dp))
                    Text(longLabel(b), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${stringResource(Res.string.usage_total)} ${formatBytes(b.total)}   ↓ ${formatBytes(b.down)}   ↑ ${formatBytes(b.up)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val rows = buckets.reversed().filter { it.total > 0 }
            Card {
                if (rows.isEmpty()) {
                    Text(
                        stringResource(Res.string.usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    rows.forEachIndexed { i, b ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(longLabel(b), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    "↓ ${formatBytes(b.down)}   ↑ ${formatBytes(b.up)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatBytes(b.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Text(
                stringResource(Res.string.usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { confirmClear = true }) { Text(stringResource(Res.string.usage_clear)) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.usage_clear)) },
            text = { Text(stringResource(Res.string.usage_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    UsageLedger.clear()
                    confirmClear = false
                }) { Text(stringResource(Res.string.action_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SummaryCard(title: String, bucket: UsageBucket) {
    Card {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatBytes(bucket.total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text(stringResource(Res.string.usage_download), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("↓ ${formatBytes(bucket.down)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(stringResource(Res.string.usage_upload), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("↑ ${formatBytes(bucket.up)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UsageChart(buckets: List<UsageBucket>, selected: Int?, onSelect: (Int?) -> Unit) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val peak = buckets.maxOf { it.total }.coerceAtLeast(1L).toFloat()

    Canvas(
        Modifier.fillMaxWidth().height(180.dp).pointerInput(buckets, selected) {
            detectTapGestures { pos ->
                val slot = size.width.toFloat() / buckets.size
                val index = (pos.x / slot).toInt().coerceIn(0, buckets.lastIndex)
                onSelect(if (selected == index) null else index)
            }
        },
    ) {
        val slot = size.width / buckets.size
        val barWidth = slot * 0.62f
        val radius = CornerRadius(barWidth / 4f, barWidth / 4f)
        val minBar = 2.dp.toPx()

        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = size.height * (1f - fraction)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        buckets.forEachIndexed { i, b ->
            val x = i * slot + (slot - barWidth) / 2f
            if (selected == i) {
                drawRoundRect(highlight, Offset(i * slot, 0f), Size(slot, size.height), CornerRadius(8.dp.toPx()))
            }
            if (b.total <= 0L) return@forEachIndexed
            val total = (size.height * (b.total / peak)).coerceAtLeast(minBar)
            val down = (size.height * (b.down / peak)).coerceAtMost(total)
            val up = total - down
            if (down > 0f) drawRoundRect(downColor, Offset(x, size.height - down), Size(barWidth, down), radius)
            if (up > 0f) drawRoundRect(upColor, Offset(x, size.height - total), Size(barWidth, up), radius)
        }
    }
}

private fun two(n: Int): String = n.toString().padStart(2, '0')

private fun shortLabel(b: UsageBucket): String {
    val c = UsageCalendar.civil(b.startDay)
    return when (b.range) {
        UsageRange.Day -> c[2].toString()
        UsageRange.Week -> "${c[2]}/${c[1]}"
        UsageRange.Month -> c[1].toString()
    }
}

private fun longLabel(b: UsageBucket): String {
    val s = UsageCalendar.civil(b.startDay)
    val e = UsageCalendar.civil(b.endDay)
    return when (b.range) {
        UsageRange.Day -> "${s[0]}-${two(s[1])}-${two(s[2])}"
        UsageRange.Week -> "${s[0]}-${two(s[1])}-${two(s[2])} – ${e[0]}-${two(e[1])}-${two(e[2])}"
        UsageRange.Month -> "${s[0]}-${two(s[1])}"
    }
}
