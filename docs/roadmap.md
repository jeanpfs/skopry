# Roadmap

O que o Skopry tem e o que vai ganhar, em linguagem simples. Várias dessas ideias nasceram no
projeto original (Sill, de Noah Saffer) e entram aqui adaptadas ao Skopry e ao app Android.

## Planejado

### Som do Mac no dispositivo
O som do que está tocando no Mac passa a sair no celular ou tablet, junto com a imagem da janela.
Quem escolhe é você: o Mac tem uma chave **Enviar Áudio**, e o dispositivo tem um botão **Mudo**.
No original o som ainda é um recurso em teste ("beta").
No Android é a maior das três: precisa de um tocador de áudio novo no app.

### Inverter a rolagem (Reverse Scrolling)
Uma chave nas configurações que vira ao contrário o sentido em que a rolagem acontece quando você
arrasta o dedo no trackpad da tela. Serve a quem prefere a rolagem "clássica" em vez da "natural",
e vale também para a rolagem com dois dedos do trackpad na tela.

### Capturar na escala real do display
O Mac passa a capturar a janela na resolução em que ela realmente aparece no seu monitor, nunca
acima disso. O resultado é uma imagem mais fiel e sem esforço inútil, e janelas com tamanho ímpar
não ficam mais com a borda cortada. Mexe na parte do Mac que mais pesa na nitidez, então entra
depois de medir contra o que já temos.

### Alcançar o Mac de qualquer lugar, sem depender do Tailscale
Hoje, fora de casa, o Skopry precisa do Tailscale ligado no Mac e no celular. Esta ideia é uma
conexão direta entre os dois, com chaves que só você tem, e sem servidor de ninguém no meio. É a
mais ambiciosa da lista e a que mais mexe na segurança, então vem por último e com cuidado.
(Original, PR #195, ainda não aceito lá.)

## Já no Skopry

### Trazido do projeto original
- **Pedir um novo quadro-chave ao Mac.** Se a imagem do celular trava ou é refeita, o app pede ao
  Mac uma imagem nova na hora, em vez de esperar a janela mudar.
- **Acordar a tela do Mac.** Se a tela do Mac está apagada, conectar e usar pelo celular a acorda, em
  vez de mostrar imagem preta. O primeiro toque que acorda a tela não clica em nada por engano, e fora
  de casa o Mac entende que alguém está usando à distância. A tela volta a apagar sozinha pela
  configuração do macOS.
- **Fechar janela sem travar tudo.** Se um app demora a responder ao "Fechar", o Skopry espera no
  máximo 1 segundo e avisa, sem prender o controle dos outros dispositivos.
- **Seguir a janela nova que o app abre.** Quando você está vendo uma janela e o app abre outra na
  frente (um diálogo "Abrir…", uma folha de salvar), o stream passa para ela sozinho e volta à janela
  de antes quando ela fecha.
- **Sem janelas fantasma na lista.** Processos do sistema que apareciam como cópias vazias, sem
  título, somem da lista de janelas.
- **Imagem mais estável no Mac.** O codificador de vídeo para de trocar de sessão sem parar quando
  fica lento, e a taxa de quadros não passa do que o hardware do Mac aguenta no tamanho da janela.
- **Skopry.app para Macs Intel e Apple silicon.** Um único app com as duas versões dentro, gerado
  com `SKOPRY_UNIVERSAL=1 Scripts/make-app.sh`. O build para Intel está feito; ele ainda não foi
  rodado num Mac Intel.

### Feito no Skopry
- **Foto da tela (Android).** Nas Configurações, "Save Picture" salva o que está sendo mostrado na
  pasta Pictures/Skopry, e "Share…" abre o compartilhamento do sistema. A foto só tem a imagem do
  Mac, sem os botões do app.
- **Velocidade do trackpad (Android).** Cinco níveis, do mais lento ao mais rápido, para escolher o
  quanto o ponteiro anda a cada arrastada do dedo.
- Ícone, nome, identidade visual e design do app Android (violeta, coral e abismo, fonte Plus
  Jakarta Sans).
- Android: texto maior por app, teclado acima do IME, volta do segundo plano em 0,2 s, apagar
  mantendo pressionado, nitidez de ampliação opcional, app favorito.
