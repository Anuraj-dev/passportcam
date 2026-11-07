package dev.raja.passportcam

import dev.raja.passportcam.core.Config
import dev.raja.passportcam.pipeline.highestQualityUnder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QualitySearchTest {

    private val cap = 100_000
    private val minQ = 60
    private val maxQ = 95

    /** Linear size curve: 1000 bytes per quality step. */
    private fun linear(q: Int) = q * 1000

    @Test
    fun fitsAtMaxQuality_returnsMax() {
        // 95 * 1000 = 95_000 <= cap, so every quality fits and the highest wins.
        assertEquals(95, highestQualityUnder(cap, minQ, maxQ, ::linear))
    }

    @Test
    fun fitsOnlyUpToMid_returnsMid() {
        // q <= 80 -> 80_000 fits; q = 81 -> 81_000 is over the cap.
        assertEquals(80, highestQualityUnder(80_000, minQ, maxQ, ::linear))
    }

    @Test
    fun fitsOnlyAtMin_returnsMin() {
        // q = 60 -> 60_000 fits; q = 61 -> 61_000 is over the cap.
        assertEquals(60, highestQualityUnder(60_500, minQ, maxQ, ::linear))
    }

    @Test
    fun neverFits_returnsNull() {
        assertNull(highestQualityUnder(cap, minQ, maxQ) { 200_000 })
        // Even minQ is 100_001 bytes, one over the cap.
        assertNull(highestQualityUnder(100_000, minQ, maxQ) { 100_001 })
    }

    @Test
    fun singleQualityRange() {
        assertEquals(70, highestQualityUnder(cap, 70, 70) { 50_000 })
        assertNull(highestQualityUnder(cap, 70, 70) { 150_000 })
    }

    @Test
    fun nonMonotonicNoise_returnsMeasuredFittingQuality() {
        // Random per-quality noise around a rising curve. Each seed builds one fixed table so
        // sizeAt is deterministic. Whatever the search returns must be a quality measured under the cap.
        for (seed in 0 until 300) {
            val rnd = Random(seed)
            val table = IntArray(maxQ - minQ + 1) { i ->
                val base = (i + minQ) * 1000
                base + rnd.nextInt(-30_000, 30_000)
            }
            val sizeAt = { q: Int -> table[q - minQ] }
            val result = highestQualityUnder(cap, minQ, maxQ, sizeAt)
            if (sizeAt(minQ) > cap) {
                assertNull("seed $seed", result)
            } else {
                assertTrue("seed $seed", result != null)
                assertTrue("seed $seed q=$result", result!! in minQ..maxQ)
                assertTrue("seed $seed q=$result size=${sizeAt(result)}", sizeAt(result) <= cap)
            }
        }
    }

    @Test
    fun encodeCountStaysSmall() {
        // Curves: rising (linear) and flat-over-cap. Caps cover fits-at-max, fits-at-mid, fits-at-min, never-fits.
        val curves = mapOf<String, (Int) -> Int>(
            "linear" to { q -> q * 1000 },
            "flat 200k" to { _ -> 200_000 },
        )
        val caps = listOf(Config.MAX_BYTES, 80_000, 60_500, 1_000)
        for ((name, curve) in curves) {
            for (c in caps) {
                var encodes = 0
                highestQualityUnder(c, Config.MIN_QUALITY, Config.MAX_QUALITY) { q ->
                    encodes++
                    curve(q)
                }
                assertTrue("$name cap=$c encodes=$encodes", encodes <= 7)
            }
        }
    }
}
