# Usability test script (primary user)

A 20-minute session to run at home with the primary user after each release
to their phone. The goal is to find where the app makes them hesitate, not to
test the person. Record findings in `docs/design/usability-findings.md`
(dated, no names or personal details).

## Setup

- Their own phone with the new version installed and one book they want to
  hear, sent to them through their usual messaging app (do not import it
  beforehand).
- A quiet room, a timer, and a notepad. Sit beside them, not in front.
- Say: *"We are testing the app, not you. If something is confusing, that is
  the app's fault. Please say out loud what you are thinking."*
- Do not help unless they are stuck for more than a minute or ask twice; note
  every hint you give.

## Tasks

Read each task aloud in Portuguese exactly as written. Note the time,
hesitations (pauses over 5 seconds), wrong taps, hints and comments.

| # | Task (say this) | Done when |
|---|-----------------|-----------|
| 1 | "Te mandaram um livro pelo WhatsApp. Coloque esse livro no R Reader." | The book appears in the library |
| 2 | "Comece a ouvir esse livro." | Audio is playing |
| 3 | "Você se distraiu. Volte um pouquinho para ouvir de novo." | Used −15 s (or the slider) |
| 4 | "Bloqueie a tela. Agora pause a leitura sem desbloquear o celular." | Paused from the lock screen |
| 5 | "Feche o aplicativo. Agora abra de novo e continue de onde parou." | Resumed at the same sentence |
| 6 | "Escolha outra voz de que você goste mais." | A different default voice |
| 7 | "Deixe a voz um pouco mais devagar." | Speed below 1.0× |
| 8 | "Vá para o próximo capítulo." | Next chapter playing |

Phase 3 and later add: follow the text on screen, set a sleep timer, open a
scanned PDF, download a free classic.

## After the tasks

Ask, and write down the answers in their words:

1. "De 1 a 5, quão fácil foi usar?" (show five faces from sad to happy)
2. "O que você mais gostou?"
3. "O que mais te atrapalhou ou irritou?"
4. "A voz está agradável? A letra está num tamanho bom?"
5. "Você usaria isso para ouvir um livro inteiro?"

## Recording findings

For each problem: the task number, what happened, how severe it was
(blocked / slowed down / cosmetic), and a proposed fix. Turn every
"blocked" or "slowed down" finding into a roadmap item before the next
release.
