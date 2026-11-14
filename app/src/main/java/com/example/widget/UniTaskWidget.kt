package com.example.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.ui.theme.AccentColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val MAX_PER_SECTION = 8

private val ColorBackground = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1B22))
private val ColorText = ColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFE6E1E9))
private val ColorDim = ColorProvider(day = Color(0xFF6B6B76), night = Color(0xFF9A98A3))
private val ColorDanger = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))

/** The "Today" home screen widget: today's classes and tasks due today. */
class UniTaskWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read everything first, then draw. A failure shows a short message instead of crashing.
        val overview = try {
            TodayOverviewLoader.load(context)
        } catch (_: Exception) {
            null
        }
        provideContent { WidgetContent(context, overview) }
    }

    companion object {
        /** Redraws every placed widget. Safe to call when there is none. Never throws. */
        suspend fun refresh(context: Context) {
            try {
                UniTaskWidget().updateAll(context.applicationContext)
            } catch (_: Exception) {
            }
        }

        /** Same as [refresh] for code that is not already in a coroutine. */
        fun refreshAsync(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { refresh(appContext) }
        }
    }
}

class UniTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UniTaskWidget()
}

private sealed interface WidgetRow {
    data class Section(val title: String) : WidgetRow
    data class ClassLine(val item: WidgetClass) : WidgetRow
    data class TaskLine(val item: WidgetTask) : WidgetRow
    data class Overdue(val count: Int) : WidgetRow
    data class More(val text: String) : WidgetRow
    data class Message(val text: String) : WidgetRow
}

private fun buildRows(data: TodayOverview?): List<WidgetRow> {
    if (data == null) return listOf(WidgetRow.Message("Open UniTask to load today."))
    val rows = ArrayList<WidgetRow>()
    if (data.classes.isNotEmpty()) {
        rows.add(WidgetRow.Section("Classes"))
        data.classes.take(MAX_PER_SECTION).forEach { rows.add(WidgetRow.ClassLine(it)) }
        if (data.classes.size > MAX_PER_SECTION) {
            rows.add(WidgetRow.More("+${data.classes.size - MAX_PER_SECTION} more classes"))
        }
    }
    if (data.tasks.isNotEmpty() || data.overdueCount > 0) {
        rows.add(WidgetRow.Section("Tasks"))
        data.tasks.take(MAX_PER_SECTION).forEach { rows.add(WidgetRow.TaskLine(it)) }
        if (data.tasks.size > MAX_PER_SECTION) {
            rows.add(WidgetRow.More("+${data.tasks.size - MAX_PER_SECTION} more tasks"))
        }
        if (data.overdueCount > 0) rows.add(WidgetRow.Overdue(data.overdueCount))
    }
    if (rows.isEmpty()) rows.add(WidgetRow.Message("Nothing planned today."))
    return rows
}

/** Opens the app. For a personal task it also tells the app which tab to show. */
private fun openAppAction(context: Context, task: WidgetTask? = null): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val taskId = task?.personalTaskId
        if (taskId != null) {
            // The data makes each task's PendingIntent different from the others.
            data = Uri.parse("unitask://widget/task/$taskId")
            putExtra("opened_task_id", taskId)
            putExtra("extra_is_my_task", task?.isMyTask == true)
        } else {
            data = Uri.parse("unitask://widget/open")
        }
    }
    return actionStartActivity(intent)
}

@Composable
private fun WidgetContent(context: Context, data: TodayOverview?) {
    val rows = buildRows(data)
    val accentColor = ColorProvider(
        day = AccentColors.widgetLightColor(context),
        night = AccentColors.widgetDarkColor(context)
    )
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openAppAction(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today",
                style = TextStyle(color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = data?.dateText.orEmpty(),
                style = TextStyle(color = ColorDim, fontSize = 12.sp)
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(rows) { row -> RowContent(context, row) }
        }
    }
}

@Composable
private fun RowContent(context: Context, row: WidgetRow) {
    val accentColor = ColorProvider(
        day = AccentColors.widgetLightColor(context),
        night = AccentColors.widgetDarkColor(context)
    )
    when (row) {
        is WidgetRow.Section -> Text(
            text = row.title.uppercase(),
            modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            style = TextStyle(color = ColorDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )

        is WidgetRow.ClassLine -> {
            val c = row.item
            val dimmed = c.state == ClassState.PAST
            val place = if (c.place.isBlank()) "" else " • ${c.place}"
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(openAppAction(context)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = c.timeText,
                    modifier = GlanceModifier.width(68.dp),
                    style = TextStyle(color = if (dimmed) ColorDim else ColorText, fontSize = 13.sp)
                )
                Text(
                    text = c.code + place,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(
                        color = if (dimmed) ColorDim else ColorText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (c.state == ClassState.NOW) {
                    Text(
                        text = "NOW",
                        style = TextStyle(color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        is WidgetRow.TaskLine -> {
            val t = row.item
            val label = if (t.label.isBlank()) "" else "${t.label}: "
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(openAppAction(context, t)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t.timeText,
                    modifier = GlanceModifier.width(68.dp),
                    style = TextStyle(color = ColorText, fontSize = 13.sp)
                )
                Text(
                    text = label + t.title,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(color = ColorText, fontSize = 13.sp)
                )
            }
        }

        is WidgetRow.Overdue -> Text(
            text = "${row.count} overdue",
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp).clickable(openAppAction(context)),
            style = TextStyle(color = ColorDanger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )

        is WidgetRow.More -> Text(
            text = row.text,
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(openAppAction(context)),
            style = TextStyle(color = ColorDim, fontSize = 12.sp)
        )

        is WidgetRow.Message -> Text(
            text = row.text,
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp).clickable(openAppAction(context)),
            style = TextStyle(color = ColorDim, fontSize = 13.sp)
        )
    }
}

