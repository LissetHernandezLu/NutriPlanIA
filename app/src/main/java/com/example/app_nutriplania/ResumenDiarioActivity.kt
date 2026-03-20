package com.example.app_nutriplania

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import android.content.Intent

class ResumenDiarioActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val client = OkHttpClient()
    private val gson = Gson()

    // 🔑 OPENAI API KEY
    private val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY;

    private lateinit var tvTotalCalorias: TextView
    private lateinit var tvTotalProteinas: TextView
    private lateinit var tvTotalCarbos: TextView
    private lateinit var tvTotalGrasas: TextView
    private lateinit var tvCantidadComidas: TextView
    private lateinit var tvEvaluacion: TextView
    private lateinit var tvRecomendacionIA: TextView
    private lateinit var pbCargandoIA: ProgressBar
    private lateinit var btnVolver: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resumen_diario)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvTotalCalorias = findViewById(R.id.tvTotalCalorias)
        tvTotalProteinas = findViewById(R.id.tvTotalProteinas)
        tvTotalCarbos = findViewById(R.id.tvTotalCarbos)
        tvTotalGrasas = findViewById(R.id.tvTotalGrasas)
        tvCantidadComidas = findViewById(R.id.tvCantidadComidas)
        tvEvaluacion = findViewById(R.id.tvEvaluacion)
        tvRecomendacionIA = findViewById(R.id.tvRecomendacionIA)
        pbCargandoIA = findViewById(R.id.pbCargandoIA)
        btnVolver = findViewById(R.id.btnVolverAComida)

        findViewById<Button>(R.id.btnActualizarResumen).setOnClickListener {
            cargarDatos()
        }

        btnVolver.setOnClickListener {
            val intent = Intent(this, RegistrarComidaActivity::class.java)
            startActivity(intent)
            finish()
        }

        cargarDatos()
    }

    private fun cargarDatos() {
        val uid = auth.currentUser?.uid ?: return

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val inicioDia = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val finDia = calendar.time

        db.collection("usuarios").document(uid)
            .collection("registro_comida")
            .whereGreaterThanOrEqualTo("fecha", inicioDia)
            .whereLessThanOrEqualTo("fecha", finDia)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    tvEvaluacion.text = "No hay registros hoy"
                    tvRecomendacionIA.text = "Registra comidas para obtener recomendación"
                    limpiarUI()
                } else {
                    calcularTotales(documents.documents)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calcularTotales(registros: List<com.google.firebase.firestore.DocumentSnapshot>) {
        var totalCal = 0
        var totalProt = 0.0
        var totalCarb = 0.0
        var totalGras = 0.0

        for (doc in registros) {
            totalCal += doc.getLong("calorias")?.toInt() ?: 0
            totalProt += doc.getDouble("proteinas") ?: 0.0
            totalCarb += doc.getDouble("carbohidratos") ?: 0.0
            totalGras += doc.getDouble("grasas") ?: 0.0
        }

        tvTotalCalorias.text = "Calorías: $totalCal kcal"
        tvTotalProteinas.text = "Proteínas: ${"%.1f".format(totalProt)} g"
        tvTotalCarbos.text = "Carbohidratos: ${"%.1f".format(totalCarb)} g"
        tvTotalGrasas.text = "Grasas: ${"%.1f".format(totalGras)} g"
        tvCantidadComidas.text = "Comidas: ${registros.size}"

        tvEvaluacion.text = when {
            totalCal < 1500 -> "Consumo bajo"
            totalCal in 1500..2500 -> "Consumo adecuado"
            else -> "Consumo alto"
        }

        generarRecomendacionIA(totalCal, totalProt, totalCarb, totalGras)
    }

    private fun generarRecomendacionIA(cal: Int, prot: Double, carb: Double, gras: Double) {

        pbCargandoIA.visibility = View.VISIBLE
        tvRecomendacionIA.text = "Generando recomendación..."

        val url = "https://api.openai.com/v1/responses"

        val prompt = """
            Eres un nutricionista profesional.

            Datos del día:
            Calorías: $cal kcal
            Proteínas: ${"%.1f".format(prot)} g
            Carbohidratos: ${"%.1f".format(carb)} g
            Grasas: ${"%.1f".format(gras)} g

            Da una recomendación breve (máximo 2 líneas) para mejorar la alimentación mañana.
        """.trimIndent()

        val requestMap = mapOf(
            "model" to "gpt-4o-mini",
            "input" to prompt
        )

        val json = gson.toJson(requestMap)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    pbCargandoIA.visibility = View.GONE
                    tvRecomendacionIA.text = "Error de red"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {

                    val responseBody = response.body?.string() ?: return

                    if (!response.isSuccessful) {
                        runOnUiThread {
                            pbCargandoIA.visibility = View.GONE
                            tvRecomendacionIA.text = "Error ${response.code}"
                        }
                        return
                    }

                    try {
                        val jsonObject = gson.fromJson(responseBody, Map::class.java)

                        val output = jsonObject["output"] as List<*>
                        val message = output[0] as Map<*, *>
                        val contentList = message["content"] as List<*>
                        val textMap = contentList[0] as Map<*, *>
                        val content = textMap["text"].toString()

                        runOnUiThread {
                            pbCargandoIA.visibility = View.GONE
                            tvRecomendacionIA.text = content.trim()
                        }

                    } catch (e: Exception) {
                        runOnUiThread {
                            pbCargandoIA.visibility = View.GONE
                            tvRecomendacionIA.text = "Error procesando IA"
                        }
                    }
                }
            }
        })
    }

    private fun limpiarUI() {
        tvTotalCalorias.text = "Calorías: 0 kcal"
        tvTotalProteinas.text = "Proteínas: 0 g"
        tvTotalCarbos.text = "Carbohidratos: 0 g"
        tvTotalGrasas.text = "Grasas: 0 g"
        tvCantidadComidas.text = "Comidas: 0"
    }
}