---
name: wa-curseforge-imagens
description: Gera e atualiza as imagens da página do Wireless Automate no CurseForge - banner, imagem geral e imagens de função a partir de capturas reais do jogo (vitrine WA_SHOWCASE numa casa de quartzo), com faixa de título pelo gerar_imagens.py - e adapta a descricao.md com marcadores até o dono subir os arquivos e mandar os links. Use quando o dono falar em "imagens do CurseForge", "prints da descrição", "telas do description", "banner", "media do CurseForge", "refaz a vitrine", "fotos do mod", ou mandar uma lista de links media.forgecdn.net para pôr na descrição.
---

# Imagens do CurseForge

O dono não gosta de imagens montadas a partir de capturas soltas do e2e ("ficou meio feio"). As imagens boas saem de um cenário de verdade, construído e fotografado pelo próprio jogo, sempre no mesmo estilo. O resultado aprovado foi:
- uma casa de quartzo sem teto, de dia;
- dentro dela, as alas do mod;
- uma imagem geral com tudo, seguida das imagens de detalhe.

## Peças

- `client/DevEndToEnd.java`, seção "Vitrine (WA_SHOWCASE)":
  - `showcaseHouse`: piso em xadrez de quartzo liso e calcita com borda de ardósia, paredes de tijolo de quartzo com colunas a cada 4 blocos e janelas, a parede sul baixa para a câmera ver por cima, lanternas, tapete ciano, plantas, cerejeiras, cerca viva, flores e caminho com postes.
  - `showcaseSmelting`: fornalhas acesas com carvão, alimentadas pelo baú de entrada.
  - `showcaseStorage`: os cinco armazenamentos, cada um mandando para um cofre sob o piso, para o Tablet mostrar vazão de verdade.
  - `showcaseDisplay`: os itens em `item_display` com `billboard:"center"` e luz cheia, sobre pedestais.
  - `showcaseArcane`: o laboratório do Ars, com jarra criativa, Tanque, Apparatus e os quatro tiers do Tanque de Source.
  - `showcaseTiers`: o salão dos tiers no fundo da casa, com os oito roteadores sobre o bloco do material de cada tier e o cartão flutuando em cima (tapete laranja sob os do Allthemodium; sem o mod, eles ficam de fora). Os tiers do ATM nas outras alas passam pelo `atm(tier, substituto)`.
  - `showcase()`: o roteiro de câmeras e telas. `showLook(nome, câmera xyz, alvo xyz, interface)` calcula o ângulo a partir da base.
- `scripts/curseforge/gerar_imagens.py`:
  - a lista `DESTAQUES`, com arquivo, título, subtítulo, captura, recorte e um detalhe opcional no canto;
  - `tiers()`, a tabela gerada a partir das vazões do `ResourceType`;
  - `banner()`.
  - O script recusa subtítulo que passe da borda.
- `docs/curseforge/descricao.md`, com os links `https://media.forgecdn.net/attachments/<a>/<b>/<arquivo>-png.png`.

## Fluxo

1. **Planeje as cenas.** Para cada novidade, defina qual ala a mostra e qual tela abrir. Mudança de tela significa refazer a imagem dela. Item ou bloco novo entra na casa (num pedestal ou numa ala). Pergunte ao dono só o que mudar o cenário, e mostre a ideia antes (`mockup-antes-de-executar`).
2. **Fotografe.**
   ```bash
   rm -f run/showcase/*.png run/showcase/result.txt
   WA_SHOWCASE="$PWD/run/showcase" ./gradlew runClient     # uns 3 min; result.txt = OK
   ```
   Rode em segundo plano e espere pelo `result.txt`. Não abra outro jogo enquanto isso. O Gradle já compila antes; se falhar a compilação, não há o que esperar.
3. **Confira numa folha de contato.** Olhar 20 capturas uma a uma gasta muito.
   ```bash
   python .claude/skills/wa-curseforge-imagens/scripts/folha.py <scratchpad>/folha.png run/showcase/s*.png --colunas 3 --largura 640
   ```
   Ajuste câmera e cenário e repita. Foram 4 rodadas até ficar bom. Os problemas mais comuns:
   - **Passo condicional:** um passo que depende de algo montado só em `showcaseWorld` some, porque a lista de passos é montada antes do mundo. Teste `StorageKind.X.loaded()`, não uma variável preenchida depois.
   - **Câmera:** a câmera dentro de um bloco, ou atrás da parede ou da mureta (escolha uma célula livre, e o pé fica em `cy`); câmera longe demais na foto geral; pedestais tapando a galeria (suba a câmera).
   - **Ordem dos blocos:** olhando para o sul, o leste fica à esquerda, então inverta a ordem para o Básico ficar à esquerda.
   - **Alcance:** para abrir uma tela, a câmera precisa estar a menos de uns 4 blocos do bloco.
   - **Recorte:** a vitrine renderiza fora da tela em 3840x2400 (1280x800 vezes `WA_SHOWCASE_SCALE`, padrão 3) com a interface na escala 6, o mesmo layout da escala 2 em 1280x800. Os recortes do `gerar_imagens.py` ficam em coordenadas de 1280x800 e as imagens saem com 1920 px; nunca amplie uma captura com fator quebrado (borra). O CurseForge recusa arquivo acima de 2 MB e reconverte o que recebe: PNG vira paleta de 256 cores quase sem perda, mas JPEG é recomprimido com qualidade baixa e borra. Por isso o `salva()` grava PNG e, se passar de 2.000.000 bytes, reduz ele mesmo para 256 cores (corte mediano); JPEG só se nem assim couber. O JEI aparece à direita, então recorte para tirá-lo.
4. **Gere as imagens.**
   ```bash
   python scripts/curseforge/gerar_imagens.py
   python .claude/skills/wa-curseforge-imagens/scripts/folha.py <scratchpad>/final.png docs/curseforge/banner.png docs/curseforge/feature-*.png --largura 640
   ```
   Confira recortes, subtítulos e detalhes. Os textos ficam em inglês, para o público internacional. Números só do código, nunca inventados.
5. **Adapte a descrição.**
   - Cada imagem nova ou refeita vira `![Título](ENVIAR:<arquivo>.png)` no lugar certo.
   - Seção nova para cada novidade grande. Não repita o mesmo texto em duas seções.
   - Mostre a imagem geral e duas ou três novas ao dono (SendUserFile) e liste o que mudou.
6. **Commit.** Quando o dono pedir, commite as imagens, o script, a vitrine e a descrição, e faça o push. Isso **não** é uma versão nova: não crie release nem changelog (ver `wa-release`).
7. **Troque os links.** O dono sobe os PNGs no CurseForge e manda os links.
   - Troque cada `ENVIAR:<arquivo>.png` pelo link dele e confira que não sobrou nenhum (`grep ENVIAR`).
   - Imagem que ele não mandou continua com o link antigo. Avise qual, e se o conteúdo dela mudou (o banner mudou de frase, por exemplo).
   - Commit e push de novo.

## Estrelas na galeria (se o dono perguntar)

Na galeria, as imagens com estrela ganham destaque e aparecem em miniatura pequena e cortada. Vale escolher fotos do jogo que se entendem de relance:
- a imagem geral, primeiro;
- a tela do roteador;
- as novidades da versão (na 1.1: Source e Tanque de Source);
- o Tablet com estatísticas;
- se couber, os armazenamentos.

Imagens que são quase só texto (o banner e a dos tiers) e telas pequenas (filtros, Baú, Vinculador) funcionam melhor na descrição.
