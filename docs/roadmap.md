# Roadmap

O que o Skopry tem e o que vai ganhar, em linguagem simples. Várias dessas ideias nasceram no
projeto original (Sill, de Noah Saffer) e entram aqui adaptadas ao Skopry e ao app Android.

## Planejado

### Alcançar o Mac de qualquer lugar, sem depender do Tailscale
Hoje, fora de casa, o Skopry precisa do Tailscale ligado no Mac e no celular. Esta ideia é uma
conexão direta entre os dois, com chaves que só você tem, e sem servidor de ninguém no meio. É a
mais ambiciosa da lista e a que mais mexe na segurança, então vem por último e com cuidado.
(Original, PR #195, ainda não aceito lá.)

## Já no Skopry

### Feito agora
- **Som do Mac no Android.** O som do que está tocando no Mac (o do app da janela que você vê,
  ou o de todo o Mac na mesa de trabalho) sai no celular junto com a imagem. O som não é gravado: é
  captado pelo macOS e enviado direto. O Mac segue tocando também. Nas Configurações do Mac ("Send
  Audio") e nas do Android ("Sound › Send Audio") há a chave, desligada de fábrica, e o Android
  tem o botão **Mute**, só dele. O iPhone e o iPad ainda não tocam o som.
- **Inverter a rolagem (Android).** Uma chave nas Configurações vira ao contrário o sentido da rolagem
  feita com o dedo, na tela e no trackpad, e também a rolagem que continua depois que você solta. A
  roda de um mouse não muda.
- **Capturar na escala real do display.** O Mac captura a janela na resolução em que ela de fato
  aparece no monitor, nunca acima disso. Com um monitor comum (não Retina), uma janela não fica mais
  num canto pequeno da imagem, a mesa de trabalho não é capturada com o quádruplo de pixels, e
  janelas com tamanho ímpar não ficam borradas. O ponteiro também passa a chegar até o último pixel
  da borda (a Dock escondida aparece). Numa tela Retina a imagem fica como antes.

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
