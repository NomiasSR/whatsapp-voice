package com.example.voicewhatsapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val status = TextView(this).apply {
            text = "1) Conceda a permissão de microfone.\n" +
                    "2) Ative o serviço de Acessibilidade do app.\n" +
                    "3) Toque em Iniciar escuta."
            textSize = 16f
        }

        val btnPermissao = Button(this).apply {
            text = "Conceder permissão de microfone"
            setOnClickListener { pedirPermissaoMicrofone() }
        }

        val btnAcessibilidade = Button(this).apply {
            text = "Abrir configurações de Acessibilidade"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnIniciar = Button(this).apply {
            text = "Iniciar escuta de comandos"
            setOnClickListener { iniciarServicoDeEscuta() }
        }

        root.addView(status)
        root.addView(btnPermissao)
        root.addView(btnAcessibilidade)
        root.addView(btnIniciar)
        setContentView(root)
    }

    private fun pedirPermissaoMicrofone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 1003)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1004)
        }
    }

    private fun iniciarServicoDeEscuta() {
        val intent = Intent(this, VoiceListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}