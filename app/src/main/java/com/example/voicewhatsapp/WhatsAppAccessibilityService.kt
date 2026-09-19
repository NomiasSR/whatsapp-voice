package com.example.voicewhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceWA"

        var instance: WhatsAppAccessibilityService? = null

        private const val ID_SEARCH_BAR = "com.whatsapp:id/search_bar_inner_layout"
        private const val ID_MESSAGE_ENTRY = "com.whatsapp:id/entry"
        private const val ID_SEND_BUTTON = "com.whatsapp:id/send"
        private const val ID_CONTACT_ROW_CONTAINER = "com.whatsapp:id/contact_row_container"
        private const val ID_MESSAGE_TEXT = "com.whatsapp:id/message_text"
        private const val ID_VOICE_NOTE_BUTTON = "com.whatsapp:id/voice_note_btn"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService conectado")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrompido")
    }

    fun openWhatsApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (launchIntent == null) {
            Log.e(TAG, "getLaunchIntentForPackage retornou NULL — o pacote com.whatsapp não foi encontrado neste celular.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launchIntent)
            Log.d(TAG, "startActivity chamado com sucesso para com.whatsapp")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar abrir o WhatsApp", e)
        }
    }

    fun openContact(nome: String): Boolean {
        if (buscarNaTelaAtual(nome)) {
            return true
        }

        if (abrirArquivadasEBuscar(nome)) {
            return true
        }

        val apenasDigitos = nome.replace(Regex("[^0-9]"), "")
        if (apenasDigitos.length >= 8 && apenasDigitos.length == nome.replace(" ", "").length) {
            abrirConversaPorNumero(apenasDigitos)
            return true
        }

        val numero = buscarNumeroNaAgenda(nome)
        if (numero != null) {
            Log.d(TAG, "Contato \"$nome\" encontrado na agenda: $numero")
            abrirConversaPorNumero(numero)
            return true
        }

        Log.w(TAG, "Não encontrei \"$nome\" na tela, em arquivadas, nem na agenda do celular")
        return false
    }

    private fun buscarNaTelaAtual(nome: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val encontrados = root.findAccessibilityNodeInfosByText(nome)
        if (encontrados.isEmpty()) return false

        var target: AccessibilityNodeInfo? = encontrados.first()
        while (target != null && target.viewIdResourceName != ID_CONTACT_ROW_CONTAINER) {
            target = target.parent
        }

        return if (target != null) {
            clickNode(target)
            Log.d(TAG, "Contato \"$nome\" encontrado na tela atual")
            true
        } else {
            false
        }
    }

    private fun abrirArquivadasEBuscar(nome: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val arquivadasNode = root.findAccessibilityNodeInfosByText("Arquivadas").firstOrNull()
            ?: return false

        var target: AccessibilityNodeInfo? = arquivadasNode
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        if (target == null) return false

        clickNode(target)
        Thread.sleep(500)

        val achou = buscarNaTelaAtual(nome)
        if (!achou) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return achou
    }

    private fun buscarNumeroNaAgenda(nome: String): String? {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permissão READ_CONTACTS não concedida — não é possível buscar na agenda.")
            return null
        }

        return try {
            val resolver = contentResolver
            val projecao = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selecao = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val argumentos = arrayOf("%$nome%")

            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projecao,
                selecao,
                argumentos,
                null
            )?.use { cursor ->
                Log.d(TAG, "Busca na agenda por \"$nome\" retornou ${cursor.count} resultado(s)")
                if (cursor.moveToFirst()) {
                    val nomeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numeroIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    Log.d(TAG, "Contato encontrado na agenda: ${cursor.getString(nomeIndex)}")
                    return cursor.getString(numeroIndex)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao consultar a agenda de contatos", e)
            null
        }
    }

    private fun abrirConversaPorNumero(numeroBruto: String) {
        val numeroLimpo = numeroBruto.replace(Regex("[^0-9]"), "")
        val numeroComPais = if (numeroLimpo.length in 10..11 && !numeroLimpo.startsWith("55")) {
            "55$numeroLimpo"
        } else {
            numeroLimpo
        }

        val uri = Uri.parse("https://wa.me/$numeroComPais")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir conversa por número", e)
        }
    }

    fun readLastMessage(callback: (String?) -> Unit) {
        val root = rootInActiveWindow
        if (root == null) {
            callback(null)
            return
        }

        val messageNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodesById(root, ID_MESSAGE_TEXT, messageNodes)

        if (messageNodes.isEmpty()) {
            callback(null)
            return
        }

        val ultimo = messageNodes.last()
        callback(ultimo.text?.toString())
    }

    fun typeMessage(texto: String) {
        val root = rootInActiveWindow ?: return
        val entryField = findNodeById(root, ID_MESSAGE_ENTRY)
        if (entryField != null) {
            setText(entryField, texto)
        } else {
            Log.w(TAG, "Campo de mensagem não encontrado")
        }
    }

    fun sendMessage() {
        val root = rootInActiveWindow ?: return
        val sendButton = findNodeById(root, ID_SEND_BUTTON)
        if (sendButton != null) {
            clickNode(sendButton)
        } else {
            Log.w(TAG, "Botão de enviar não encontrado")
        }
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun fecharWhatsApp() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun resolverNumeroParaLigar(nome: String): String? {
        val apenasDigitos = nome.replace(Regex("[^0-9]"), "")
        if (apenasDigitos.length >= 8 && apenasDigitos.length == nome.replace(" ", "").length) {
            return apenasDigitos
        }
        return buscarNumeroNaAgenda(nome)
    }

    fun ligarPara(numero: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$numero")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão CALL_PHONE não concedida", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar ligar", e)
        }
    }

    /**
     * Grava e envia um áudio (nota de voz) segurando o botão de nota de voz
     * do WhatsApp (com.whatsapp:id/voice_note_btn) pelo tempo indicado e
     * depois soltando — exatamente como um toque manual: segurar grava,
     * soltar sem arrastar envia automaticamente.
     */
    fun gravarEEnviarAudio(duracaoMs: Long, aoConcluir: () -> Unit) {
        val root = rootInActiveWindow
        val botao = root?.let { findNodeById(it, ID_VOICE_NOTE_BUTTON) }
        if (botao == null) {
            Log.w(TAG, "Botão de nota de voz não encontrado — não é possível gravar")
            aoConcluir()
            return
        }

        val bounds = Rect()
        botao.getBoundsInScreen(bounds)
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duracaoMs))
            .build()

        val disparado = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gravação de áudio concluída (${duracaoMs}ms)")
                aoConcluir()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesto de gravação foi cancelado pelo sistema")
                aoConcluir()
            }
        }, null)

        if (!disparado) {
            Log.e(TAG, "dispatchGesture retornou false — gesto não pôde ser iniciado")
            aoConcluir()
        }
    }

    private fun findNodeById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.firstOrNull()
    }

    private fun collectNodesById(
        root: AccessibilityNodeInfo,
        viewId: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        out.addAll(root.findAccessibilityNodeInfosByViewId(viewId))
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            var parent = node.parent
            while (parent != null && !parent.isClickable) {
                parent = parent.parent
            }
            parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun setText(node: AccessibilityNodeInfo, texto: String) {
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            texto
        )
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun dumpVisibleNodes() {
        val root = rootInActiveWindow ?: return
        dumpNode(root, 0)
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        Log.d(TAG, "$indent id=${node.viewIdResourceName} text=${node.text} desc=${node.contentDescription}")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, depth + 1) }
        }
    }
}