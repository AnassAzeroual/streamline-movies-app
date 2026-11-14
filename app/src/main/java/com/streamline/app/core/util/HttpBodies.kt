package com.streamline.app.core.util

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val DEFAULT_MAX_BYTES = 4L * 1024L * 1024L

fun ResponseBody.readTextBounded(maxBytes: Long = DEFAULT_MAX_BYTES): String {
    val bytes = readBytesBounded(maxBytes)
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return String(bytes, charset)
}

fun ResponseBody.readBytesBounded(maxBytes: Long = DEFAULT_MAX_BYTES): ByteArray {
    val declared = contentLength()
    if (declared > maxBytes) {
        throw IOException("Response is larger than the allowed limit.")
    }
    val output = ByteArrayOutputStream(
        if (declared in 1..maxBytes) declared.toInt() else 16 * 1024,
    )
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    byteStream().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("Response is larger than the allowed limit.")
            }
            output.write(buffer, 0, read)
        }
    }
    return output.toByteArray()
}
