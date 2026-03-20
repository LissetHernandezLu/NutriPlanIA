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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

class RegistrarComidaActivity : AppCompatActivity() {

    // Instancias de Firebase, cliente HTTP y procesamiento JSON
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val client = OkHttpClient()
    private val gson = Gson()

    // Clave de acceso para la API de OpenAI
    private val OPENAI_API_KEY =BuildConfig.OPENAI_API_KEY;

    // Almacena los resultados del ultimo analisis realizado
    private var infoNutricionalActual: NutritionalInfo? = null

    // Referencias a los componentes visuales
    private lateinit var etAlimento: TextInputEditText
    private lateinit var cardResultados: MaterialCardView
    private lateinit var tvCalorias: TextView
    private lateinit var tvProteinas: TextView
    private lateinit var tvCarbos: TextView
    private lateinit var tvGrasas: TextView
    private lateinit var btnGuardar: Button
    private lateinit var actvTipoComida: AutoCompleteTextView
    private lateinit var btnVolverPerfil: ImageButton

    // Manejador para capturar la imagen de la camara y procesarla
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                // Redimensiona, convierte a Base64 y envia a la IA
                val resized = resizeBitmap(it, 512, 512)
                val base64 = bitmapToBase64(resized)
                analizarImagenConIA(base64)
            }
        }
    }

    // Manejador para la solicitud de permisos de la camara
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            abrirCamara()
        } else {
            Toast.makeText(this, "Permiso de camara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrar_comida)

        // Inicializacion de servicios
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Vinculacion de vistas con el codigo
        etAlimento = findViewById(R.id.etAlimento)
        actvTipoComida = findViewById(R.id.actvTipoComida)
        cardResultados = findViewById(R.id.cardResultados)
        tvCalorias = findViewById(R.id.tvCalorias)
        tvProteinas = findViewById(R.id.tvProteinas)
        tvCarbos = findViewById(R.id.tvCarbos)
        tvGrasas = findViewById(R.id.tvGrasas)
        btnGuardar = findViewById(R.id.btnGuardarComida)
        btnVolverPerfil = findViewById(R.id.btnVolverPerfil)

        // Configuracion inicial de la interfaz
        cardResultados.visibility = View.GONE
        btnGuardar.isEnabled = false

        // Llena el selector de tipos de comida
        val opciones = arrayOf("Desayuno", "Almuerzo", "Cena", "Snack")
        actvTipoComida.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, opciones))

        // Inicia el analisis mediante texto ingresado
        findViewById<Button>(R.id.btnAnalizar).setOnClickListener {
            val texto = etAlimento.text.toString().trim()
            if (texto.isNotEmpty()) analizarTextoConIA(texto)
            else Toast.makeText(this, "Escribe un alimento", Toast.LENGTH_SHORT).show()
        }

        // Inicia el proceso de captura de fotografia
        findViewById<Button>(R.id.btnTomarFoto)?.setOnClickListener {
            verificarPermisosYAbirCamara()
        }

        // Vuelve a la pantalla de perfil nutricional
        btnVolverPerfil.setOnClickListener {
            val intent = Intent(this, PerfilNutricionalActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Almacena el registro final en Firestore
        btnGuardar.setOnClickListener {
            val tipo = actvTipoComida.text.toString()
            val nombre = etAlimento.text.toString()

            if (tipo.isNotEmpty() && infoNutricionalActual != null) {
                guardarEnFirestore(tipo, nombre, infoNutricionalActual!!)
            } else {
                Toast.makeText(this, "Selecciona el tipo de comida", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gestiona la solicitud de permisos antes de abrir la camara
    private fun verificarPermisosYAbirCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Lanza la aplicacion de camara del sistema
    private fun abrirCamara() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    // Reduce el tamaño de la imagen para optimizar el envio por red
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

    // Codifica la imagen en una cadena de texto para la API
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // Prepara el prompt de texto para enviar a la IA
    private fun analizarTextoConIA(alimento: String) {
        val prompt = "Analiza el alimento: '$alimento' y responde SOLO este JSON: { \"nombre\": \"$alimento\", \"calorias\": 0, \"proteinas\": 0.0, \"carbohidratos\": 0.0, \"grasas\": 0.0 }"
        enviarPeticionOpenAI(prompt, null)
    }

    // Prepara el prompt de imagen para enviar a la IA
    private fun analizarImagenConIA(base64Image: String) {
        val prompt = "Analiza la imagen de comida y devuelve SOLO este JSON válido: { \"nombre\": \"\", \"calorias\": 0, \"proteinas\": 0.0, \"carbohidratos\": 0.0, \"grasas\": 0.0 }"
        enviarPeticionOpenAI(prompt, base64Image)
    }

    // Realiza la peticion HTTP a OpenAI para obtener el analisis nutricional
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

        runOnUiThread { Toast.makeText(this, "Analizando...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@RegistrarComidaActivity, "Error de red: ${e.message}", Toast.LENGTH_LONG).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Log.e("OpenAI_Error", "Code: ${response.code} Body: $responseBody")
                        runOnUiThread { Toast.makeText(this@RegistrarComidaActivity, "Error ${response.code} en IA", Toast.LENGTH_LONG).show() }
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
                        runOnUiThread { Toast.makeText(this@RegistrarComidaActivity, "Error procesando IA", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        })
    }

    // Refleja los resultados del analisis en las etiquetas de la pantalla
    private fun actualizarUI(info: NutritionalInfo) {
        infoNutricionalActual = info
        cardResultados.visibility = View.VISIBLE
        tvCalorias.text = "Calorias: ${info.calorias} kcal"
        tvProteinas.text = "Proteinas: ${info.proteinas} g"
        tvCarbos.text = "Carbohidratos: ${info.carbohidratos} g"
        tvGrasas.text = "Grasas: ${info.grasas} g"
        btnGuardar.isEnabled = true
    }

    // Almacena la informacion en la subcoleccion registro_comida del usuario
    private fun guardarEnFirestore(tipo: String, nombre: String, info: NutritionalInfo) {
        val uid = auth.currentUser?.uid ?: return
        val registro = hashMapOf(
            "tipo_comida" to tipo,
            "nombre_alimento" to nombre,
            "calorias" to info.calorias,
            "proteinas" to info.proteinas,
            "carbohidratos" to info.carbohidratos,
            "grasas" to info.grasas,
            "fecha" to Timestamp.now()
        )

        db.collection("usuarios").document(uid).collection("registro_comida").add(registro)
            .addOnSuccessListener {
                Toast.makeText(this, "Guardado correctamente", Toast.LENGTH_SHORT).show()
                // Dirige al resumen diario tras el guardado
                startActivity(Intent(this, ResumenDiarioActivity::class.java))
                finish()
            }
            .addOnFailureListener { Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show() }
    }

    // Modelo de datos para la informacion nutricional
    data class NutritionalInfo(
        val nombre: String? = "",
        val calorias: Int,
        val proteinas: Double,
        val carbohidratos: Double,
        val grasas: Double
    )
}
