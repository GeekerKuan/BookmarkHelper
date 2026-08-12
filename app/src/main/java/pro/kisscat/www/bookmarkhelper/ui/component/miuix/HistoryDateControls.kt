package pro.kisscat.www.bookmarkhelper.ui.component.miuix

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Calendar
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * A continuous Miuix slider with equal visual anchor spacing and haptics only at anchors.
 * A zero magnet threshold intentionally keeps every position selectable.
 */
@Composable
fun HistoryDateRangeSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var previousPosition by remember { mutableFloatStateOf(position) }
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = position,
            onValueChange = { rawPosition ->
                val newPosition = HistoryDateScale.magnetizedPosition(rawPosition)
                if (HistoryDateScale.crossedNode(previousPosition, newPosition)) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                previousPosition = newPosition
                onPositionChange(newPosition)
            },
            modifier = Modifier.fillMaxWidth().onSizeChanged { sliderSize = it },
            enabled = enabled,
            valueRange = HistoryDateScale.positionRange,
            steps = 0,
            showKeyPoints = true,
            keyPoints = HistoryDateScale.keyPoints,
            // The library threshold is a fraction of the full range (5 * .016 = .08).
            magnetThreshold = HistoryDateScale.NODE_MAGNET_RADIUS / 5f,
            hapticEffect = SliderDefaults.SliderHapticEffect.None,
        )
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            if (sliderSize.width > 0 && sliderSize.height > 0) {
                HistoryDateScale.dayNodes.forEachIndexed { index, days ->
                    Text(
                        text = days.toString(),
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val center = HistoryDateScale.nodeCenterPx(
                                index,
                                sliderSize.width.toFloat(),
                                sliderSize.height.toFloat(),
                            ).toInt()
                            layout(constraints.maxWidth, placeable.height) {
                                placeable.placeRelative(center - placeable.width / 2, 0)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The single date-picker implementation used by date ranges and history quick jump.
 * Miuix 0.9.2 does not expose a calendar DatePicker, so its native WindowBottomSheet
 * and NumberPicker are composed into a year/month/day picker here.
 */
@Composable
fun MiuixDatePickerBottomSheet(
    show: Boolean,
    title: String,
    initialDateMillis: Long,
    onDismissRequest: () -> Unit,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minimumDateMillis: Long? = null,
    maximumDateMillis: Long? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val pickerFeedback: () -> Unit = {
        if (UiPreferences.hapticsEnabled(context)) {
            val feedback = if (Build.VERSION.SDK_INT >= 34) {
                HapticFeedbackConstants.SEGMENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            // Let HyperOS/the current OEM map the platform constant to its own haptic engine.
            view.performHapticFeedback(feedback)
            if (view.isSoundEffectsEnabled) view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val initial = remember(initialDateMillis) { calendarAtStartOfDay(initialDateMillis) }
    val suppliedMinimum = remember(minimumDateMillis) {
        minimumDateMillis?.let(::calendarAtStartOfDay)
            ?: Calendar.getInstance().apply { clear(); set(1970, Calendar.JANUARY, 1) }
    }
    val suppliedMaximum = remember(maximumDateMillis) {
        maximumDateMillis?.let(::calendarAtStartOfDay) ?: calendarAtStartOfDay(System.currentTimeMillis())
    }
    val lowerBound = minOf(suppliedMinimum.timeInMillis, suppliedMaximum.timeInMillis)
    val upperBound = maxOf(suppliedMinimum.timeInMillis, suppliedMaximum.timeInMillis)
    val minimum = remember(lowerBound) { calendarAtStartOfDay(lowerBound) }
    val maximum = remember(upperBound) { calendarAtStartOfDay(upperBound) }
    val boundedInitial = initial.timeInMillis.coerceIn(lowerBound, upperBound)
    val boundedCalendar = remember(boundedInitial) { calendarAtStartOfDay(boundedInitial) }

    var year by remember { mutableIntStateOf(boundedCalendar.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(boundedCalendar.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(boundedCalendar.get(Calendar.DAY_OF_MONTH)) }

    LaunchedEffect(show, boundedInitial) {
        if (show) {
            year = boundedCalendar.get(Calendar.YEAR)
            month = boundedCalendar.get(Calendar.MONTH) + 1
            day = boundedCalendar.get(Calendar.DAY_OF_MONTH)
        }
    }

    val yearRange = minimum.get(Calendar.YEAR)..maximum.get(Calendar.YEAR)
    val monthRange = allowedMonthRange(year, minimum, maximum)
    val resolvedMonth = month.coerceIn(monthRange)
    val dayRange = allowedDayRange(year, resolvedMonth, minimum, maximum)
    val resolvedDay = day.coerceIn(dayRange)
    LaunchedEffect(resolvedMonth) {
        if (month != resolvedMonth) month = resolvedMonth
    }
    LaunchedEffect(resolvedDay) {
        if (day != resolvedDay) day = resolvedDay
    }

    WindowBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FixedUnitNumberPicker(
                    value = year,
                    onValueChange = {
                        if (it != year) pickerFeedback()
                        year = it
                    },
                    unit = "年",
                    modifier = Modifier.weight(1.35f),
                    range = yearRange,
                )
                FixedUnitNumberPicker(
                    value = resolvedMonth,
                    onValueChange = {
                        if (it != month) pickerFeedback()
                        month = it
                    },
                    unit = "月",
                    modifier = Modifier.weight(1f),
                    range = monthRange,
                    wrapAround = monthRange.first == 1 && monthRange.last == 12,
                )
                FixedUnitNumberPicker(
                    value = resolvedDay,
                    onValueChange = {
                        if (it != day) pickerFeedback()
                        day = it
                    },
                    unit = "日",
                    modifier = Modifier.weight(1f),
                    range = dayRange,
                    wrapAround = dayRange.first == 1,
                )
            }
            TextButton(
                text = "确定",
                onClick = {
                    val selected = Calendar.getInstance().apply {
                        clear()
                        set(year, resolvedMonth - 1, resolvedDay, 0, 0, 0)
                    }.timeInMillis.coerceIn(lowerBound, upperBound)
                    onDateSelected(selected)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = maxOf(16.dp, navigationBarPadding)),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun FixedUnitNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    unit: String,
    range: IntRange,
    modifier: Modifier = Modifier,
    wrapAround: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberPicker(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            range = range,
            label = { it.toString() },
            wrapAround = wrapAround,
        )
        Text(unit)
    }
}

fun localDayStartMillis(timeMillis: Long): Long = calendarAtStartOfDay(timeMillis).timeInMillis

private fun calendarAtStartOfDay(timeMillis: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun allowedMonthRange(year: Int, minimum: Calendar, maximum: Calendar): IntRange {
    val first = if (year == minimum.get(Calendar.YEAR)) minimum.get(Calendar.MONTH) + 1 else 1
    val last = if (year == maximum.get(Calendar.YEAR)) maximum.get(Calendar.MONTH) + 1 else 12
    return first..last
}

private fun allowedDayRange(year: Int, month: Int, minimum: Calendar, maximum: Calendar): IntRange {
    val daysInMonth = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val first = if (
        year == minimum.get(Calendar.YEAR) && month == minimum.get(Calendar.MONTH) + 1
    ) minimum.get(Calendar.DAY_OF_MONTH) else 1
    val last = if (
        year == maximum.get(Calendar.YEAR) && month == maximum.get(Calendar.MONTH) + 1
    ) maximum.get(Calendar.DAY_OF_MONTH) else daysInMonth
    return first..last
}
