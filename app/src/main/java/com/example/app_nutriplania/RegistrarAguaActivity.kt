package com.example.app_nutriplania

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class RegistrarAguaActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrar_agua)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val editAgua = findViewById<TextInputEditText>(R.id.editCantidadMl)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarAgua)

        btnGuardar.setOnClickListener {
            val cantidad = editAgua.text.toString()

            if (cantidad.isNotEmpty()) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val agua = hashMapOf(
                        "fecha" to Date(),
                        "cantidad_ml" to cantidad.toIntOrNull()
                    )

                    db.collection("usuarios").document(uid)
                        .collection("consumo_agua")
                        .add(agua)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Agua registrada", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Ingresa la cantidad", Toast.LENGTH_SHORT).show()
            }
        }
    }
}