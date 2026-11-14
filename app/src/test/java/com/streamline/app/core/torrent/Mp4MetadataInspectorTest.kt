package com.streamline.app.core.torrent

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4MetadataInspectorTest {

    @Test
    fun `finds moov in the head of a fast-start mp4`() {
        val file = File.createTempFile("fast-start", ".mp4")
        try {
            writeBoxes(
                file,
                byteArrayOf(),
                ftypBox(24),
                box("moov", 100),
                box("mdat", 500),
            )
            val layout = Mp4MetadataInspector.inspect(file, file.length())
            assertTrue(layout.isMp4)
            assertNotNull(layout.moovOffset)
            assertFalse(layout.needsTail)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `finds a trailing moov outside the head window`() {
        val file = File.createTempFile("tail-moov", ".mp4")
        try {
            val mdatSize = 3 * 1024 * 1024
            writeBoxes(
                file,
                byteArrayOf(),
                ftypBox(24),
                box("mdat", mdatSize),
                box("moov", 200),
            )
            val layout = Mp4MetadataInspector.inspect(file, file.length())
            assertTrue(layout.isMp4)
            assertTrue(layout.moovOffset != null)
            assertFalse(layout.needsTail)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `ignores non-mp4 files`() {
        val file = File.createTempFile("video", ".mkv")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val layout = Mp4MetadataInspector.inspect(file, file.length())
            assertFalse(layout.isMp4)
        } finally {
            file.delete()
        }
    }

    private fun writeBoxes(file: File, prefix: ByteArray, vararg boxes: ByteArray) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(prefix)
            boxes.forEach { raf.write(it) }
        }
    }

    private fun ftypBox(size: Int): ByteArray = box("ftyp", size)

    private fun box(type: String, size: Int): ByteArray {
        val bytes = ByteArray(size)
        bytes[0] = ((size ushr 24) and 0xFF).toByte()
        bytes[1] = ((size ushr 16) and 0xFF).toByte()
        bytes[2] = ((size ushr 8) and 0xFF).toByte()
        bytes[3] = (size and 0xFF).toByte()
        type.forEachIndexed { index, char -> bytes[4 + index] = char.code.toByte() }
        return bytes
    }
}
