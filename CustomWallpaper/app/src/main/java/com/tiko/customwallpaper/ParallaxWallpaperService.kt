package com.tiko.customwallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

class ParallaxWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ParallaxEngine()

    private inner class ParallaxEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var bottom: Bitmap? = null
        private var middle: Bitmap? = null
        private var top: Bitmap? = null
        private var xOffset = 0.5f
        private var visible = false
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadLayers()
            setTouchEventsEnabled(false)
        }

        private fun loadLayers() {
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            fun load(key: String): Bitmap? {
                val text = prefs.getString(key, null) ?: return null
                return try {
                    contentResolver.openInputStream(Uri.parse(text))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            bottom = load(MainActivity.KEY_BOTTOM_URI)
                ?: load(MainActivity.KEY_IMAGE_URI)
            middle = load(MainActivity.KEY_MIDDLE_URI)
            top = load(MainActivity.KEY_TOP_URI)
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            if (visibleNow) drawFrame() else handler.removeCallbacks(drawRunner)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            drawFrame()
        }

        override fun onOffsetsChanged(
            xOffsetNow: Float,
            yOffsetNow: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            xOffset = xOffsetNow.coerceIn(0f, 1f)
            drawFrame()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawRunner)
            bottom?.recycle()
            middle?.recycle()
            top?.recycle()
            bottom = null
            middle = null
            top = null
            super.onDestroy()
        }

        private fun drawFrame() {
            handler.removeCallbacks(drawRunner)
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(0xFF102D25.toInt())
                    // Different offset multipliers create a simple three-depth parallax effect.
                    drawLayer(canvas, bottom, 0.25f)
                    drawLayer(canvas, middle, 0.60f)
                    drawLayer(canvas, top, 1.0f)
                }
            } catch (_: Exception) {
                // Avoid crashing the launcher if a wallpaper surface becomes unavailable.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
            if (visible) handler.postDelayed(drawRunner, 1000L / 20L)
        }

        private fun drawLayer(canvas: Canvas, bitmap: Bitmap?, depth: Float) {
            if (bitmap == null || bitmap.isRecycled) return
            val scale = max(
                surfaceWidth.toFloat() / bitmap.width,
                surfaceHeight.toFloat() / bitmap.height
            )
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val extraX = max(0f, scaledWidth - surfaceWidth)
            val extraY = max(0f, scaledHeight - surfaceHeight)

            // Move each layer at a different rate as the launcher changes its page offset.
            val shiftX = extraX * xOffset * depth
            val shiftY = extraY * 0.5f
            val srcLeft = (shiftX / scale).toInt().coerceIn(0, max(0, bitmap.width - 1))
            val srcTop = (shiftY / scale).toInt().coerceIn(0, max(0, bitmap.height - 1))
            val srcWidth = min(bitmap.width - srcLeft, (surfaceWidth / scale).toInt().coerceAtLeast(1))
            val srcHeight = min(bitmap.height - srcTop, (surfaceHeight / scale).toInt().coerceAtLeast(1))
            if (srcWidth > 0 && srcHeight > 0) {
                canvas.drawBitmap(
                    bitmap,
                    Rect(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight),
                    Rect(0, 0, surfaceWidth, surfaceHeight),
                    paint
                )
            }
        }
    }
}
