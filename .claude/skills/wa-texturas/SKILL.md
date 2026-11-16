---
name: wa-texturas
description: Use quando for criar ou mudar a textura, o modelo ou o blockstate de um bloco ou item do Wireless Automate (bloco novo, peça de multibloco, estado formado, modelo do Blockbench, peça renderizada que gira), ou quando o dono reprovar uma textura ou modelo como genérico, liso, sem textura, borrado, de bloquinhos ou com peças atravessadas.
---

# wa-texturas: textura e modelo de bloco no nível do mod

## Princípio

Bloco do Wireless Automate é **pixel art na densidade do mundo, gerada por código a partir da identidade "Porcelana e Sinal"**. Cada pixel é 1/16 de bloco. A luz vem de cima e da esquerda, e as cores saem das rampas da `PALETA`. A geometria encosta e nunca se atravessa. Nada se pinta à mão e nada se edita no PNG ou no JSON: muda-se o gerador e roda-se de novo.

## Onde cada coisa nasce

- **`scripts/textures/identidade.py`:** a `PALETA` (porcelana, grafite, coral, latão, vidro, energia...), a `LENTE` por tier, o `olho` (o emblema), o `Sprite` e o `validar`. O `validar` só aceita alfa 0 ou 255, salvo vidro.
- **`scripts/textures/blocos.py`:**
  - os pincéis (`painel`, `porcelana`, `grafite`, `etiqueta`, `marcas`, `com_olho`);
  - as texturas (`sprites()`);
  - os modelos e blockstates (`modelos()`, classe `Modelo`);
  - as prévias isométricas para a folha de sprites.
- **Peça esculpida** (forma que não sai de cubos inteiros): desenhada no Blockbench e convertida por `bbmodel_json.py`. Modelos de referência: `reparador_modelo.py`, `doca_modelo.py`, `tela_modelo.py`.
- **`scripts/textures/gerar_texturas.py`:** roda tudo e grava em `assets/`.

## As regras do desenho

1. **16 × 16 por face**, um pixel = 1/16 de bloco. Peça renderizada que gira segue a mesma densidade (skill `wa-efeitos`).
2. **Luz de cima e da esquerda:** o pincel `painel`, com claro em cima e à esquerda e escuro embaixo e à direita, em cada placa de 16 px. As emendas das placas caem na grade do bloco.
3. **Rampas da `PALETA`:**
   - de 3 a 5 tons por material, sem cor fora da paleta;
   - sem degradê: a passagem é por degrau ou por trama;
   - sem contorno escuro em bloco (contorno é só de item);
   - sem preto nem branco puros.
4. **Detalhe com motivo, poucos por face:** emenda de placa, parafuso ou rebite de latão (1 a 3 px), a única listra coral da marca, grelha de grafite e o Olho com a lente do tier. As **marcas de tier** (1 a 4 traços) também entram, para quem não distingue cores. Face lisa e vazia é reprovada ("genérico", "liso").
5. **Todo bloco vive:**
   - a identidade pede uma animação discreta em cada bloco: o Pulso do Olho, de 1,6 a 3,2 s, ou um movimento próprio da peça;
   - os quadros vão empilhados, por `animacao(quadros, frametime)`;
   - o que se move de verdade é renderizado (skill `wa-efeitos`), nunca tira de textura.

## Multibloco: solto e formado

- **Solto:** cada bloco se lê sozinho (o que é, para que serve) com a sua textura de bloco inteiro.
- **Formado:** a estrutura vira uma máquina só.
  - **Estrutura de cubos:** texturas conectadas escolhidas pelo blockstate (posição na estrutura). As emendas, as listras e as escalas ficam alinhadas à grade do mundo, de modo que continuam de um bloco para o outro.
  - **Escultura (modelo do Blockbench):**
    - Fatie o modelo na grade de blocos: cada bloco recebe o seu pedaço, **dentro de 0 a 16** em cada eixo, porque UV fora do bloco sai preta e a culling falha.
    - Cada fatia vira um modelo do estado formado, num blockstate por parte.
    - Corte os cubos que cruzam a fronteira de bloco em dois (a ferramenta "slice to block grid" do Blockbench, ou o script).

### Blockstate por posição

O modelo do jogo é o de `blockstates_ressonador()` no `blocos.py`:

- **A propriedade de estado:**
  - Um enum `position` com o valor `loose` (solto) e um nome por lugar na estrutura. Na carcaça de hoje são as letras n/c/p por eixo (`posicoes_carcaca()`).
  - Para peças com frente, `facing` e `formed`.
  - Quem troca o estado é o código que forma a estrutura (o Java); a textura não decide nada.
  - As letras n/c/p servem só para a caixa de cubos. Uma escultura dá um nome por papel da fatia, em minúsculas e sem acento (ex.: `base_canto`, `pilar_topo`). A simetria fica no `y`.
- **O arquivo de blockstate:** um `variants` com uma entrada por valor, cada uma apontando para `<bloco>_<posição>`. O `loose` aponta para o modelo solto, e o item usa o solto (`models/item/<bloco>.json` com `parent` nele).
- **A fatia como `Modelo`:**
  - Cada fatia é um `Modelo(f"{bloco}_{posicao}", particula)` com uma `caixa(de, ate, faces, rot)` por elemento, e entra no dicionário de `construir_modelos_*()`.
  - Se a fatia vier do `.bbmodel`, os elementos saem do `converter()` do `bbmodel_json.py` e passam pelo `validar()` dele.
- **Simetria:** quando uma fatia é a outra girada (os quatro cantos, por exemplo), use um modelo só e o `y` do blockstate (90, 180, 270). Assim se desenha uma vez só.

### Textura que continua de uma fatia para a outra

- **Material que se repete** (placa, grelha, latão): a UV de cada face é a sua posição no mundo em texels, módulo 16. Uma face que começa em x = 20 da escultura usa `u = 20 mod 16 = 4`. Assim a emenda da placa cai na mesma linha nos dois blocos.
- **Desenho que atravessa blocos** (a listra que sobe o pilar, o Olho grande): desenhe a folha da peça inteira no gerador (ex.: 16 × 48 para um pilar de 3 blocos) e recorte um sprite de 16 × 16 por bloco. Cada fatia usa o seu recorte, com a UV de 0 a 16.
- **Qual das duas:**
  - Cada face usa uma regra só.
  - Face só de material: módulo 16.
  - Face com qualquer pedaço de desenho de folha: recorte. O material em volta é pintado dentro da folha, no gerador, com a mesma fase de 16 do mundo.
- **Confira na prancha:** monte as fatias lado a lado na posição do mundo e veja a emenda seguir sem degrau.

### Limite de elementos

- Uma fatia de bloco leva o menor número de elementos que segura a silhueta. Hoje o maior modelo do mod tem 18 elementos (a bateria).
- **Regra do projeto:** até 40 elementos por fatia; 41 reprova.
- Passou disso:
  - simplifique a forma (degraus maiores, menos segmentos no arco); ou
  - mande a peça para o render (BER), se ela também se move.
### Peça que se move

- Renderizada, facetada e contínua, com textura em pixel.
- Joia ou cristal preso nela tem engaste próprio, que gira junto (skill `wa-efeitos`, `references/arte.md`).

## Geometria: encosta, nunca atravessa

- **Peças que se tocam encostam exatamente:** volume de interseção zero. Nada de folga de 0,01 nem de caixa "um pouco maior": isso é sobreposição e pisca.
- **Face coberta por peça encostada sai.** No limite do bloco que dá para fora da estrutura, entra o `cullface` (veja "Cullface entre fatias").
- **Duas faces desenhadas nunca dividem o mesmo plano** com área em comum.
- **Forma redonda** sem sobrepor:
  - degraus de cubos alinhados (octógono em escada); ou
  - segmentos girados que se tocam na aresta de dentro, com a fenda em V por fora.
  - Peça girada que apoia em outra gira em torno da aresta de apoio.
- **Ângulos aceitos pelo modelo de bloco:** -45, -22,5, 0, 22,5 e 45 graus. Giro maior é dividido em quartos de volta (o `bbmodel_json` faz isso).
- **Verifique sempre com número:**
  - `python -I .claude/skills/wa-texturas/scripts/verificar_modelo.py <arquivo.bbmodel|modelo.json>` imprime a sobreposição de volume (OBB contra OBB) e as faces no mesmo plano. As duas contagens têm de dar 0.
  - **Fatias de um multibloco:** `verificar_modelo.py --junto <fatia.json>@x,y,z[,giro] ...`, com cada fatia no seu bloco (x, y, z em blocos). O `giro` opcional é o `y` do blockstate daquela variante (90, 180 ou 270).
    - Confere cada fatia sozinha e depois o conjunto: a peça que entra no bloco vizinho e a face de fronteira desenhada dos dois lados.
    - O aviso "faces escondidas" lista a face desenhada e coberta por inteiro pela peça do vizinho. Tire essa face da fatia.
  - A classe `Modelo` do `blocos.py` faz as mesmas conferências nos modelos gerados.
- **Cullface entre fatias:**
  - O `cullface` só some com a face quando o vizinho tem a face cheia e opaca (cubo inteiro).
  - Uma fatia esculpida não tem a face cheia, então a face que encosta na fatia vizinha **sai do modelo**: não conte com o `cullface` para isso.
  - Deixe o `cullface` só nas faces do limite que dão para fora da estrutura (o `Modelo.caixa` já o põe sozinho nas faces do limite).
  - No jogo, confira de perto andando em volta: não pode haver fresta de céu nem face piscando na emenda.

## Ordem de trabalho

1. **Leia `docs/identidade-visual.md` e as texturas vizinhas do mod.** O padrão a igualar é concreto:
   - **Os blocos já aprovados do mod**, com a mesma luz, as mesmas rampas e a mesma densidade de detalhe por face.
   - **Os blocos vanilla do lado na prancha:** `iron_block`, `copper_block` e `polished_deepslate`.
     - O contraste entre o tom mais claro e o mais escuro de uma face fica na faixa deles.
     - Não deixe a face mais lisa nem mais ruidosa que a deles.
2. Escreva o gerador (ou o `.bbmodel`) e rode a verificação.
3. Mostre ao dono e pergunte antes de abrir o jogo (memória "pensar antes de rodar"):
   - a prancha da peça solta e da formada, com as prévias e com renders do Blockbench (vista offscreen, de 4 ângulos);
   - um ou dois blocos vanilla do lado, para comparar.
4. Depois do ok: uma rodada no jogo, sem shaderpack e com Complementary. Fotos por ângulo; emendas sem fresta e sem pisca ao andar em volta.
5. Commit só depois do "aprovado" (skill `wa-feature`).

## Erros comuns

| Instinto | Correção |
| --- | --- |
| Pintar no Aseprite, ou editar o PNG gerado | Mudar o gerador em Python e rodar de novo |
| Paleta própria, sombra puxando para o azul | As rampas da `PALETA` de `identidade.py` |
| Contorno escuro de 1 px no bloco | Contorno só em item; no bloco, `painel` (luz e sombra) |
| Afastar ou crescer 0,01 para fugir do z-fighting | Encostar exato e tirar a face coberta |
| Peça formada saindo do bloco | Fatiar na grade: cada pedaço dentro de 0 a 16 |
| Face lisa de uma cor | Motivos da identidade: emenda, rebite, listra, grelha, Olho |
| Bloco parado | Animação discreta (o Pulso) pela tira de quadros |
