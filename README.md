# Voice WhatsApp — controle do WhatsApp por voz (projeto do zero)

App Android nativo, 100% gratuito (sem Tasker, sem libs pagas), usando só APIs
do próprio Android:

- `SpeechRecognizer` — reconhecimento de voz
- `TextToSpeech` — fala em voz alta
- `AccessibilityService` — "enxerga" e interage com a tela do WhatsApp

## Como abrir o projeto

1. Instale o **Android Studio** (gratuito): https://developer.android.com/studio
2. Abra a pasta `whatsapp-voice/` como projeto existente ("Open" no Android Studio).
3. Deixe o Gradle sincronizar (pode demorar alguns minutos na primeira vez).
4. Conecte um celular Android por USB com "Depuração USB" ativada, ou use um emulador.
5. Rode o app (▶️ no Android Studio).

## Como usar

1. Abra o app "Voice WhatsApp" e toque em **"Conceder permissão de microfone"**.
2. Toque em **"Abrir configurações de Acessibilidade"** e ative o serviço
   "Voice WhatsApp" na lista (isso é obrigatório — sem essa permissão o app
   não consegue interagir com a tela do WhatsApp).
3. Volte ao app e toque em **"Iniciar escuta de comandos"**.
4. A partir daí, os comandos reconhecidos são:
   - **"abrir whatsapp"**
   - **"abrir contato [nome]"**
   - **"falar última mensagem"**
   - **"mandar mensagem [nome]"** → depois disso, a próxima fala inteira vira
     o texto da mensagem (é ditado livre, não um comando fixo)
   - **"enviar mensagem"**

## Limitações e pontos frágeis (leia antes de usar no dia a dia)

- **Os `viewId` do WhatsApp podem mudar em atualizações.** O código usa IDs
  como `com.whatsapp:id/entry` e `com.whatsapp:id/send`, observados em
  versões recentes, mas o WhatsApp pode alterá-los. Se algo parar de
  funcionar, use `WhatsAppAccessibilityService.dumpVisibleNodes()` (loga a
  árvore de elementos da tela atual no Logcat) para achar os novos IDs.
- **Escuta contínua em segundo plano gasta bateria** e alguns fabricantes
  (Samsung, Xiaomi, etc.) matam serviços em background agressivamente — pode
  ser necessário liberar o app das otimizações de bateria manualmente.
- **`Thread.sleep()` como espera entre telas é frágil.** Se o celular estiver
  lento, o tempo fixo (400ms, 600ms) pode não ser suficiente e a automação
  falha. Uma versão mais robusta esperaria por eventos de mudança de janela
  (`TYPE_WINDOW_STATE_CHANGED`) em vez de um tempo fixo — fica como próxima
  melhoria.
- **Reconhecimento de voz "sempre ouvindo" tem custo de precisão**: como não
  há uma palavra de ativação (wake word) dedicada, qualquer fala próxima do
  celular pode ser interpretada como comando. Para reduzir isso, os comandos
  atuais exigem frases específicas (`"abrir whatsapp"`, `"mandar mensagem"`),
  mas ainda pode haver ativações indesejadas.
- **Esse tipo de automação pode ir contra os Termos de Uso do WhatsApp**
  (que geralmente proíbem automação não oficial da interface). Use por sua
  conta e risco, preferencialmente para fins pessoais/acessibilidade.

## Próximos passos possíveis

- Trocar `Thread.sleep()` por escuta de eventos de acessibilidade reais.
- Adicionar uma wake word (ex: usando o próprio `SpeechRecognizer` em loop
  curto, checando se a frase começa com uma palavra-chave antes de agir).
- Persistir o último contato usado, para comandos mais curtos tipo apenas
  "mandar mensagem" sem repetir o nome toda vez.
