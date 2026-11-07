package dev.raja.passportcam.pipeline

/**
 * Pure JPEG quality search for [Compressor]. Returns the highest quality in [minQ]..[maxQ] whose encoded size is
 * <= [maxBytes], or null if even [minQ] is too big. [sizeAt] encodes at a quality and returns the byte count.
 *
 * Binary search assumes size grows with quality. If the curve is only roughly monotonic, the result is still a quality
 * that was measured as fitting: the returned value is always a probe (or [minQ]) whose size was <= [maxBytes].
 * Costs at most 1 + ceil(log2(maxQ - minQ + 1)) encodes (7 for 60..95).
 */
fun highestQualityUnder(maxBytes: Int, minQ: Int, maxQ: Int, sizeAt: (Int) -> Int): Int? {
    require(minQ <= maxQ) { "minQ $minQ > maxQ $maxQ" }
    if (sizeAt(minQ) > maxBytes) return null
    // Invariant: `fits` was measured <= maxBytes. `firstMiss` is maxQ + 1 (sentinel) or was measured > maxBytes.
    var fits = minQ
    var firstMiss = maxQ + 1
    while (firstMiss - fits > 1) {
        val mid = (fits + firstMiss) ushr 1
        if (sizeAt(mid) <= maxBytes) fits = mid else firstMiss = mid
    }
    return fits
}
