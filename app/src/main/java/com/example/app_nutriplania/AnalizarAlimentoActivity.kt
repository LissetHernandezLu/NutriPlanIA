package com.example.app_nutriplania

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

class AnalizarAlimentoActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val gson = Gson()

    // 🛡️ API KEY desde BuildConfig
    private val OPENAI_API_KEY =BuildConfig.OPENAI_API_KEY;

    // UI References
    private lateinit var etAlimento: TextInputEditText
    private lateinit var cardResultados: MaterialCardView
    private lateinit var tvCalorias: TextView
    private lateinit var tvProteinas: TextView
    private lateinit var tvCarbos: TextView
    private lateinit var tvGrasas: TextView
    private lateinit var btnVolver: ImageButton

    // 📸 Lanzador de Cámara
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val resized = resizeBitmap(it, 512, 512)
                val base64 = bitmapToBase64(resized)
                analizarImagenConIA(base64)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            abrirCamara()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analizar_alimento)

        // Inicializar vistas
        etAlimento = findViewById(R.id.etAlimentoAnalizar)
        cardResultados = findViewById(R.id.cardResultadosSolo)
        tvCalorias = findViewById(R.id.tvCaloriasSolo)
        tvProteinas = findViewById(R.id.tvProteinasSolo)
        tvCarbos = findViewById(R.id.tvCarbosSolo)
        tvGrasas = findViewById(R.id.tvGrasasSolo)
        btnVolver = findViewById(R.id.btnVolverInicio)

        cardResultados.visibility = View.GONE

        // Botón Analizar Texto
        findViewById<Button>(R.id.btnAnalizarSolo).setOnClickListener {
            val texto = etAlimento.text.toString().trim()
            if (texto.isNotEmpty()) analizarTextoConIA(texto)
            else Toast.makeText(this, "Escribe un alimento", Toast.LENGTH_SHORT).show()
        }

        // Botón Tomar Foto
        findViewById<Button>(R.id.btnTomarFotoSolo).setOnClickListener {
            verificarPermisosYAbirCamara()
        }

        // Botón Volver
        btnVolver.setOnClickListener {
            finish()
        }
    }

    private fun verificarPermisosYAbirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        val ratioBitmap = width.toFloat() / height.toFloat()
        if (width > maxWidth || height > maxHeight) {
            if (ratioBitmap > 1) {
                width = maxWidth
                height = (width / ratioBitmap).toInt()
            } else {
                height = maxHeight
                width = (height * ratioBitmap).toInt()
            }
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return bitmap
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun analizarTextoConIA(alimento: String) {
        val prompt = "Analiza el alimento: '$alimento' y responde SOLO este JSON: { \"nombre\": \"$alimento\", \"calorias\": 0, \"proteinas\": 0.0, \"carbohidratos\": 0.0, \"grasas\": 0.0 }"
        enviarPeticionOpenAI(prompt, null)
    }

    private fun analizarImagenConIA(base64Image: String) {
        val prompt = "Analiza la imagen de comida y devuelve SOLO este JSON válido: { \"nombre\": \"\", \"calorias\": 0, \"proteinas\": 0.0, \"carbohidratos\": 0.0, \"grasas\": 0.0 }"
        enviarPeticionOpenAI(prompt, base64Image)
    }

    private fun enviarPeticionOpenAI(prompt: String, base64Image: String?) {
        val url = "https://api.openai.com/v1/chat/completions"

        val contentList = mutableListOf<Map<String, Any>>(
            mapOf("type" to "text", "text" to prompt)
        )

        base64Image?.let {
            contentList.add(mapOf(
                "type" to "image_url",
                "image_url" to mapOf("url" to "data:image/jpeg;base64,$it")
            ))
        }

        val requestMap = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to contentList
                )
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val json = gson.toJson(requestMap)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(body)
            .build()

        runOnUiThread { Toast.makeText(this, "Analizando con IA...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@AnalizarAlimentoActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Log.e("OpenAI_Error", "Code: ${response.code} Body: $responseBody")
                        runOnUiThread { Toast.makeText(this@AnalizarAlimentoActivity, "Error ${response.code} en IA", Toast.LENGTH_LONG).show() }
                        return
                    }

                    try {
                        val jsonResponse = gson.fromJson(responseBody, Map::class.java)
                        val choices = jsonResponse["choices"] as List<*>
                        val firstChoice = choices[0] as Map<*, *>
                        val message = firstChoice["message"] as Map<*, *>
                        var content = message["content"].toString()

                        if (content.contains("{")) {
                            content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1)
                        }

                        val info = gson.fromJson(content, NutritionalInfo::class.java)
                        runOnUiThread { 
                            info.nombre?.let { if (it.isNotEmpty()) etAlimento.setText(it) }
                            actualizarUI(info) 
                        }
                    } catch (e: Exception) {
                        Log.e("Parse_Error", "Error: ${e.message} Content: $responseBody")
                        runOnUiThread { Toast.makeText(this@AnalizarAlimentoActivity, "Error procesando IA", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        })
    }

    private fun actualizarUI(info: NutritionalInfo) {
        cardResultados.visibility = View.VISIBLE
        tvCalorias.text = "Calorías: ${info.calorias} kcal"
        tvProteinas.text = "Proteínas: ${info.proteinas} g"
        tvCarbos.text = "Carbohidratos: ${info.carbohidratos} g"
        tvGrasas.text = "Grasas: ${info.grasas} g"
    }

    data class NutritionalInfo(
        val nombre: String? = "",
        val calorias: Int,
        val proteinas: Double,
        val carbohidratos: Double,
        val grasas: Double
    )
}
