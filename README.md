# Voice WhatsApp — controle do WhatsApp por voz (projeto do zero)

App Android nativo, 100% gratuito (sem Tasker, sem libs pagas), usando só APIs
do próprio Android:

- `SpeechRecognizer` — reconhecimento de voz contínuo
- `TextToSpeech` — fala em voz alta
- `AccessibilityService` — "enxerga" e interage com a tela do WhatsApp (clique, digitação e gestos de toque prolongado)
- `ContactsContract` — busca contatos na agenda do celular
- `Intent.ACTION_CALL` — faz ligações telefônicas de verdade

## Como abrir o projeto

1. Instale o **Android Studio** (gratuito): https://developer.android.com/studio
2. Abra a pasta `whatsapp-voice/` como projeto existente ("Open" no Android Studio).
3. Deixe o Gradle sincronizar (pode demorar alguns minutos na primeira vez).
4. Conecte um celular Android por USB com "Depuração USB" ativada, ou use um emulador.
5. Rode o app (▶️ no Android Studio).

## Como usar

1. Abra o app "Voice WhatsApp" e toque em **"Conceder permissão de microfone"** (isso também pede permissão de Contatos, de Ligação e de Notificações, tudo de uma vez).
2. Toque em **"Abrir configurações de Acessibilidade"** e ative o serviço "Voice WhatsApp" na lista.
   > **Importante:** toda vez que o app é reinstalado (cada "Run" no Android Studio), o Android desativa esse serviço automaticamente por segurança — é preciso reativar manualmente depois de cada reinstalação.
3. Volte ao app e toque em **"Iniciar escuta de comandos"**.

## Comandos de voz disponíveis

| Comando | O que faz |
|---|---|
| "abrir whatsapp" (ou "abre", "abrindo") | Abre o app WhatsApp |
| "abrir contato [nome]" | Busca e abre a conversa com o contato |
| "procurar contato" | Espera você falar o nome em uma segunda fala, procura, e pergunta o que fazer (ver fluxo abaixo) |
| "falar última mensagem" | Lê em voz alta a última mensagem da conversa aberta |
| "mandar mensagem [nome]" | Prepara o campo de texto para o contato; a próxima fala vira o conteúdo da mensagem |
| "enviar mensagem" | Envia o texto já digitado no campo |
| "gravar áudio" | Grava uma nota de voz (6 segundos, ajustável) e envia automaticamente |
| "voltar" | Simula o botão voltar do sistema |
| "fechar whatsapp" | Vai para a tela inicial do Android (esconde o WhatsApp) |
| "depurar" | Loga no Logcat a árvore de elementos da tela atual (ajuda a descobrir IDs do WhatsApp) |

### Fluxo de "procurar contato"

1. Você diz **"procurar contato"** → o sistema responde "Pode falar o nome do contato".
2. Você fala o **nome completo** em uma única frase → o sistema procura (tela atual, Arquivadas, e por fim a agenda do Android) e diz:
   - "Contato encontrado. Você deseja: ligar, mandar texto ou mandar áudio?"
   - ou "Contato não localizado", se não achar em nenhum lugar.
3. Você responde com uma das três opções:
   - **"ligar"** → faz uma ligação telefônica de verdade (exige que o número tenha sido resolvido via agenda ou ditado como número puro).
   - **"texto"** ou **"mensagem"** → abre o modo de ditado; a próxima fala vira o texto, e depois é preciso dizer "enviar mensagem" para confirmar.
   - **"áudio"** → grava e envia uma nota de voz automaticamente (mesmo fluxo do comando "gravar áudio").

## Como a busca de contato funciona (por trás dos panos)

`openContact()` tenta, nessa ordem:
1. **Tela atual** — procura o nome na lista de conversas já visível (mais rápido).
2. **Arquivadas** — se não achar, abre a seção "Arquivadas" (se existir) e procura lá.
3. **Número puro** — se o que foi falado já é só dígitos (8+ números), usa direto como telefone.
4. **Agenda do Android** — busca o nome nos Contatos salvos no celular e usa o número encontrado para abrir a conversa via link `wa.me`, que funciona mesmo sem conversa anterior.

Se nenhum desses níveis encontrar, o comando falha e o app avisa por voz.

## IDs reais do WhatsApp confirmados via `dumpVisibleNodes()`

Estes foram confirmados rodando o comando "depurar" na versão instalada durante o desenvolvimento — podem mudar em atualizações futuras do WhatsApp:

- `com.whatsapp:id/contact_row_container` — linha clicável de cada conversa na lista principal
- `com.whatsapp:id/entry` — campo de digitação de mensagem
- `com.whatsapp:id/send` — botão de enviar texto (só aparece com texto digitado)
- `com.whatsapp:id/message_text` — texto de cada mensagem na conversa
- `com.whatsapp:id/voice_note_btn` — botão de nota de voz (sempre presente, separado do botão de enviar)

## Limitações e pontos frágeis (leia antes de usar no dia a dia)

- **Os `viewId` do WhatsApp podem mudar em atualizações.** Se algo parar de funcionar, fale "depurar" na tela relevante e use o Logcat (filtro `VoiceWA`) para achar os novos IDs.
- **Gravação de áudio tem duração fixa** (6 segundos por padrão, ajustável na constante `duracaoSegundos` em `VoiceListenerService.kt`) — não há como "falar até terminar" livremente, porque o reconhecimento de voz do próprio app precisa ficar pausado enquanto o WhatsApp grava (os dois disputam o microfone).
- **Ligar por voz só funciona se o número for resolvível**: precisa estar salvo na agenda do Android (não só como conversa no WhatsApp) ou ter sido ditado como número puro.
- **Escuta contínua em segundo plano gasta bateria** e alguns fabricantes (Samsung, Xiaomi, Motorola) podem hibernar o app agressivamente — libere o app da otimização de bateria e desative "remover permissões se o app não for usado" nas configurações do celular.
- **Sem wake word** (não existe uma palavra de ativação tipo "Ok Google") — qualquer fala próxima do celular é interpretada como possível comando.
- **Esse tipo de automação pode ir contra os Termos de Uso do WhatsApp** (que restringem automação não-oficial da interface). Projeto pessoal/educacional — use por sua conta e risco.

## Permissões usadas

| Permissão | Para quê |
|---|---|
| `RECORD_AUDIO` | Reconhecimento de voz |
| `READ_CONTACTS` | Buscar números de contatos salvos na agenda |
| `CALL_PHONE` | Fazer ligações diretamente |
| `POST_NOTIFICATIONS` | Notificação obrigatória do serviço em primeiro plano (Android 13+) |
| Acessibilidade (`BIND_ACCESSIBILITY_SERVICE`) | Ler a tela do WhatsApp e simular toques/gestos |

## Próximos passos possíveis

- Trocar as pequenas esperas fixas (`Thread.sleep`) por escuta real de eventos de mudança de tela.
- Adicionar uma wake word própria.
- Permitir escolher a duração da gravação de áudio por voz (ex: "gravar áudio de dez segundos").
- Persistir o último contato usado, para comandos mais curtos.
- Detectar automaticamente novos `viewId` quando os atuais pararem de funcionar (fallback mais inteligente).