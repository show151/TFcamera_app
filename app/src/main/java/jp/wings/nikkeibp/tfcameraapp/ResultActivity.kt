package jp.wings.nikkeibp.tfcameraapp

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@Suppress("DEPRECATION")
class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val originalText = intent.getStringExtra("originalText")
        val translatedText = intent.getStringExtra("translatedText")
        val imageBitmap = intent.getParcelableExtra<Bitmap>("imageBitmap")

        val originalTextView: TextView = findViewById(R.id.original_text)
        val translatedTextView: TextView = findViewById(R.id.translated_text)
        val imageView: ImageView = findViewById(R.id.image_view)

        originalTextView.text = originalText
        translatedTextView.text = translatedText
        imageView.setImageBitmap(imageBitmap)
    }
}