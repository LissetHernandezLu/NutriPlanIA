package com.example.app_nutriplania

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Configuracion de la Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "NutriPlanIA"

        // Inicializacion del DrawerLayout y NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        // Configuracion del boton de hamburguesa para el menu lateral
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Actualizar el correo del usuario en el encabezado del menu
        actualizarHeader(navigationView)
    }

    // Gestiona las acciones al seleccionar un item del menu lateral
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_inicio -> {
                // Ya estamos en Inicio (MainActivity)
            }
            R.id.nav_registrar_comida -> {
                startActivity(Intent(this, RegistrarComidaActivity::class.java))
            }
            R.id.nav_resumen -> {
                startActivity(Intent(this, ResumenDiarioActivity::class.java))
            }
            R.id.nav_perfil -> {
                startActivity(Intent(this, PerfilNutricionalActivity::class.java))
            }
            R.id.nav_analizar -> {
                startActivity(Intent(this, AnalizarAlimentoActivity::class.java))
            }
            R.id.nav_logout -> {
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
        // Cierra el menu lateral tras la seleccion
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // Muestra el correo electronico del usuario autenticado en el menu
    private fun actualizarHeader(navigationView: NavigationView) {
        val headerView = navigationView.getHeaderView(0)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)
        tvUserEmail.text = auth.currentUser?.email ?: "Sin sesion"
    }

    // Maneja el cierre del menu con el boton atras fisico
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
