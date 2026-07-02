package net.ankiweb.rsdroid.rules

import net.ankiweb.rsdroid.rules.LogcatRule.LogEntry.LogLevel
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class LogcatRule : TestWatcher() {
    val logs: List<LogEntry> get() =
        Logcat
            .dump()
            .mapNotNull { line ->
                logPattern.find(line)
            }.map { match ->
                val (levelString, tag, message) = match.destructured
                LogEntry(
                    level = LogLevel.fromString(levelString),
                    tag = tag,
                    message = message,
                    rawLine = match.value,
                )
            }.toList()

    val errors get() = logs.filter { it.level == LogLevel.ERROR }

    override fun starting(description: Description) {
        Logcat.clear()
    }

    data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val rawLine: String,
    ) {
        enum class LogLevel {
            TRACE,
            DEBUG,
            INFO,
            WARN,

            // error and WTF
            ERROR,

            ;

            companion object {
                fun fromString(string: String) =
                    when (string) {
                        "T" -> TRACE
                        "D" -> DEBUG
                        "I" -> INFO
                        "W" -> WARN
                        "E" -> ERROR
                        else -> throw IllegalArgumentException("unexpected level: $string")
                    }
            }
        }
    }

    companion object {
        // 1:level; 2:tag; 3:message |    1 [        2      ] [         3           ]
        // 06-14 14:17:18.600 25500 25524 I rsdroid::logging: rsdroid logging enabled
        private val logPattern = Regex(""".* ([TDIWE]) (.+?)\s+(.+)$""")
    }
}

private object Logcat {
    fun clear() {
        Runtime.getRuntime().exec("logcat -c")
    }

    fun dump() =
        sequence<String> {
            val process = Runtime.getRuntime().exec("logcat -d")
            process.inputStream.bufferedReader().use { reader ->
                yieldAll(reader.lineSequence())
            }
        }
}
