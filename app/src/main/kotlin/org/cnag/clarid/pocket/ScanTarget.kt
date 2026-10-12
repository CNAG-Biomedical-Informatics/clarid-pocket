package org.cnag.clarid.pocket

internal data class ScanBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    val centreX get() = (left + right) / 2f
    val centreY get() = (top + bottom) / 2f
}

internal data class ScanCandidate(val text: String?, val bounds: ScanBounds)
internal data class ScanFeedback(val value: String? = null, val ambiguous: Boolean = false)

/** Immediate capture. The crosshair only disambiguates multiple visible QR codes. */
internal class ScanTarget {
    private var delivered = false

    fun reset() { delivered = false }

    fun update(candidates: List<ScanCandidate>, width: Float, height: Float): ScanFeedback {
        if (delivered || width <= 0 || height <= 0) return ScanFeedback()
        val visible = candidates.filter {
            it.bounds.right > it.bounds.left && it.bounds.bottom > it.bounds.top &&
                it.bounds.centreX in 0f..width && it.bounds.centreY in 0f..height
        }
        val target = if (visible.size == 1) visible.single()
            else visible.filter { it.bounds.contains(width / 2f, height / 2f) }.singleOrNull()
        val value = target?.text?.takeIf { it.isNotEmpty() }
        if (value != null) delivered = true
        return ScanFeedback(value, ambiguous = visible.size > 1 && value == null)
    }

    companion object { const val GUIDE_FRACTION = 0.74f }
}
