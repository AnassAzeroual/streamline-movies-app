package com.streamline.app.core.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.streamline.app.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit







object AppLog {
    private const val TAG = "StreamlineLog"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val MAX_FIELD_CHARS = 500

    private val lock = Any()
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2_048),
        { runnable -> Thread(runnable, "streamline-log").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )
    private val timestamp =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private var sessionId: String = "uninitialized"

    fun init(context: Context) {
        synchronized(lock) {
            if (writer != null) return
            sessionId = UUID.randomUUID().toString().take(8)
            val directory = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
            logFile = File(directory, "streamline.log")
            openWriter()
        }
        event(
            category = "APP",
            event = "process_start",
            fields = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE,
                "android" to Build.VERSION.RELEASE,
                "sdk" to Build.VERSION.SDK_INT,
                "device" to (Build.MANUFACTURER + " " + Build.MODEL),
            ),
        )
    }

    fun event(
        category: String,
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = write("INFO", category, event, message, fields)

    fun warn(
        category: String,
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = write("WARN", category, event, message, fields)

    fun error(
        category: String,
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = write("ERROR", category, event, message, fields)

    fun exception(
        category: String,
        event: String,
        throwable: Throwable,
        fields: Map<String, Any?> = emptyMap(),
    ) = write(
        "ERROR",
        category,
        event,
        throwable.message ?: throwable.javaClass.simpleName,
        fields + mapOf(
            "exception" to throwable.javaClass.name,
            "stack" to throwable.stackTraceToString().take(4_000),
        ),
    )

    fun file(): File? {
        flush()
        return logFile
    }

    fun readAll(): String {
        flush()
        return synchronized(lock) {
            val file = logFile ?: return ""
            runCatching { file.readText() }.getOrDefault("")
        }
    }

    fun clear() {
        val file = logFile
        synchronized(lock) {
            runCatching { writer?.close() }
            writer = null
            if (file != null) runCatching { file.delete() }
            openWriter()
        }
        event("APP", "logs_cleared")
    }

    fun flush() {
        val latch = CountDownLatch(1)
        executor.execute {
            synchronized(lock) { runCatching { writer?.flush() } }
            latch.countDown()
        }
        runCatching { latch.await(1, TimeUnit.SECONDS) }
    }

    private fun write(
        level: String,
        category: String,
        event: String,
        message: String,
        fields: Map<String, Any?>,
    ) {
        val line = formatLine(level, category, event, message, fields)
        runCatching {
            Log.println(
                when (level) {
                    "ERROR" -> Log.ERROR
                    "WARN" -> Log.WARN
                    else -> Log.INFO
                },
                TAG,
                line,
            )
        }
        executor.execute {
            synchronized(lock) {
                runCatching {
                    rotateIfNeeded()
                    val out = writer ?: return@runCatching
                    out.append(line)
                    out.newLine()
                    out.flush()
                }
            }
        }
    }

    private fun openWriter() {
        runCatching {
            writer?.close()
            val file = logFile ?: return
            writer = BufferedWriter(FileWriter(file, true))
        }
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (!file.exists() || file.length() < MAX_BYTES) return
        writer?.close()
        writer = null
        val rotated = File(file.parentFile, "streamline.log.1")
        runCatching { rotated.delete() }
        runCatching { file.renameTo(rotated) }
        openWriter()
    }

    private fun formatLine(
        level: String,
        category: String,
        event: String,
        message: String,
        fields: Map<String, Any?>,
    ): String = buildString {
        append('{')
        field("ts", timestamp.format(ZonedDateTime.now()))
        append(',')
        field("level", level)
        append(',')
        field("cat", category)
        append(',')
        field("event", event)
        append(',')
        field("session", sessionId)
        if (message.isNotBlank()) {
            append(',')
            field("msg", message.take(MAX_FIELD_CHARS))
        }
        fields.forEach { (key, value) ->
            if (value != null && key.isNotBlank()) {
                append(',')
                field(key, value.toString().take(MAX_FIELD_CHARS))
            }
        }
        append('}')
    }

    private fun StringBuilder.field(key: String, value: String) {
        append('"')
        append(escape(key))
        append('"')
        append(':')
        append('"')
        append(escape(value))
        append('"')
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\u005C' -> append("\u005C\u005C")
                '\u0022' -> append("\u005C\u0022")
                '\u000A' -> append("\u005Cn")
                '\u000D' -> append("\u005Cr")
                '\u0009' -> append("\u005Ct")
                else -> append(char)
            }
        }
    }}