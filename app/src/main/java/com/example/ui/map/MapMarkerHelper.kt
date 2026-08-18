package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.data.model.CloudDevice

object MapMarkerHelper {

    fun createDeviceMarkerDrawable(
        context: Context,
        device: CloudDevice,
        isSelected: Boolean
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val isOnline = (System.currentTimeMillis() - device.lastSeen) < 10 * 60 * 1000L && device.isOnline

        val primaryColor = try {
            Color.parseColor(device.colorHex)
        } catch (e: Exception) {
            Color.parseColor("#00D2FF")
        }

        val markerWidth = (140 * density).toInt()
        val markerHeight = (54 * density).toInt()

        val bitmap = Bitmap.createBitmap(markerWidth, markerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor("#1E293B") else Color.parseColor("#0F172A")
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) primaryColor else Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = (if (isSelected) 2.5f else 1.2f) * density
        }

        // Draw pill background
        val pillRect = RectF(
            4f * density,
            4f * density,
            markerWidth.toFloat() - 4f * density,
            markerHeight.toFloat() - 14f * density
        )
        val cornerRadius = 14f * density
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, borderPaint)

        // Draw pointer triangle at bottom
        val path = android.graphics.Path().apply {
            val midX = markerWidth / 2f
            val bottomY = markerHeight.toFloat() - 4f * density
            val startY = markerHeight.toFloat() - 14f * density
            moveTo(midX - 6f * density, startY)
            lineTo(midX, bottomY)
            lineTo(midX + 6f * density, startY)
            close()
        }
        canvas.drawPath(path, pillPaint)

        // Draw Status Dot (Green for online, Gray for offline)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOnline) Color.parseColor("#10B981") else Color.parseColor("#94A3B8")
            style = Paint.Style.FILL
        }
        val dotX = 16f * density
        val dotY = (pillRect.top + pillRect.bottom) / 2f
        val dotRadius = 4f * density
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)

        // If online, draw subtle pulse halo around dot
        if (isOnline) {
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3310B981")
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
            }
            canvas.drawCircle(dotX, dotY, dotRadius + 2f * density, haloPaint)
        }

        // Draw Device Name
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val name = if (device.name.length > 11) device.name.take(10) + "…" else device.name
        canvas.drawText(name, 26f * density, dotY - 2f * density, textPaint)

        // Draw Speed / Status Subtitle
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOnline) Color.parseColor("#00D2FF") else Color.parseColor("#94A3B8")
            textSize = 9.5f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val statusText = if (isOnline) {
            String.format(java.util.Locale.getDefault(), "%.0f km/h • %d%%", device.lastSpeedKmh, device.batteryPercent)
        } else {
            "Offline"
        }
        canvas.drawText(statusText, 26f * density, dotY + 11f * density, subTextPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createLocalUserMarkerDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (36 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Outer glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D00D2FF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 16f * density, glowPaint)

        // Inner circle
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00D2FF")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 9f * density, innerPaint)

        // White center dot
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, 4f * density, centerPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
