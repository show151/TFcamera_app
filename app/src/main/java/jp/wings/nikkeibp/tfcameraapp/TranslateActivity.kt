package jp.wings.nikkeibp.tfcameraapp

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class TranslateActivity : AppCompatActivity() {
    private val apiKey = "AIzaSyCPgzqxqMyO5PWo2UoNPHrviyXk9QHpzqc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)
        setContentView(R.layout.activity_result)

        val imageUriString = intent.getStringExtra("imageUri")
        val resultText = intent.getStringExtra("resultText")

        if (imageUriString != null) {
            val imageUri = Uri.parse(imageUriString)
            val imageFile = File(imageUri.path!!)
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

            // 画像をImageViewに表示
            val imageView: ImageView = findViewById(R.id.image_view)
            imageView.setImageBitmap(bitmap)

            // 翻訳結果をTextViewに表示
            val originalTextView: TextView = findViewById(R.id.original_text)
            originalTextView.text = resultText

            // Google Translate APIを使用して翻訳
            translateText(resultText ?: "")
        }
    }

    private fun translateText(text: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://translation.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(TranslateService::class.java)

        val call = service.translateText(apiKey, text, "ja")
        call.enqueue(object : Callback<TranslateResponse> {
            override fun onResponse(call: Call<TranslateResponse>, response: Response<TranslateResponse>) {
                val translatedText = response.body()?.data?.translations?.firstOrNull()?.translatedText
                val translatedTextView: TextView = findViewById(R.id.translated_text)
                translatedTextView.text = translatedText
            }

            override fun onFailure(call: Call<TranslateResponse>, t: Throwable) {
                // エラーハンドリング
                t.printStackTrace()
            }
        })
    }
}