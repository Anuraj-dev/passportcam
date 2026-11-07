package dev.raja.passportcam.core

/** Tunables for the passport pipeline. India passport photo: 51x51 mm square, ~600x600 px at 300 dpi. */
object Config {
    /** Hard size cap. 100_000 (not 102_400) so it is "under 100 KB" under either KB definition. */
    const val MAX_BYTES = 100_000

    /** Output side lengths to try, largest first. The compressor drops to the next one only if quality floor still too big. */
    val OUTPUT_SIDES_PX = intArrayOf(600, 500, 413)

    /** JPEG quality search range (inclusive). Never encode below MIN_QUALITY. */
    const val MAX_QUALITY = 95
    const val MIN_QUALITY = 60

    /** Faces whose bounding box shorter side (in source pixels, after EXIF rotation) is below this are skipped. */
    const val MIN_FACE_PX = 120

    /** Head (chin to crown) should fill ~this fraction of the output height. */
    const val HEAD_HEIGHT_FRACTION = 0.74f

    /** Decoded source is downsampled so its longest side is at most this many px (memory guard). */
    const val MAX_DECODE_SIDE_PX = 4096

    /** MediaStore folder (under Pictures/). Photos here appear in the system file picker "Recent". */
    const val SAVE_FOLDER = "PassportPhotos"
}
