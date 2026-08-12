package pro.kisscat.www.bookmarkhelper.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import pro.kisscat.www.bookmarkhelper.sync.task.HistoryRange
import pro.kisscat.www.bookmarkhelper.ui.AppHapticProvider
import pro.kisscat.www.bookmarkhelper.ui.MiuixBookmarkTheme
import pro.kisscat.www.bookmarkhelper.ui.UiPreferences
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.MiuixDatePickerBottomSheet
import pro.kisscat.www.bookmarkhelper.ui.component.miuix.localDayStartMillis
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference

class DateRangeActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_FROM = "from_seconds"
        private const val EXTRA_TO = "to_seconds"
        private const val EXTRA_LABEL = "range_label"

        fun intent(context: Context, current: HistoryRange?) =
            Intent(context, DateRangeActivity::class.java).apply {
                current?.let {
                    putExtra(EXTRA_FROM, it.fromUnixSeconds)
                    putExtra(EXTRA_TO, it.toUnixSeconds)
                }
            }

        fun readResult(intent: Intent?): HistoryRange? {
            if (intent == null || !intent.hasExtra(EXTRA_FROM) || !intent.hasExtra(EXTRA_TO)) return null
            return HistoryRange(
                intent.getLongExtra(EXTRA_FROM, 0L),
                intent.getLongExtra(EXTRA_TO, 0L),
                intent.getStringExtra(EXTRA_LABEL).orEmpty(),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val nowSeconds = System.currentTimeMillis() / 1000L
        val initialStart = localDayStartMillis(
            intent.getLongExtra(EXTRA_FROM, nowSeconds - 7L * 86_400L) * 1000L
        )
        val initialEnd = localDayStartMillis(
            intent.getLongExtra(EXTRA_TO, nowSeconds) * 1000L
        )
        setContent {
            BindSystemBack(UiPreferences.predictiveBackEnabled(this))
            AppHapticProvider(UiPreferences.hapticsEnabled(this)) {
                MiuixBookmarkTheme(
                    UiPreferences.themeMode(this),
                    UiPreferences.monetEnabled(this),
                ) {
                    DateRangePage(initialStart, initialEnd)
                }
            }
        }
    }

    @Composable
    private fun DateRangePage(initialStart: Long, initialEnd: Long) {
        var start by remember { mutableLongStateOf(initialStart) }
        var end by remember { mutableLongStateOf(initialEnd) }
        var pickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }
        val formatter = remember { DateFormat.getDateInstance(DateFormat.LONG) }
        val apply = {
            val label = "${formatter.format(Date(start))} 至 ${formatter.format(Date(end))}"
            setResult(RESULT_OK, Intent().apply {
                putExtra(EXTRA_FROM, start / 1000L)
                putExtra(EXTRA_TO, end / 1000L + 86_399L)
                putExtra(EXTRA_LABEL, label)
            })
            finish()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = "选择日期范围",
                    navigationIcon = {
                        IconButton(::finishSystemPage) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card {
                    ArrowPreference(
                        title = "开始日期",
                        summary = formatter.format(Date(start)),
                        onClick = { pickerTarget = DatePickerTarget.START },
                    )
                    ArrowPreference(
                        title = "结束日期",
                        summary = formatter.format(Date(end)),
                        onClick = { pickerTarget = DatePickerTarget.END },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    text = "确定",
                    onClick = apply,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

        pickerTarget?.let { target ->
            MiuixDatePickerBottomSheet(
                show = true,
                title = if (target == DatePickerTarget.START) "选择开始日期" else "选择结束日期",
                initialDateMillis = if (target == DatePickerTarget.START) start else end,
                minimumDateMillis = if (target == DatePickerTarget.END) start else null,
                maximumDateMillis = if (target == DatePickerTarget.START) end else System.currentTimeMillis(),
                onDismissRequest = { pickerTarget = null },
                onDateSelected = { selected ->
                    if (target == DatePickerTarget.START) start = selected.coerceAtMost(end)
                    else end = selected.coerceAtLeast(start)
                },
            )
        }
    }

    private enum class DatePickerTarget { START, END }
}
