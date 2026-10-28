# Roadmap

O que o Skopry vai ganhar, em linguagem simples. Várias dessas ideias nasceram no projeto
original (Sill, de Noah Saffer) e entram aqui adaptadas ao Skopry e ao app Android.

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

### Seguir a janela nova que o app abre
Quando você está vendo uma janela e o app abre outra na frente (um diálogo "Abrir…", uma folha de
salvar), o stream passa para ela sozinho e volta à janela de antes quando ela fecha. Hoje a janela
nova fica escondida e parece que o app travou. (Ideia do original, PR #231.)

### Acordar a tela do Mac, melhor
Duas melhorias no que já trouxemos: o primeiro toque que acorda a tela não clica em nada por
engano, e fora de casa o Mac entende que alguém está usando à distância. (Original, PRs #228 e #229.)

### Sem janelas fantasma na lista
Alguns processos do sistema aparecem na lista de janelas como cópias vazias, sem título. Elas somem
da lista. (Original, PR #223.)

### Imagem mais estável no Mac
O codificador de vídeo para de trocar de sessão sem parar quando fica lento, e a taxa de quadros
não passa do que o hardware do Mac aguenta no tamanho da janela. A imagem fica mais constante,
principalmente em janelas grandes. (Original, PRs #71 e #122.)

### Skopry.app para Macs Intel e Apple silicon
Um único download que serve aos dois tipos de Mac, com a correção que faltava para o display
virtual funcionar nos Intel. (Original, PRs #88 e #87.)

### Foto da tela (Android)
Um botão para tirar uma foto do que está sendo mostrado e salvá-la ou compartilhá-la.

### Velocidade do trackpad (Android)
Uma configuração com cinco níveis, do mais lento ao mais rápido, para escolher o quanto o ponteiro
anda a cada arrastada do dedo.

### Alcançar o Mac de qualquer lugar, sem depender do Tailscale
Hoje, fora de casa, o Skopry precisa do Tailscale ligado no Mac e no celular. Esta ideia é uma
conexão direta entre os dois, com chaves que só você tem, e sem servidor de ninguém no meio. É a
mais ambiciosa da lista e a que mais mexe na segurança, então vem por último e com cuidado.
(Original, PR #195, ainda não aceito lá.)

## Já trazido do projeto original

- **Pedir um novo quadro-chave ao Mac.** Se a imagem do celular trava ou é refeita, o app pede ao
  Mac uma imagem nova na hora, em vez de esperar a janela mudar.
- **Acordar a tela do Mac.** Se a tela do Mac está apagada, conectar e usar pelo celular a acorda, em
  vez de mostrar imagem preta. Ela volta a apagar sozinha pela configuração do macOS.
- **Fechar janela sem travar tudo.** Se um app demora a responder ao "Fechar", o Skopry espera no
  máximo 1 segundo e avisa, sem prender o controle dos outros dispositivos.

## Feito no Skopry

- Ícone, nome, identidade visual e design do app Android (violeta, coral e abismo, fonte Plus
  Jakarta Sans).
- Android: texto maior por app, teclado acima do IME, volta do segundo plano em 0,2 s,
  apagar mantendo pressionado, nitidez de ampliação opcional, app favorito.
