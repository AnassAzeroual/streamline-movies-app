package com.streamline.app.core.torrent

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min





object Mp4MetadataInspector {
    const val HEAD_WINDOW_BYTES = 2L * 1024L * 1024L
    const val TAIL_WINDOW_BYTES = 8L * 1024L * 1024L
    private const val ATOM_HEADER_BYTES = 8
    private const val LARGE_ATOM_HEADER_BYTES = 16

    data class Layout(
        val isMp4: Boolean,
        val moovOffset: Long? = null,
        val moovSize: Long? = null,
        val needsTail: Boolean = false,
    )

    fun isMp4(fileName: String): Boolean = fileName.substringAfterLast('.', "")
        .lowercase()
        .let { it == "mp4" || it == "m4v" || it == "mov" }

    fun inspect(file: File, fileSize: Long): Layout {
        if (!isMp4(file.name) || fileSize <= 0L) return Layout(isMp4 = false)

        val headLength = min(HEAD_WINDOW_BYTES, fileSize)
        val head = readWindow(file, 0L, headLength.toInt())
        findTopLevelMoov(head, 0L)?.takeIf { it.offset + it.size <= fileSize }?.let { atom ->
            return Layout(isMp4 = true, moovOffset = atom.offset, moovSize = atom.size)
        }

        val tailStart = max(0L, fileSize - TAIL_WINDOW_BYTES)
        val tailLength = (fileSize - tailStart).toInt()
        val tail = readWindow(file, tailStart, tailLength)
        val moov = findMoovInTail(tail, tailStart, fileSize)
        return Layout(
            isMp4 = true,
            moovOffset = moov?.offset,
            moovSize = moov?.size,
            needsTail = moov == null,
        )
    }

    private data class Atom(val offset: Long, val size: Long)

    private fun findTopLevelMoov(bytes: ByteArray, baseOffset: Long): Atom? {
        var cursor = 0
        while (cursor + ATOM_HEADER_BYTES <= bytes.size) {
            val smallSize = readUInt(bytes, cursor)
            val type = readType(bytes, cursor + 4)
            val headerSize: Int
            val size: Long
            when {
                smallSize == 1L -> {
                    if (cursor + LARGE_ATOM_HEADER_BYTES > bytes.size) return null
                    headerSize = LARGE_ATOM_HEADER_BYTES
                    size = readLong(bytes, cursor + 8)
                }
                smallSize == 0L -> {
                    size = bytes.size - cursor.toLong()
                    headerSize = ATOM_HEADER_BYTES
                }
                else -> {
                    size = smallSize
                    headerSize = ATOM_HEADER_BYTES
                }
            }
            if (size < headerSize) return null
            if (type == "moov") {
                
                
                return Atom(baseOffset + cursor, size)
            }
            if (cursor + size > bytes.size) return null
            cursor += size.toInt()
        }
        return null
    }

    private fun findMoovInTail(bytes: ByteArray, baseOffset: Long, fileSize: Long): Atom? {
        var index = 0
        while (index + ATOM_HEADER_BYTES <= bytes.size) {
            if (readType(bytes, index + 4) == "moov") {
                val boxStart = index
                val sizeAt = boxStart
                if (sizeAt >= 4) {
                    val smallSize = readUInt(bytes, sizeAt)
                    val largeSize = if (smallSize == 1L && sizeAt + 8 <= bytes.size) {
                        readLong(bytes, sizeAt + 8)
                    } else {
                        null
                    }
                    val size = largeSize ?: smallSize
                    val absoluteOffset = baseOffset + boxStart - 4L
                    if (size >= ATOM_HEADER_BYTES && absoluteOffset >= 0L) {
                        val end = absoluteOffset + size
                        if (end <= fileSize && end > absoluteOffset + ATOM_HEADER_BYTES) {
                            return Atom(absoluteOffset, size)
                        }
                    }
                }
            }
            index++
        }
        return null
    }

    private fun readWindow(file: File, offset: Long, length: Int): ByteArray {
        if (length <= 0 || !file.exists()) return ByteArray(0)
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val bytes = ByteArray(length)
                var total = 0
                while (total < length) {
                    val read = raf.read(bytes, total, length - total)
                    if (read <= 0) break
                    total += read
                }
                if (total == length) bytes else bytes.copyOf(total)
            }
        }.getOrDefault(ByteArray(0))
    }

    private fun readType(bytes: ByteArray, offset: Int): String =
        if (offset + 4 <= bytes.size) {
            String(bytes, offset, 4, Charsets.ISO_8859_1)
        } else {
            ""
        }

    private fun readUInt(bytes: ByteArray, offset: Int): Long =
        if (offset + 4 <= bytes.size) {
            ((bytes[offset].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
                (bytes[offset + 3].toLong() and 0xFF)
        } else {
            -1L
        }

    private fun readLong(bytes: ByteArray, offset: Int): Long =
        if (offset + 8 <= bytes.size) {
            (readUInt(bytes, offset) shl 32) or readUInt(bytes, offset + 4)
        } else {
            -1L
        }
}
