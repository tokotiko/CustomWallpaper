package com.tiko.customwallpaper

import android.service.wallpaper.WallpaperService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

class ParallaxWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ParallaxEngine()

    private inner class ParallaxEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var bitmap: Bitmap? = null
        private var xOffset = 0.5f
        private var visible = false
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadImage()
            setTouchEventsEnabled(false)
        }

        private fun loadImage() {
            val uriText = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(MainActivity.KEY_IMAGE_URI, null) ?: return
            try {
                bitmap = contentResolver.openInputStream(Uri.parse(uriText))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                bitmap = null
            }
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

        override fun onOffsetsChanged(xOffsetNow: Float, yOffset: Float, xOffsetStep: Float,
                                      yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            xOffset = xOffsetNow.coerceIn(0f, 1f)
            drawFrame()
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawRunner)
            bitmap?.recycle()
            bitmap = null
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
                    val image = bitmap
                    if (image != null && !image.isRecycled) {
                        val scale = max(surfaceWidth.toFloat() / image.width, surfaceHeight.toFloat() / image.height)
                        val scaledW = image.width * scale
                        val scaledH = image.height * scale
                        val extraX = max(0f, scaledW - surfaceWidth)
                        val extraY = max(0f, scaledH - surfaceHeight)
                        val shiftX = extraX * xOffset
                        val srcLeft = (shiftX / scale).toInt().coerceIn(0, max(0, image.width - 1))
                        val srcTop = (extraY / scale / 2f).toInt().coerceIn(0, max(0, image.height - 1))
                        val srcWidth = min(image.width - srcLeft, (surfaceWidth / scale).toInt().coerceAtLeast(1))
                        val srcHeight = min(image.height - srcTop, (surfaceHeight / scale).toInt().coerceAtLeast(1))
                        canvas.drawBitmap(image, Rect(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight),
                            Rect(0, 0, surfaceWidth, surfaceHeight), paint)
                    }
                }
            } catch (_: Exception) {
                // Avoid crashing the launcher if a wallpaper surface becomes unavailable.
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
            }
            if (visible) handler.postDelayed(drawRunner, 1000L / 20L)
        }
    }
}
