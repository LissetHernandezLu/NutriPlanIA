package com.example.app_nutriplania

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class RegistrarActividadActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrar_actividad)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val editPasos = findViewById<TextInputEditText>(R.id.editPasos)
        val editCalorias = findViewById<TextInputEditText>(R.id.editCaloriasQuemadas)
        val editTipo = findViewById<TextInputEditText>(R.id.editTipoActividad)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarActividad)

        btnGuardar.setOnClickListener {
            val pasos = editPasos.text.toString()
            val cal = editCalorias.text.toString()
            val tipo = editTipo.text.toString()

            if (pasos.isNotEmpty() && cal.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val actividad = hashMapOf(
                        "fecha" to Date(),
                        "pasos" to pasos.toIntOrNull(),
                        "calorias_quemadas" to cal.toIntOrNull(),
                        "tipo_actividad" to tipo
                    )

                    db.collection("usuarios").document(uid)
                        .collection("actividad_fisica")
                        .add(actividad)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Actividad registrada", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}