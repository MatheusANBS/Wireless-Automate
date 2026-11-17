---
name: wa-curseforge-imagens
description: Grava e monta os WebPs animados da página do Wireless Automate no CurseForge (roteador, filtro, baú, doca, tela e os quatro da Armilar), gravados no jogo com o Complementary pela vitrine WA_VITRINE (client/dev/vitrine) e montados com o ffmpeg pelo gif.py, e adapta a descricao.md. Use quando o dono falar em "imagens do CurseForge", "GIFs da descrição", "vitrine", "media do CurseForge", "refaz as animações", "fotos do mod", ou mandar links para pôr na descrição.
---

# Animações do CurseForge

A página tem poucas mídias, quase todas animações do mod em ação, gravadas no jogo de verdade com o shaderpack, sem cartões nem faixas por cima. Cada uma é uma cena curta (`client/dev/vitrine/`, uma classe `Cena*` por arquivo), e os arquivos ficam em `docs/curseforge/<cena>.webp` (no repositório; a descrição aponta para o GitHub). É WebP animado e não GIF porque o GIF só tem 256 cores por quadro e listra o céu e o brilho do shader.

## Gravar

```bash
./scripts/e2e.sh --vitrine todas          # ou uma cena: roteador, filtro, bau, doca, tela, armilar (os quatro da Armilar)
```

- Abre o jogo com o Complementary (`run/config/iris.properties`, sem tocar no `run/options.txt`), um mundo plano ao fim da tarde e uma praça por cena (80 blocos de uma para a outra); escreve `run/vitrine/<cena>/NNNN.png` (~30 q/s, no tempo real do jogo), o `quadros.csv` (instante, cursor e idade do clique de cada quadro) e `run/vitrine/chao-<cena>.png` (a praça vista de cima, para conferir antes de gravar).
- Um jogo por vez; não mexa no mouse nem no teclado. A pixel art e o ícone da cena da Tela vêm do `scripts/curseforge/arte_vitrine.py`, que o `e2e.sh --vitrine` roda sozinho.

## Montar

```bash
python -I scripts/curseforge/gif.py run/vitrine --folha <scratchpad>/vitrine-gifs.png   # todas; ou os nomes das cenas
```

O `gif.py` reamostra a 30 q/s pelo instante, desenha o cursor e o realce do clique por cima e grava o WebP com o ffmpeg (`libwebp_anim`, qualidade 95), no tamanho do quadro. A folha mostra seis quadros de cada animação e, embaixo, o quadro do meio em 100% ao lado do PNG original, mais as fotos do chão de cima: olhe antes de aceitar (câmera fora de bloco, telas legíveis, cursor visível, shader ligado, nada cortado, praça simétrica e inteira).

## Regras que valem

- A câmera (`Camera`) é o jogador voando, conduzido por uma curva do relógio a cada quadro, só no cliente; nada de teleporte do servidor em passos. Os laços balançam num seno inteiro (ou dão uma volta inteira) para emendar.
- Chão: `Palco.praca` (simétrico em torno do bloco do meio, sem buracos), grande o bastante para a borda não entrar no quadro.
- Uma cena nova é uma classe `Cena*` no pacote, registrada no `Vitrine`, com o nome na lista `CENAS` do `gif.py`.
- Os textos da descrição ficam em inglês. Números só do código, nunca inventados. Commit e push só quando o dono pedir, e isso não é versão nova (ver `wa-release`).
