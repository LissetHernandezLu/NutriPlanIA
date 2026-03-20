package com.example.app_nutriplania

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PerfilNutricionalActivity : AppCompatActivity() {

    // Instancias para autenticacion y base de datos
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuracion de pantalla completa
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil_nutricional)

        // Inicializacion de Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Referencias a campos de entrada de texto y selectores
        val editEdad = findViewById<EditText>(R.id.editEdad)
        val editPeso = findViewById<EditText>(R.id.editPeso)
        val editAltura = findViewById<EditText>(R.id.editAltura)
        val spinnerObjetivo = findViewById<AutoCompleteTextView>(R.id.spinnerObjetivo)
        val spinnerActividad = findViewById<AutoCompleteTextView>(R.id.spinnerActividad)
        
        val spinnerGenero = findViewById<AutoCompleteTextView>(R.id.spinnerGenero)
        val editPesoObjetivo = findViewById<EditText>(R.id.editPesoObjetivo)
        val spinnerVelocidad = findViewById<AutoCompleteTextView>(R.id.spinnerVelocidad)
        val spinnerComidas = findViewById<AutoCompleteTextView>(R.id.spinnerComidas)
        val spinnerDieta = findViewById<AutoCompleteTextView>(R.id.spinnerTipoDieta)
        val spinnerVariedad = findViewById<AutoCompleteTextView>(R.id.spinnerVariedad)
        val tvResultado = findViewById<TextView>(R.id.tvCaloriasResultado)
        
        val btnGuardar = findViewById<Button>(R.id.btnGuardarPerfil)

        // Configura los datos de las listas desplegables
        setupSpinners(spinnerGenero, spinnerObjetivo, spinnerActividad, spinnerVelocidad, spinnerComidas, spinnerDieta, spinnerVariedad)

        // Manejador del boton de guardado
        btnGuardar.setOnClickListener {
            val edad = editEdad.text.toString()
            val peso = editPeso.text.toString()
            val altura = editAltura.text.toString()
            val pObjetivo = editPesoObjetivo.text.toString()
            
            val genero = spinnerGenero.text.toString()
            val objetivo = spinnerObjetivo.text.toString()
            val actividad = spinnerActividad.text.toString()
            val velocidad = spinnerVelocidad.text.toString()
            val comidas = spinnerComidas.text.toString()
            val dieta = spinnerDieta.text.toString()
            val variedad = spinnerVariedad.text.toString()

            // Valida que los campos no esten vacios antes de procesar
            if (validateFields(edad, peso, altura, pObjetivo, genero, objetivo, actividad, velocidad)) {
                
                // Realiza el calculo de calorias basado en los datos ingresados
                val calorias = calcularCalorias(
                    genero, edad.toInt(), peso.toDouble(), altura.toDouble(), actividad, objetivo, velocidad
                )
                
                // Muestra el resultado en la interfaz
                tvResultado.text = "Calorias recomendadas: $calorias kcal"
                
                // Guarda la informacion completa en Firestore
                guardarEnFirestore(genero, edad, peso, altura, pObjetivo, objetivo, actividad, velocidad, comidas, dieta, variedad, calorias)
            }
        }
    }

    // Llena los selectores con las opciones predefinidas
    private fun setupSpinners(vararg spinners: AutoCompleteTextView) {
        val data = mapOf(
            0 to arrayOf("Masculino", "Femenino"),
            1 to arrayOf("Bajar peso", "Mantener peso", "Ganar masa muscular"),
            2 to arrayOf("Bajo", "Moderado", "Alto"),
            3 to arrayOf("Lento", "Medio", "Rápido"),
            4 to arrayOf("3", "4", "5"),
            5 to arrayOf("Normal", "Vegetariana", "Alta en proteínas", "Keto"),
            6 to arrayOf("Baja", "Media", "Alta")
        )
        spinners.forEachIndexed { index, spinner ->
            data[index]?.let {
                spinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, it))
            }
        }
    }

    // Verifica que ningun campo obligatorio este vacio
    private fun validateFields(vararg fields: String): Boolean {
        for (field in fields) {
            if (field.isEmpty()) {
                Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    // Implementacion de la formula Mifflin-St Jeor para determinar las calorias diarias
    private fun calcularCalorias(genero: String, edad: Int, peso: Double, altura: Double, actividad: String, objetivo: String, velocidad: String): Int {
        var tmb = (10 * peso) + (6.25 * (altura * 100)) - (5 * edad)
        tmb = if (genero == "Masculino") tmb + 5 else tmb - 161
        
        val factorActividad = when(actividad) {
            "Bajo" -> 1.2
            "Moderado" -> 1.55
            "Alto" -> 1.725
            else -> 1.2
        }
        
        val mantenimiento = (tmb * factorActividad).toInt()
        
        val ajusteBase = when(velocidad) {
            "Lento" -> 200
            "Medio" -> 300
            "Rápido" -> 500
            else -> 300
        }
        
        return when(objetivo) {
            "Bajar peso" -> mantenimiento - ajusteBase
            "Ganar masa muscular" -> mantenimiento + ajusteBase
            else -> mantenimiento
        }
    }

    // Almacena el perfil nutricional en la subcoleccion del usuario
    private fun guardarEnFirestore(genero: String, edad: String, peso: String, altura: String, pObjetivo: String, objetivo: String, actividad: String, velocidad: String, comidas: String, dieta: String, variedad: String, calorias: Int) {
        val user = auth.currentUser ?: return
        
        val perfil = hashMapOf(
            "genero" to genero,
            "edad" to edad.toInt(),
            "peso" to peso.toDouble(),
            "altura" to altura.toDouble(),
            "peso_objetivo" to pObjetivo.toDouble(),
            "objetivo" to objetivo,
            "actividad" to actividad,
            "velocidad" to velocidad,
            "comidas_al_dia" to comidas,
            "tipo_dieta" to dieta,
            "variedad" to variedad,
            "calorias_recomendadas" to calorias
        )

        db.collection("usuarios").document(user.uid)
            .collection("perfil_nutricional").document("datos")
            .set(perfil)
            .addOnSuccessListener {
                Toast.makeText(this, "Perfil y cálculo guardado", Toast.LENGTH_SHORT).show()
                // Navega al registro de comida tras guardar
                startActivity(Intent(this, RegistrarComidaActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
