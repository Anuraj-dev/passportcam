package dev.raja.passportcam

import dev.raja.passportcam.core.Config
import dev.raja.passportcam.pipeline.PassportCropper
import dev.raja.passportcam.pipeline.SquareCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropMathTest {

    private fun crop(l: Int, t: Int, r: Int, b: Int, w: Int, h: Int): SquareCrop =
        PassportCropper.squareCropFor(l, t, r, b, w, h)

    private fun assertInside(c: SquareCrop, w: Int, h: Int) {
        // SquareCrop has a single side length, so squareness is structural; only check sizes and bounds.
        assertTrue("side must be >= 1, was ${c.side}", c.side >= 1)
        assertTrue("left ${c.left} outside image width $w", c.left >= 0 && c.left + c.side <= w)
        assertTrue("top ${c.top} outside image height $h", c.top >= 0 && c.top + c.side <= h)
    }

    @Test
    fun centredFace_isCentredHorizontallyAndKeepsHeadProportions() {
        // Face box brow-to-chin: 800px tall, 600px wide, centred at x=1500 in a 3000x4000 image.
        val c = crop(1200, 1000, 1800, 1800, 3000, 4000)
        assertInside(c, 3000, 4000)

        val faceCentreX = 1500.0
        val cropCentreX = c.left + c.side / 2.0
        assertTrue("horizontal centre off by ${abs(cropCentreX - faceCentreX)}", abs(cropCentreX - faceCentreX) <= 1.0)

        // Crown is 0.30 of the box height above the top; chin is the box bottom.
        val crownY = 1000 - 0.30 * 800
        val headFraction = (1800 - crownY) / c.side
        assertEquals(Config.HEAD_HEIGHT_FRACTION.toDouble(), headFraction, 0.01)
    }

    @Test
    fun centredFace_eyesLandNearMiddleOfSquare() {
        val c = crop(1200, 1000, 1800, 1800, 3000, 4000)
        // Eyes sit about halfway between crown and chin.
        val crownY = 1000 - 0.30 * 800
        val eyeY = crownY + 0.5 * (800 * 1.30)
        val eyeFraction = (eyeY - c.top) / c.side
        assertTrue("eye line at $eyeFraction of square", eyeFraction in 0.45..0.55)
    }

    @Test
    fun faceNearLeftEdge_clampsToLeftEdgeWithoutShrinking() {
        val c = crop(20, 1000, 620, 1800, 3000, 4000)
        assertInside(c, 3000, 4000)
        assertEquals(0, c.left)
    }

    @Test
    fun faceNearRightEdge_clampsToRightEdge() {
        val c = crop(2400, 1000, 2990, 1800, 3000, 4000)
        assertInside(c, 3000, 4000)
        assertEquals(3000, c.left + c.side)
    }

    @Test
    fun faceNearTopEdge_crownAboveImage_clampsToTop() {
        // Crown estimate (50 - 0.3*800 = -190) is above the image; top must clamp to 0.
        val c = crop(1200, 50, 1800, 850, 3000, 4000)
        assertInside(c, 3000, 4000)
        assertEquals(0, c.top)
    }

    @Test
    fun faceNearBottomEdge_shiftsUpWithoutShrinking() {
        // Chin near the bottom: the square would overflow, but it still fits after shifting up.
        val c = crop(1200, 3300, 1800, 3990, 3000, 4000)
        assertInside(c, 3000, 4000)
        assertEquals(4000, c.top + c.side)
        // Ideal side is round(690 * 1.30 / 0.74) = 1212; shifting alone must keep it.
        assertEquals(1212, c.side)
    }

    @Test
    fun faceLargerThanImage_shrinksToShorterSideAndStaysInside() {
        val c = crop(0, 0, 500, 700, 500, 400)
        assertInside(c, 500, 400)
        assertEquals(400, c.side)
    }

    @Test
    fun tinyImage_stillReturnsInsideSquare() {
        assertEquals(SquareCrop(0, 0, 1), crop(0, 0, 1, 1, 1, 1))
        assertInside(crop(0, 0, 3, 2, 3, 2), 3, 2)
        assertInside(crop(1, 1, 2, 2, 7, 5), 7, 5)
    }

    @Test
    fun grid_resultAlwaysSquareAndInsideImage() {
        val sizes = listOf(1 to 1, 2 to 9, 9 to 2, 100 to 100, 640 to 480, 480 to 640, 4000 to 3000)
        for ((w, h) in sizes) {
            val boxes = listOf(
                // Faces at a range of positions, sizes, and partly off-frame.
                intArrayOf(0, 0, w, h),
                intArrayOf(-w, -h, 2 * w, 2 * h),
                intArrayOf(w / 4, h / 4, 3 * w / 4, 3 * h / 4),
                intArrayOf(0, h - 1, w, h + w),
                intArrayOf(-5, -5, 5, 5),
                intArrayOf(w - 1, h - 1, w + 3, h + 3),
            )
            for (b in boxes) {
                val c = crop(b[0], b[1], b[2], b[3], w, h)
                assertInside(c, w, h)
            }
        }
    }
}
