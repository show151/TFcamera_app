package jp.wings.nikkeibp.tfcameraapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("DEPRECATION", "NAME_SHADOWING")
class MainActivity : AppCompatActivity() {
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_CODE = 101
    private lateinit var tflite: Interpreter
    private lateinit var labels: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            val model = FileUtil.loadMappedFile(this, "yolo11n-seg_float32.tflite")
            val options = Interpreter.Options()
            options.addDelegate(NnApiDelegate())
            tflite = Interpreter(model, options)
            Log.d("MainActivity", "Model loaded successfully with NNAPI Delegate")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MainActivity", "Error loading model with NNAPI Delegate: ${e.message}")
        }

        // ラベルのロード
        labels = loadLabels()

        val cameraButton: Button = findViewById(R.id.camera_button)
        cameraButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            }
            else {
                openCamera()
            }
        }

        // ストレージアクセス権限の確認とリクエスト
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_CODE)
        }
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openCamera()
            } else {
                // パーミッションが拒否された場合の処理
                showPermissionDeniedDialog()
            }
        }

        if (requestCode == REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("MainActivity", "Storage permission granted")
                readFile()
            } else {
                Log.d("MainActivity", "Storage permission denied")
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("カメラのパーミッションが必要です")
            .setMessage("このアプリを使用するにはカメラのパーミッションが必要です。設定からパーミッションを許可してください。")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                if (data != null && data.extras != null) {
                    val imageBitmap = data.extras?.getParcelable<Bitmap>("data")
                    if (imageBitmap != null) {
                        Log.d("MainActivity", "Image captured successfully")
                        val resultText = runYOLO11sSeg(imageBitmap)

                        // 画像を一時ファイルとして保存
                        val imageFile = saveBitmapToFile(imageBitmap)
                        val imageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)

                        val intent = Intent(this, TranslateActivity::class.java).apply {
                            putExtra("imageUri", imageUri.toString())
                            putExtra("resultText", resultText)
                        }
                        startActivity(intent)
                    } else {
                        Log.e("MainActivity", "Failed to capture image")
                    }
                } else {
                    Log.e("MainActivity", "No image data found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainActivity", "Error processing image: ${e.message}")
            }
        } else {
            Log.e("MainActivity", "Image capture failed or canceled")
        }
    }

    private fun runYOLO11sSeg(bitmap: Bitmap): String {
        // 画像を前処理してfloat32のByteBufferに変換
        val inputBuffer = processImageForModel(bitmap)

        // 出力バッファの準備
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 25200, 85), DataType.FLOAT32)

        // 推論の実行
        tflite.run(inputBuffer, outputBuffer.buffer.rewind())

        // 推論結果の処理
        val outputArray = outputBuffer.floatArray
        val resultText = StringBuilder()

        // YOLOv8s-segの出力を解析
        for (i in outputArray.indices step 85) {
            val confidence = outputArray[i + 4]
            if (confidence > 0.7) { // 信頼度が0.7以上の検出結果のみを使用
                val classId = outputArray[i + 7].toInt()
                val className = getClassLabel(classId)
                resultText.append("Detected: $className\n")
            }
        }

        return resultText.toString()
    }

    private fun getClassLabel(classId: Int): String {
        return if (classId in labels.indices) labels[classId] else "Unknown"
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        assets.open("yolo11data.txt").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachLine { line ->
                    labels.add(line)
                }
            }
        }
        return labels
    }

    // 指定したサイズに Bitmap をリサイズ（640x640）
    private fun preprocessImage(bitmap: Bitmap, targetWidth: Int = 640, targetHeight: Int = 640): ByteBuffer {
        // 画像をリサイズ
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        // uint8バッファを用意（640 * 640 * 3 = 1,228,800 バイト）
        val inputBuffer = ByteBuffer.allocateDirect(targetWidth * targetHeight * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        // Bitmap のピクセルを ByteBuffer に変換
        val intValues = IntArray(targetWidth * targetHeight)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        // ピクセルデータをバッファに詰める (ARGB -> RGB)
        for (pixel in intValues) {
            // 0xFF で各色チャンネルの値を抽出して格納
            val r = (pixel shr 16) and 0xFF  // Red
            val g = (pixel shr 8) and 0xFF   // Green
            val b = pixel and 0xFF           // Blue

            // uint8 バッファにRGB値を格納
            inputBuffer.put(r.toByte())
            inputBuffer.put(g.toByte())
            inputBuffer.put(b.toByte())
        }

        // バッファの位置を0にリセット
        inputBuffer.rewind()

        return inputBuffer
    }

    // ファイルを読み取るメソッドを追加
    private fun readFile() {
        try {
            val file = File("/cache/captured_image.png")
            val inputStream = FileInputStream(file)
            // ファイルの読み取り処理
            Log.d("MainActivity", "File read successfully")
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Log.e("MainActivity", "File not found: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MainActivity", "Error reading file: ${e.message}")
        }
    }

    // uint8 の ByteBuffer を float32 の ByteBuffer に変換する関数
    private fun convertToFloat32(inputBuffer: ByteBuffer, targetWidth: Int = 640, targetHeight: Int = 640): ByteBuffer {
        // float32 用のバッファ (4バイト * 640 * 640 * 3 = 4,915,200バイトのバッファ)
        val floatBuffer = ByteBuffer.allocateDirect(4 * targetWidth * targetHeight * 3)
        floatBuffer.order(ByteOrder.nativeOrder())

        // uint8データ (0-255) を float32 データ (0.0-1.0) に変換
        for (i in 0 until inputBuffer.capacity()) {
            floatBuffer.putFloat((inputBuffer.get(i).toInt() and 0xFF) / 255.0f)
        }

        // バッファの位置を0にリセット
        floatBuffer.rewind()

        return floatBuffer
    }

    // 画像をByteBufferに変換し、TensorFlow Lite用のfloat32バッファにするまでの一連の処理
    private fun processImageForModel(bitmap: Bitmap): ByteBuffer {
        // 画像を前処理してuint8のByteBufferに変換
        val inputBuffer = preprocessImage(bitmap)

        // uint8のバッファをfloat32に変換
        return convertToFloat32(inputBuffer)
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val file = File(cacheDir, "captured_image.png")
        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("MainActivity", "Error saving bitmap to file: ${e.message}")
        }
        return file
    }

}
