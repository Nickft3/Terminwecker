package de.terminwecker.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.terminwecker.R
import de.terminwecker.data.CalendarView
import de.terminwecker.data.EventInstance
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** So weit lässt sich im Zeitraster zurückblättern; der ViewModel lädt passend dazu. */
internal const val GRID_LOOKBACK_DAYS = 56L
private const val GRID_LOOKAHEAD_DAYS = 400L

private val HourHeight = 52.dp
private val GutterWidth = 44.dp
private val AllDayLaneHeight = 22.dp
private const val MIN_EVENT_MINUTES = 25
private const val STACK_MINUTES = 30
private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val ALARM_ICON = "alarm"

/** Ordnet Pager-Seiten Tagen zu. Die Woche beginnt montags, Tag und 3 Tage starten bei heute. */
private class GridPaging(view: CalendarView, today: LocalDate) {
    val days = view.days
    val anchor: LocalDate = if (view == CalendarView.WEEK) today.with(DayOfWeek.MONDAY) else today
    val initialPage = ceilDiv(GRID_LOOKBACK_DAYS, days)
    val pageCount = initialPage + ceilDiv(GRID_LOOKAHEAD_DAYS, days) + 1

    fun firstDay(page: Int): LocalDate = anchor.plusDays((page - initialPage).toLong() * days)

    fun days(page: Int): List<LocalDate> = firstDay(page).let { first -> List(days) { first.plusDays(it.toLong()) } }

    fun pageOf(date: LocalDate): Int =
        (initialPage + Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), days.toLong()).toInt()).coerceIn(0, pageCount - 1)

    private fun ceilDiv(a: Long, b: Int): Int = ((a + b - 1) / b).toInt()
}

/** Termine pro Kalendertag, getrennt nach Zeitraster und Ganztags-Zeile. */
private class DayIndex(events: List<EventInstance>) {
    val timed = HashMap<LocalDate, MutableList<EventInstance>>()
    val allDay = HashMap<LocalDate, MutableList<EventInstance>>()

    init {
        for (inst in events) {
            val target = if (inst.allDay || inst.end - inst.begin >= DAY_MS) allDay else timed
            val (from, to) = coveredDays(inst)
            for (day in from..minOf(to, from + 400)) target.getOrPut(LocalDate.ofEpochDay(day)) { mutableListOf() }.add(inst)
        }
    }
}

/** Erster und letzter belegter Tag als Epoch-Day; das Ende ist exklusiv gespeichert. */
private fun coveredDays(inst: EventInstance): Pair<Long, Long> {
    val start = inst.localStart().toLocalDate().toEpochDay()
    val end = if (inst.allDay) inst.localEnd().toLocalDate().toEpochDay() - 1
    else inst.localEnd().minusNanos(1).toLocalDate().toEpochDay()
    return start to max(start, end)
}

/** Ein Termin im Zeitraster eines Tages, in Minuten ab Mitternacht. */
private class Placed(val inst: EventInstance, start: Int, end: Int) {
    val visEnd = min(24 * 60, max(end, start + MIN_EVENT_MINUTES))
    val visStart = min(start, visEnd - MIN_EVENT_MINUTES)
    var col = 0
    var cols = 1

    /** > 0: eingerückt über einem früher beginnenden Termin gezeichnet statt daneben. */
    var indent = 0

    fun overlaps(other: Placed) = visStart < other.visEnd && other.visStart < visEnd
}

/**
 * Überlappende Termine anordnen wie in üblichen Kalender-Apps: Beginnen sie mindestens
 * [STACK_MINUTES] später, liegen sie eingerückt über dem früheren Termin (dessen Titel bleibt lesbar),
 * sonst teilen sie sich die Breite in Spalten.
 */
private fun layoutDay(day: LocalDate, events: List<EventInstance>): List<Placed> {
    val placed = events.map { inst ->
        val s = inst.localStart()
        val e = inst.localEnd()
        val start = if (s.toLocalDate().isBefore(day)) 0 else s.hour * 60 + s.minute
        val end = if (e.toLocalDate().isAfter(day)) 24 * 60 else e.hour * 60 + e.minute
        Placed(inst, start, max(start, end))
    }.sortedWith(compareBy({ it.visStart }, { -it.visEnd }))

    val cluster = mutableListOf<Placed>()
    val colEnds = mutableListOf<Int>()
    var clusterEnd = -1
    fun flush() {
        val stackable = cluster.indices.all { i ->
            (0 until i).all { j -> !cluster[i].overlaps(cluster[j]) || cluster[i].visStart - cluster[j].visStart >= STACK_MINUTES }
        }
        if (stackable) {
            cluster.forEachIndexed { i, p ->
                p.col = 0
                p.indent = (0 until i).filter { cluster[it].overlaps(p) }.maxOfOrNull { cluster[it].indent + 1 } ?: 0
            }
        } else {
            cluster.forEach { it.cols = colEnds.size }
        }
        cluster.clear()
        colEnds.clear()
    }
    for (p in placed) {
        if (cluster.isNotEmpty() && p.visStart >= clusterEnd) flush()
        val free = colEnds.indexOfFirst { it <= p.visStart }
        if (free >= 0) {
            p.col = free
            colEnds[free] = p.visEnd
        } else {
            p.col = colEnds.size
            colEnds += p.visEnd
        }
        cluster += p
        clusterEnd = max(clusterEnd, p.visEnd)
    }
    flush()
    return placed
}

/** Ganztägiger oder mehrtägiger Termin als Balken über die Spalten [from]..[to]. */
private class Span(val inst: EventInstance, val from: Int, val to: Int) {
    var lane = 0
}

private fun allDaySpans(days: List<LocalDate>, index: DayIndex): List<Span> {
    val first = days.first().toEpochDay()
    val last = days.last().toEpochDay()
    val spans = days.flatMap { index.allDay[it].orEmpty() }
        .distinctBy { it.key }
        .map { inst ->
            val (s, e) = coveredDays(inst)
            Span(inst, (max(s, first) - first).toInt(), (min(e, last) - first).toInt())
        }
        .sortedWith(compareBy({ it.from }, { -(it.to - it.from) }, { it.inst.title }))
    val laneEnds = mutableListOf<Int>()
    for (span in spans) {
        val lane = laneEnds.indexOfFirst { it < span.from }
        if (lane >= 0) {
            span.lane = lane
            laneEnds[lane] = span.to
        } else {
            span.lane = laneEnds.size
            laneEnds += span.to
        }
    }
    return spans
}

/**
 * Zeitraster für 1, 3 oder 7 Tage mit Stundenachse, Ganztags-Zeile und Jetzt-Linie.
 * Wischen blättert, Tippen auf einen Termin öffnet den Wecker-Editor, Tippen auf einen Tag die Tagesansicht.
 */
@Composable
fun CalendarGrid(
    view: CalendarView,
    events: List<EventInstance>,
    alarmKeys: Set<String>,
    now: Long,
    loading: Boolean,
    focus: LocalDate,
    onFocusChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onPickDate: () -> Unit,
    onFilter: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val nowTime = Instant.ofEpochMilli(now).atZone(zone)
    val today = nowTime.toLocalDate()
    val nowMinute = nowTime.hour * 60 + nowTime.minute
    val index = remember(events) { DayIndex(events) }
    val paging = remember(view, today) { GridPaging(view, today) }
    val density = LocalDensity.current
    // Ein gemeinsamer Scroll-Zustand, damit beim Blättern dieselbe Uhrzeit sichtbar bleibt.
    val verticalScroll = rememberScrollState(with(density) { (HourHeight * max(0, nowTime.hour - 1)).roundToPx() })
    val scope = rememberCoroutineScope()
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)

    key(paging) {
        val pagerState = rememberPagerState(initialPage = paging.pageOf(focus)) { paging.pageCount }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val first = paging.firstDay(page)
                val inPage = !today.isBefore(first) && today.isBefore(first.plusDays(paging.days.toLong()))
                currentOnFocusChange(if (inPage) today else first)
            }
        }
        LaunchedEffect(focus) {
            val target = paging.pageOf(focus)
            if (target != pagerState.settledPage) pagerState.animateScrollToPage(target)
        }

        Column(Modifier.fillMaxSize()) {
            val shown = paging.days(pagerState.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPickDate) {
                    Text(
                        Fmt.monthRange(shown.first(), shown.last()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = stringResource(R.string.choose_date),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.previous))
                }
                IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.next))
                }
                val todayLabel = stringResource(R.string.go_to_today)
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(paging.pageOf(today)) } },
                    modifier = Modifier.semantics { contentDescription = todayLabel },
                ) {
                    TodayIcon(today.dayOfMonth)
                }
                IconButton(onClick = onFilter) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.choose_calendars))
                }
            }

            Box(Modifier.weight(1f)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val days = remember(paging, page) { paging.days(page) }
                    val spans = remember(days, index) { allDaySpans(days, index) }
                    val placed = remember(days, index) { days.map { layoutDay(it, index.timed[it].orEmpty()) } }
                    Column(Modifier.fillMaxSize()) {
                        DayHeaderRow(days, today, view, onDayClick)
                        AllDayRow(spans, days.size, now, alarmKeys, onEventClick)
                        HorizontalDivider()
                        TimeGrid(
                            days = days,
                            placed = placed,
                            todayIndex = days.indexOf(today),
                            nowMinute = nowMinute,
                            now = now,
                            alarmKeys = alarmKeys,
                            showDetails = view != CalendarView.WEEK,
                            scrollState = verticalScroll,
                            onEventClick = onEventClick,
                        )
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

/** Kalender-Symbol mit dem heutigen Tag, wie in Google Kalender. */
@Composable
private fun TodayIcon(day: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(22.dp)
            .border(1.5.dp, color, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DayHeaderRow(days: List<LocalDate>, today: LocalDate, view: CalendarView, onDayClick: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.week_number, days.first().get(WeekFields.ISO.weekOfWeekBasedYear())),
            modifier = Modifier.width(GutterWidth),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        days.forEach { day ->
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = view != CalendarView.DAY) { onDayClick(day) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Fmt.weekday(day, long = view == CalendarView.DAY),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayRow(
    spans: List<Span>,
    dayCount: Int,
    now: Long,
    alarmKeys: Set<String>,
    onEventClick: (EventInstance) -> Unit,
) {
    if (spans.isEmpty()) return
    val lanes = spans.maxOf { it.lane } + 1
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Spacer(Modifier.width(GutterWidth))
        // Viele Ganztags-Termine lassen sich in der Zeile scrollen, statt das Raster zu verdrängen.
        Box(
            Modifier
                .weight(1f)
                .heightIn(max = AllDayLaneHeight * 3 + 11.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Layout(
                content = {
                    spans.forEach { span ->
                        EventBlock(
                            inst = span.inst,
                            hasAlarm = span.inst.key in alarmKeys,
                            past = span.inst.end <= now,
                            details = null,
                            onClick = { onEventClick(span.inst) },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AllDayLaneHeight * lanes),
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val dayWidth = width.toFloat() / dayCount
                val laneHeight = AllDayLaneHeight.roundToPx()
                val gap = 2.dp.roundToPx()
                val placeables = measurables.mapIndexed { i, m ->
                    val span = spans[i]
                    val x0 = (span.from * dayWidth).roundToInt()
                    val x1 = ((span.to + 1) * dayWidth).roundToInt()
                    m.measure(Constraints.fixed(max(1, x1 - x0 - gap), laneHeight - gap))
                }
                layout(width, laneHeight * lanes) {
                    placeables.forEachIndexed { i, p ->
                        p.place((spans[i].from * dayWidth).roundToInt(), spans[i].lane * laneHeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeGrid(
    days: List<LocalDate>,
    placed: List<List<Placed>>,
    todayIndex: Int,
    nowMinute: Int,
    now: Long,
    alarmKeys: Set<String>,
    showDetails: Boolean,
    scrollState: ScrollState,
    onEventClick: (EventInstance) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val nowColor = Color(0xFFE53935)
    Row(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        Box(Modifier.width(GutterWidth).height(HourHeight * 24)) {
            for (hour in 1 until 24) {
                Text(
                    Fmt.hour(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = HourHeight * hour - 8.dp)
                        .padding(end = 6.dp),
                )
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(HourHeight * 24)
                .drawBehind {
                    val hourPx = HourHeight.toPx()
                    val dayWidth = size.width / days.size
                    val stroke = 1.dp.toPx()
                    if (todayIndex >= 0) {
                        drawRect(todayTint, topLeft = Offset(todayIndex * dayWidth, 0f), size = Size(dayWidth, size.height))
                    }
                    for (hour in 1 until 24) {
                        drawLine(lineColor, Offset(0f, hour * hourPx), Offset(size.width, hour * hourPx), stroke)
                    }
                    for (d in 0 until days.size) {
                        drawLine(lineColor, Offset(d * dayWidth, 0f), Offset(d * dayWidth, size.height), stroke)
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (todayIndex >= 0) {
                        val y = nowMinute / 60f * HourHeight.toPx()
                        val dayWidth = size.width / days.size
                        val x0 = todayIndex * dayWidth
                        drawLine(nowColor, Offset(x0, y), Offset(x0 + dayWidth, y), 2.dp.toPx())
                        drawCircle(nowColor, 5.dp.toPx(), Offset(x0, y))
                    }
                },
        ) {
            placed.forEach { dayEvents ->
                DayColumn(
                    placed = dayEvents,
                    now = now,
                    alarmKeys = alarmKeys,
                    showDetails = showDetails,
                    onEventClick = onEventClick,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 1.dp, end = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    placed: List<Placed>,
    now: Long,
    alarmKeys: Set<String>,
    showDetails: Boolean,
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier,
) {
    Layout(
        content = {
            placed.forEach { p ->
                EventBlock(
                    inst = p.inst,
                    hasAlarm = p.inst.key in alarmKeys,
                    past = p.inst.end <= now,
                    details = if (showDetails) eventDetails(p.inst) else null,
                    outlined = p.indent > 0,
                    onClick = { onEventClick(p.inst) },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val minutePx = HourHeight.toPx() / 60f
        val gap = 1.dp.roundToPx()
        val indentStep = min(width / 4, 40.dp.roundToPx())
        fun x(p: Placed) = if (p.indent > 0) min(p.indent * indentStep, width * 3 / 5) else p.col * (width / p.cols)
        fun w(p: Placed) = if (p.indent > 0) width - x(p) else width / p.cols
        val placeables = measurables.mapIndexed { i, m ->
            val p = placed[i]
            val top = (p.visStart * minutePx).roundToInt()
            val bottom = (p.visEnd * minutePx).roundToInt()
            m.measure(Constraints.fixed(max(1, w(p) - gap), max(1, bottom - top - gap)))
        }
        layout(width, constraints.maxHeight) {
            placeables.forEachIndexed { i, pl ->
                val p = placed[i]
                pl.place(x(p), (p.visStart * minutePx).roundToInt())
            }
        }
    }
}

private fun eventDetails(inst: EventInstance): String = buildString {
    append(Fmt.eventTime(inst))
    if (inst.location.isNotBlank()) append(" · ").append(inst.location)
}

/** Farbiger Termin-Block; ein Wecker-Symbol vor dem Titel zeigt, dass ein Wecker gestellt ist. */
@Composable
private fun EventBlock(
    inst: EventInstance,
    hasAlarm: Boolean,
    past: Boolean,
    details: String?,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    val background = calendarColor(inst.color)
    val content = if (background.luminance() > 0.5f) Color(0xFF1F1F1F) else Color.White
    val lineHeight = 13.sp
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .alpha(if (past) 0.55f else 1f)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.surface, shape) else Modifier)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        val lines = with(LocalDensity.current) { (maxHeight.toPx() / lineHeight.toPx()).toInt() }.coerceAtLeast(1)
        val detailText = details?.takeIf { lines >= 2 }
        val title = inst.title.ifBlank { stringResource(R.string.untitled) }
        val alarmSet = stringResource(R.string.alarm_set)
        Column(verticalArrangement = Arrangement.Top) {
            Text(
                buildAnnotatedString {
                    if (hasAlarm) {
                        appendInlineContent(ALARM_ICON, "⏰")
                        append(' ')
                    }
                    append(title)
                },
                inlineContent = mapOf(
                    ALARM_ICON to InlineTextContent(Placeholder(11.sp, 11.sp, PlaceholderVerticalAlign.TextCenter)) {
                        Icon(Icons.Filled.Alarm, contentDescription = alarmSet, tint = content, modifier = Modifier.fillMaxSize())
                    },
                ),
                color = content,
                fontSize = 11.sp,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = if (detailText != null) lines - 1 else lines,
                overflow = TextOverflow.Ellipsis,
            )
            if (detailText != null) {
                Text(
                    detailText,
                    color = content.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
