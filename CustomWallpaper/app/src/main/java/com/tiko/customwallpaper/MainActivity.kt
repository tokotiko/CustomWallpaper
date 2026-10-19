package com.tiko.customwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : Activity() {
    private lateinit var preview: ImageView
    private var selectedUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some document providers do not offer persistable grants; preview still works for this session.
            }
            selectedUri = uri
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_IMAGE_URI, uri.toString()).apply()
            preview.setImageURI(uri)
            Toast.makeText(this, "Custom image berhasil dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_IMAGE_URI, null)?.let {
            selectedUri = Uri.parse(it)
            preview.setImageURI(selectedUri)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(24))
            setBackgroundColor(0xFF102D25.toInt())
        }
        val title = TextView(this).apply {
            text = "CUSTOM WALLPAPER"
            textSize = 23f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        val subtitle = TextView(this).apply {
            text = "Pilih gambar sendiri, lalu aktifkan efek parallax"
            textSize = 14f
            setTextColor(0xFFD2E4DC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }
        preview = ImageView(this).apply {
            setBackgroundColor(0xFF203D34.toInt())
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Preview custom wallpaper"
        }
        root.addView(title, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(subtitle, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = dp(20) })
        root.addView(makeButton("ADD CUSTOM IMAGE") { pickImage.launch(arrayOf("image/*")) }, match(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(makeButton("SET STATIC WALLPAPER") { setStaticWallpaper() }, match(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })
        root.addView(makeButton("ACTIVATE PARALLAX WALLPAPER") { activateLiveWallpaper() }, match(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })
        setContentView(root)
    }

    private fun setStaticWallpaper() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "Pilih gambar dulu", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bitmap: Bitmap? = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                Toast.makeText(this, "Gambar tidak bisa dibaca", Toast.LENGTH_LONG).show()
                return
            }
            WallpaperManager.getInstance(this).setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper statis berhasil dipasang", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memasang wallpaper: ${e.localizedMessage ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun activateLiveWallpaper() {
        if (selectedUri == null) {
            Toast.makeText(this, "Pilih gambar dulu", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ParallaxWallpaperService::class.java))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka pengaturan wallpaper: ${e.localizedMessage ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF176B52.toInt())
        setOnClickListener { action() }
    }

    private fun match(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "custom_wallpaper_prefs"
        const val KEY_IMAGE_URI = "image_uri"
    }
}
