package com.tiko.customwallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max

class ParallaxWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ParallaxEngine()

    private inner class ParallaxEngine : Engine(), SensorEventListener {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
        private var motionSensor: Sensor? = null
        private var bottom: Bitmap? = null
        private var middle: Bitmap? = null
        private var top: Bitmap? = null
        private var launcherX = 0f
        private var tiltX = 0f
        private var tiltY = 0f
        private var smoothX = 0f
        private var smoothY = 0f
        private var visible = false
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadLayers()
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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
                } catch (_: Exception) { null }
            }
            bottom = load(MainActivity.KEY_BOTTOM_URI) ?: load(MainActivity.KEY_IMAGE_URI)
            middle = load(MainActivity.KEY_MIDDLE_URI)
            top = load(MainActivity.KEY_TOP_URI)
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            if (visibleNow) {
                motionSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                drawFrame()
            } else {
                sensorManager.unregisterListener(this)
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            drawFrame()
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float,
                                      yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            launcherX = ((xOffset - 0.5f) * 2f).coerceIn(-1f, 1f)
            drawFrame()
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val targetX = (event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            val targetY = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            smoothX = smoothX * 0.82f + targetX * 0.18f
            smoothY = smoothY * 0.82f + targetY * 0.18f
            tiltX = smoothX
            tiltY = smoothY
            if (visible) drawFrame()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onDestroy() {
            visible = false
            sensorManager.unregisterListener(this)
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
                    drawLayer(canvas, bottom, 0.20f)
                    drawLayer(canvas, middle, 0.55f)
                    drawLayer(canvas, top, 1.0f)
                }
            } catch (_: Exception) {
                // Wallpaper surface can be recreated by the launcher.
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
            if (visible) handler.postDelayed(drawRunner, 1000L / 30L)
        }

        private fun drawLayer(canvas: Canvas, bitmap: Bitmap?, depth: Float) {
            if (bitmap == null || bitmap.isRecycled) return
            // Overscan leaves room for motion without exposing empty edges.
            val scale = max(surfaceWidth.toFloat() / bitmap.width,
                            surfaceHeight.toFloat() / bitmap.height) * 1.16f
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val extraX = max(0f, scaledWidth - surfaceWidth)
            val extraY = max(0f, scaledHeight - surfaceHeight)
            val shiftX = (launcherX * 0.22f + tiltX * 0.42f) * extraX * depth
            val shiftY = (-tiltY * 0.42f) * extraY * depth
            val left = (surfaceWidth - scaledWidth) / 2f - shiftX
            val top = (surfaceHeight - scaledHeight) / 2f - shiftY
            canvas.drawBitmap(bitmap, null,
                RectF(left, top, left + scaledWidth, top + scaledHeight), paint)
        }
    }
}
