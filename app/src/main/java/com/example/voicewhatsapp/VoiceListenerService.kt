package com.example.voicewhatsapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceListenerService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceWA"
        private const val CHANNEL_ID = "voice_whatsapp_channel"
        private const val NOTIF_ID = 1
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())
    private var escutaPausada = false

    private enum class Estado { IDLE, AGUARDANDO_DITADO, AGUARDANDO_NOME_CONTATO, AGUARDANDO_ACAO_CONTATO }
    private var estado = Estado.IDLE
    private var contatoPendente: String? = null
    private var numeroPendente: String? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        criarCanalNotificacao()
        startForeground(NOTIF_ID, construirNotificacao("Ouvindo comandos..."))
        iniciarEscuta()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    private fun iniciarEscuta() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Reconhecimento de voz não disponível neste dispositivo")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val texto = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.lowercase(Locale.getDefault())

                    if (texto != null) {
                        Log.d(TAG, "Reconhecido: $texto")
                        processarComando(texto)
                    }
                    handler.postDelayed({ if (!escutaPausada) iniciarEscuta() }, 300)
                }

                override fun onError(error: Int) {
                    handler.postDelayed({ if (!escutaPausada) iniciarEscuta() }, 300)
                }

                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            startListening(intent)
        }
    }

    private fun processarComando(fala: String) {
        val service = WhatsAppAccessibilityService.instance

        if (service == null) {
            Log.w(TAG, "AccessibilityService ainda não está ativo (ative em Configurações > Acessibilidade)")
            return
        }

        when {
            estado == Estado.AGUARDANDO_DITADO -> {
                service.typeMessage(fala)
                estado = Estado.IDLE
                falar("Mensagem digitada. Diga enviar mensagem para confirmar.")
            }

            estado == Estado.AGUARDANDO_NOME_CONTATO -> {
                estado = Estado.IDLE
                falar("Procurando contato $fala")
                Thread {
                    try {
                        val encontrado = service.openContact(fala)
                        val numero = service.resolverNumeroParaLigar(fala)
                        handler.post {
                            if (encontrado) {
                                contatoPendente = fala
                                numeroPendente = numero
                                estado = Estado.AGUARDANDO_ACAO_CONTATO
                                falar("Contato encontrado. Você deseja: ligar, mandar texto ou mandar áudio?")
                            } else {
                                falar("Contato não localizado")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao buscar contato \"$fala\"", e)
                        handler.post { falar("Erro ao procurar o contato") }
                    }
                }.start()
            }

            // Depois de encontrar o contato, aguarda a decisão: ligar, texto ou áudio.
            estado == Estado.AGUARDANDO_ACAO_CONTATO -> {
                when {
                    fala.contains("ligar") -> {
                        estado = Estado.IDLE
                        val numero = numeroPendente
                        if (numero != null) {
                            service.ligarPara(numero)
                            falar("Ligando para $contatoPendente")
                        } else {
                            falar("Não tenho o número desse contato para ligar")
                        }
                    }
                    fala.contains("áudio") || fala.contains("audio") -> {
                        estado = Estado.IDLE
                        gravarEEnviarAudio(service)
                    }
                    fala.contains("texto") || fala.contains("mensagem") -> {
                        estado = Estado.AGUARDANDO_DITADO
                        falar("Pode ditar a mensagem para $contatoPendente")
                    }
                    else -> {
                        falar("Diga ligar, mandar texto ou mandar áudio")
                    }
                }
            }

            fala.contains("procurar contato") || fala.contains("procura contato") -> {
                estado = Estado.AGUARDANDO_NOME_CONTATO
                falar("Pode falar o nome do contato")
            }

            fala.contains("whatsapp") && (fala.contains("abrir") || fala.contains("abre") || fala.contains("abrindo")) && !fala.contains("contato") -> {
                service.openWhatsApp()
                falar("Abrindo WhatsApp")
            }

            fala.contains("contato") && (fala.contains("abrir") || fala.contains("abre") || fala.contains("abrindo")) -> {
                val nome = fala.substringAfter("contato").trim()
                falar("Abrindo contato $nome")
                Thread {
                    Thread.sleep(500)
                    service.openContact(nome)
                }.start()
            }

            fala.contains("última mensagem") || fala.contains("ultima mensagem") -> {
                service.readLastMessage { texto ->
                    if (texto != null) falar(texto) else falar("Não encontrei nenhuma mensagem")
                }
            }

            fala.contains("voltar") -> {
                service.goBack()
                falar("Voltando")
            }

            fala.contains("fechar") && fala.contains("whatsapp") -> {
                service.fecharWhatsApp()
                falar("Fechando WhatsApp")
            }

            fala.contains("depurar") -> {
                service.dumpVisibleNodes()
                falar("Tela registrada no log")
            }

            fala.contains("mandar mensagem") || fala.contains("manda mensagem") -> {
                val nome = fala.substringAfter("mensagem").trim()
                contatoPendente = nome
                estado = Estado.AGUARDANDO_DITADO
                falar("Pode ditar a mensagem para $nome")
            }

            fala.contains("enviar mensagem") || fala.contains("envia mensagem") || fala.contains("enviar") -> {
                service.sendMessage()
                falar("Mensagem enviada")
            }

            fala.contains("gravar") && (fala.contains("áudio") || fala.contains("audio")) -> {
                gravarEEnviarAudio(service)
            }

            else -> {
                Log.d(TAG, "Comando não reconhecido: $fala")
            }
        }
    }

    private fun falar(texto: String) {
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun falarEDepois(texto: String, aoTerminar: () -> Unit) {
        val utteranceId = "utt_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { aoTerminar() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { aoTerminar() }
            }
        })
        val params = android.os.Bundle()
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun gravarEEnviarAudio(service: WhatsAppAccessibilityService, duracaoSegundos: Int = 6) {
        escutaPausada = true
        speechRecognizer?.stopListening()
        falarEDepois("Fale sua mensagem agora, você tem $duracaoSegundos segundos") {
            service.gravarEEnviarAudio(duracaoSegundos * 1000L) {
                handler.post {
                    escutaPausada = false
                    falar("Áudio enviado")
                    iniciarEscuta()
                }
            }
        }
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice WhatsApp",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun construirNotificacao(texto: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice WhatsApp ativo")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}