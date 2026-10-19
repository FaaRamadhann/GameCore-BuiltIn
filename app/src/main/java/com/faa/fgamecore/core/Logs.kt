package com.faa.fgamecore.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(val ts: Long, val level: String, val text: String) {
    val clock: String get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ts))
    val isDaemon: Boolean get() = level.startsWith("DAEMON") || level == "SHIZUKU" || level == "CORE"
}

/** Bounded runtime log: in-memory ring + one append-only file so it survives restarts. */
object Logs {
    private const val MAX = 400
    private val ring = ArrayDeque<LogLine>()
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines
    private var file: File? = null

    fun load(ctx: Context) {
        file = File(ctx.filesDir, "fgc-runtime.log")
        if (ring.isNotEmpty()) return
        runCatching {
            file?.takeIf { it.exists() }?.readLines()?.takeLast(MAX)?.forEach { raw ->
                val m = Regex("^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d) \\[(\\w+)] (.*)$").find(raw)
                if (m != null) {
                    val ts = runCatching {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(m.groupValues[1])!!.time
                    }.getOrDefault(System.currentTimeMillis())
                    ring.addLast(LogLine(ts, m.groupValues[2], m.groupValues[3]))
                }
            }
            _lines.value = ring.toList()
        }
    }

    @Synchronized
    fun add(level: String, text: String) {
        val line = LogLine(System.currentTimeMillis(), level, text)
        ring.addLast(line)
        while (ring.size > MAX) ring.removeFirst()
        _lines.value = ring.toList()
        runCatching {
            val f = file ?: return@runCatching
            if (f.length() > 512 * 1024) {
                val keep = f.readLines().takeLast(MAX)
                f.writeText(keep.joinToString("\n") + "\n")
            }
            f.appendText(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(line.ts)) +
                    " [${line.level}] ${line.text}\n"
            )
        }
    }

    @Synchronized
    fun clear() {
        ring.clear()
        _lines.value = emptyList()
        runCatching { file?.writeText("") }
    }
}
