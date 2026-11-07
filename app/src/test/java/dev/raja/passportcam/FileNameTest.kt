package dev.raja.passportcam

import dev.raja.passportcam.storage.passportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FileNameTest {

    /** Millis for a wall-clock time in the default zone, so the expectation holds on any machine. */
    private fun millisLocal(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun formatsFirstIndexFromMillis() {
        val name = passportFileName(1, millisLocal(2026, 11, 7, 9, 5, 3))
        assertEquals("passport_20261107_090503_1.jpg", name)
    }

    @Test
    fun indexIsUsedVerbatimAndMultiDigit() {
        val name = passportFileName(12, millisLocal(2026, 11, 7, 23, 59, 59))
        assertEquals("passport_20261107_235959_12.jpg", name)
    }

    @Test
    fun midnightKeepsZeroPaddedFields() {
        val name = passportFileName(3, millisLocal(2027, 1, 2, 0, 0, 0))
        assertEquals("passport_20270102_000000_3.jpg", name)
    }

    @Test
    fun zeroIndexIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            passportFileName(0, millisLocal(2026, 11, 7, 9, 5, 3))
        }
    }
}
