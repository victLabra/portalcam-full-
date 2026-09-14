
package com.portalcam

import android.graphics.Rect
import kotlin.math.*

// FaceTracker afinado para grupo grande 4-5 personas - reunion familiar
class FaceTracker {
    data class TrackedFrame(val cropRect: Rect, val faceCount: Int, val isGroup: Boolean, val zoom: Float)

    private var smoothedCenterX = 0.5f
    private var smoothedCenterY = 0.48f // ligeramente arriba para cabezas
    private var smoothedZoom = 1f
    private val smoothFast = 0.18f   // para 1 persona
    private val smoothSlow = 0.08f   // para grupo 4-5, mas estable
    private var lastGroupCount = 0

    fun update(faces: List<Rect>, frameWidth: Int, frameHeight: Int): TrackedFrame {
        if (faces.isEmpty()) {
            smoothedCenterX += (0.5f - smoothedCenterX) * 0.02f
            smoothedCenterY += (0.48f - smoothedCenterY) * 0.02f
            smoothedZoom += (1f - smoothedZoom) * 0.02f
            return TrackedFrame(Rect(0,0,frameWidth,frameHeight), 0, false, smoothedZoom)
        }

        // Bounding box grupal con padding extra para 4-5 personas
        var left = frameWidth
        var top = frameHeight
        var right = 0
        var bottom = 0
        for (f in faces) {
            left = min(left, f.left)
            top = min(top, f.top)
            right = max(right, f.right)
            bottom = max(bottom, f.bottom)
        }
        val groupW = (right - left).toFloat()
        val groupH = (bottom - top).toFloat()
        val groupCenterX = (left + right) / 2f / frameWidth
        val groupCenterY = (top + bottom) / 2f / frameHeight

        val isGroup = faces.size >= 2
        val isLargeGroup = faces.size >= 4

        // Zoom con padding generoso para grupo grande
        val padding = when {
            faces.size >= 5 -> 0.35f
            faces.size >= 4 -> 0.30f
            faces.size >= 3 -> 0.25f
            faces.size == 2 -> 0.20f
            else -> 0.12f
        }

        val spanX = groupW / frameWidth
        val spanY = groupH / frameHeight
        val maxSpan = max(spanX, spanY)

        val targetZoom = when {
            isLargeGroup -> (maxSpan + padding).coerceIn(0.85f, 1f) // casi full frame para 4-5
            isGroup -> (maxSpan + padding).coerceIn(0.70f, 0.95f)
            else -> 0.50f // close-up individual
        }

        val smooth = if (isLargeGroup) smoothSlow else smoothFast
        smoothedCenterX += (groupCenterX - smoothedCenterX) * smooth
        smoothedCenterY += (groupCenterY - smoothedCenterY) * smooth
        smoothedZoom += (targetZoom - smoothedZoom) * (smooth * 0.6f)

        // Evita que el crop se vaya fuera con grupo grande
        val cropW = (frameWidth * smoothedZoom).toInt()
        val cropH = (frameHeight * smoothedZoom).toInt()
        val centerXpx = (smoothedCenterX * frameWidth).toInt()
        val centerYpx = (smoothedCenterY * frameHeight).toInt()

        var cropLeft = (centerXpx - cropW/2).coerceIn(0, frameWidth - cropW)
        var cropTop = (centerYpx - cropH/2).coerceIn(0, frameHeight - cropH)
        // Para grupo, baja un poco el encuadre para no cortar cabezas
        if (isGroup) cropTop = (cropTop - frameHeight*0.05f).toInt().coerceAtLeast(0)

        lastGroupCount = faces.size
        return TrackedFrame(Rect(cropLeft, cropTop, cropLeft+cropW, cropTop+cropH), faces.size, isGroup, smoothedZoom)
    }
}
