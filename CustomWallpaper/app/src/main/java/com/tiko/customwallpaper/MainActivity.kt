package com.tiko.customwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var bottomPreview: ImageView
    private lateinit var middlePreview: ImageView
    private lateinit var topPreview: ImageView
    private var activeLayer: String = KEY_BOTTOM_URI

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not support persistent URI grants.
            }

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(activeLayer, uri.toString())
                // Keep compatibility with older builds that used one image URI.
                .putString(KEY_IMAGE_URI, uri.toString())
                .apply()

            previewFor(activeLayer).setImageURI(uri)
            Toast.makeText(this, "Gambar layer berhasil dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSavedPreview(KEY_BOTTOM_URI, bottomPreview)
        loadSavedPreview(KEY_MIDDLE_URI, middlePreview)
        loadSavedPreview(KEY_TOP_URI, topPreview)
    }

    private fun buildUi() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(24), dp(18), dp(24))
            setBackgroundColor(0xFF102D25.toInt())
        }

        content.addView(TextView(this).apply {
            text = "CUSTOM WALLPAPER"
            textSize = 23f
            gravity = Gravity.CENTER
            letterSpacing = 0.07f
            setTextColor(0xFFFFFFFF.toInt())
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(TextView(this).apply {
            text = "Pilih 3 gambar untuk membuat wallpaper parallax"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFD2E4DC.toInt())
            setPadding(0, dp(8), 0, dp(18))
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        bottomPreview = addLayerPicker(content, "BOTTOM LAYER", "Pilih gambar latar belakang", KEY_BOTTOM_URI)
        middlePreview = addLayerPicker(content, "MIDDLE LAYER", "Pilih gambar lapisan tengah", KEY_MIDDLE_URI)
        topPreview = addLayerPicker(content, "TOP LAYER", "Pilih gambar lapisan depan (PNG transparan disarankan)", KEY_TOP_URI)

        content.addView(makeButton("SET STATIC WALLPAPER") { setStaticWallpaper() },
            match(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(16) })
        content.addView(makeButton("ACTIVATE 3-LAYER PARALLAX") { activateLiveWallpaper() },
            match(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            setBackgroundColor(0xFF102D25.toInt())
        }
        setContentView(scroll)
    }

    private fun addLayerPicker(parent: LinearLayout, title: String, hint: String, key: String): ImageView {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(10), 0, dp(4))
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        parent.addView(TextView(this).apply {
            text = hint
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFD2E4DC.toInt())
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val preview = ImageView(this).apply {
            setBackgroundColor(0xFF203D34.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = title
        }
        parent.addView(preview, match(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)).apply {
            topMargin = dp(8)
            bottomMargin = dp(6)
        })
        parent.addView(makeButton("ADD $title IMAGE") {
            activeLayer = key
            pickImage.launch(arrayOf("image/*"))
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        return preview
    }

    private fun loadSavedPreview(key: String, target: ImageView) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val uriText = prefs.getString(key, null)
            ?: if (key == KEY_BOTTOM_URI) prefs.getString(KEY_IMAGE_URI, null) else null
        if (!uriText.isNullOrBlank()) {
            try {
                target.setImageURI(Uri.parse(uriText))
            } catch (_: Exception) {
                target.setImageDrawable(null)
            }
        }
    }

    private fun previewFor(key: String): ImageView = when (key) {
        KEY_BOTTOM_URI -> bottomPreview
        KEY_MIDDLE_URI -> middlePreview
        else -> topPreview
    }

    private fun selectedUri(key: String): Uri? {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val text = prefs.getString(key, null)
            ?: if (key == KEY_BOTTOM_URI) prefs.getString(KEY_IMAGE_URI, null) else null
        return text?.let { Uri.parse(it) }
    }

    private fun setStaticWallpaper() {
        val uris = listOf(KEY_BOTTOM_URI, KEY_MIDDLE_URI, KEY_TOP_URI).mapNotNull { selectedUri(it) }
        if (uris.isEmpty()) {
            toast("Pilih minimal satu gambar dulu")
            return
        }
        try {
            val bitmaps = uris.mapNotNull { uri ->
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            if (bitmaps.isEmpty()) {
                toast("Gambar tidak bisa dibaca")
                return
            }

            val width = bitmaps.maxOf { it.width }.coerceAtMost(4096)
            val height = bitmaps.maxOf { it.height }.coerceAtMost(8192)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(0xFF102D25.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Draw in order: bottom, middle, top. Transparent PNG areas reveal layers below.
            bitmaps.forEach { bitmap ->
                drawCover(canvas, bitmap, width, height, paint)
            }
            WallpaperManager.getInstance(this).setBitmap(output)
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
            output.recycle()
            toast("Wallpaper statis berhasil dipasang")
        } catch (e: SecurityException) {
            toast("Izin memasang wallpaper ditolak: ${e.localizedMessage ?: "permission error"}")
        } catch (e: Exception) {
            toast("Gagal memasang wallpaper: ${e.localizedMessage ?: "error"}")
        }
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int, paint: Paint) {
        val scale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val scaledWidth = (width / scale).toInt().coerceIn(1, bitmap.width)
        val scaledHeight = (height / scale).toInt().coerceIn(1, bitmap.height)
        val left = ((bitmap.width - scaledWidth) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - scaledHeight) / 2).coerceAtLeast(0)
        canvas.drawBitmap(
            bitmap,
            Rect(left, top, left + scaledWidth, top + scaledHeight),
            Rect(0, 0, width, height),
            paint
        )
    }

    private fun activateLiveWallpaper() {
        if (selectedUri(KEY_BOTTOM_URI) == null &&
            selectedUri(KEY_MIDDLE_URI) == null &&
            selectedUri(KEY_TOP_URI) == null
        ) {
            toast("Pilih minimal satu gambar dulu")
            return
        }
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ParallaxWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Tidak bisa membuka pengaturan wallpaper: ${e.localizedMessage ?: "error"}")
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF176B52.toInt())
        setOnClickListener { action() }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun match(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "custom_wallpaper_prefs"
        const val KEY_IMAGE_URI = "image_uri" // Compatibility with the previous single-image build.
        const val KEY_BOTTOM_URI = "bottom_image_uri"
        const val KEY_MIDDLE_URI = "middle_image_uri"
        const val KEY_TOP_URI = "top_image_uri"
    }
}
