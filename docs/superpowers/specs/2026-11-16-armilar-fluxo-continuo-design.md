# Ressonador: geração de tick em tick e feixe contínuo

Pedido do dono em 16/11/2026, à noite, depois de testar a Armilar no `main`. Duas mudanças. O trabalho é direto no `main`, com um commit por parte, e vai para o GitHub quando terminar.

1. O Núcleo gera e entrega energia de tick em tick, e não mais um segundo inteiro de uma vez.
2. O feixe até a Porta de Energia fica contínuo enquanto a extração for contínua, com a intensidade dada pela energia que sai.

## Hoje

- **Lote de 20 ticks.** O `ResonatorManager` roda, a cada 20 ticks e por Núcleo, um lote (`ResonatorBatch.run(level, core, 20)`). O lote faz a deriva da estação, gera 20 ticks de energia de uma vez (`generate`), gasta os cristais na proporção do que gerou, anda a recarga, fecha a vazão do lote em faixa de 0 a 7 (`CoreFlow.closeBatch`) e manda o update tag se algo mudou.
- **Porta passiva.** A Porta de Energia só entrega: quem está do lado tira do buffer (`ResonatorPorts`). Com geração em lote, quem puxa sem limite esvazia o buffer no tick seguinte ao lote e fica 19 ticks sem nada.
- **Arco preso à conjunção.** No cliente, o feixe do sol até o horizonte, o arco até a Porta e o impacto só aparecem na janela da conjunção, a cada 5,24 s e só em sintonia. A vazão só muda a força. Por isso o feixe pisca mesmo com extração contínua.

## 1. Servidor: geração de tick em tick

- **Geração por tick.** Todo Núcleo montado, num chunk com tick de blocos, gera a cada tick o seu `generationPerTick()`, cortado no espaço que sobra no buffer.
  - O custo por tick tem de ser mínimo. A quantidade por tick fica em cache no Núcleo.
  - O cache é invalidado por tudo o que muda a geração: cristal que entra, sai ou muda de estado; dial; estação e deriva; Sintonizador; interferência; Eco; montagem.
  - O lote também recalcula o cache, como rede de segurança.
  - Por tick, o trabalho é uma soma e um corte.
- **O lote de 20 ticks fica** para o resto, que não precisa de tick: deriva, desgaste dos cristais, recarga, faixa de vazão e update tag. Ele continua escalonado por fase da posição.
- **Desgaste.** O desgaste passa a vir do que foi gerado de fato nos ticks do lote. O Núcleo acumula o gerado e o que teria gerado sem o corte do buffer, e o lote aplica a mesma regra de hoje (`spent = ceil(ticks × gerado / cheio)`). O balanceamento não muda: a mesma energia por segundo e a mesma vida de cristal.
- **Avisos às Portas.** O aviso de "energia de 0 para mais" (`notifyPorts`), que hoje sai no lote, passa a sair no tick em que a energia sobe de 0.
- **A Porta continua passiva.** Quem tira da Porta recebe agora um fluxo por tick.
- **Medida e testes.**
  - A medida do gerenciador (`ResonatorProfile` e bench) é refeita, porque o custo por tick sobe.
  - Os GameTests que chamam `ResonatorBatch.run(level, core, 20)` para gerar passam por um apoio de teste que roda N ticks de geração mais o lote.
  - Os números esperados não mudam.

## 1b. Porta de Energia ativa

O dono pediu em 16/11, à noite, depois do design acima: a Porta de Energia passa a empurrar a energia para os vizinhos a cada tick, além de deixar que tirem dela. A geração fica coerente com a tela: o que a tela diz por tick (o Ultimate, cerca de 4,2 bilhões de FE/t) é o que sai por tick.

- **Para onde empurra.** Para os vizinhos da Porta que não são membros da Armilar e que aceitam energia.
  - Primeiro a capability `long` do mod (`BulkEnergy`, a do roteador): passa até o valor inteiro de uma vez.
  - Depois a `IEnergyStorage` do NeoForge, em `int`: chamadas de até `Integer.MAX_VALUE`, repetidas enquanto o vizinho aceitar e houver energia, com um teto pequeno de chamadas por tick.
- **Quanto empurra.** A cada tick, empurra o que houver no buffer até `maxOutput()` por tick e por Porta, para não esvaziar o buffer de uma vez num vizinho infinito. Se o vizinho não aceitar tudo, o resto fica no buffer.
- **Custo.**
  - A busca das capabilities usa `BlockCapabilityCache`, criado quando a Porta é registrada no Núcleo (quando monta) e solto quando desmonta.
  - Por tick e por Porta, o trabalho é ler o cache e chamar o vizinho.
  - Sem vizinho que aceite energia, nada é feito: um `null` por face em cache.
- **Com roteador e vizinho na mesma Porta.** O roteador (`NetworkManager.tick`) roda antes do empurrão e tira primeiro em cada tick. O empurrão esvazia o buffer a cada tick, então um roteador que puxa menos de uma vez por tick (em espera) leva só a geração de um tick por puxada; o resto já foi para o vizinho. É aceito: a prioridade do roteador vale por tick.
- **Vazão.** O que sai empurrado soma no `CoreFlow` da Porta, como o que é tirado. A faixa de vazão, e com ela o feixe, sai do total que deixou a Porta.
- **Testes.**
  - GameTest: um receptor ao lado da Porta recebe energia a cada tick sem puxar nada.
  - GameTest: com a capability `long`, um tick entrega a geração inteira de um Núcleo grande (acima de `Integer.MAX_VALUE`).
  - GameTest: membro da Armilar ao lado não recebe nada.

## 2. Cliente: feixe contínuo

- **Fluxo base.** Enquanto a vazão suavizada for maior que zero, o caminho da energia fica aceso sem parar: o feixe do sol ao horizonte, o arco do horizonte até a Porta e o brilho no pé da Porta. As fitas correm pelo fluxo, como no filme.
  - Isso vale com ou sem sintonia, porque a energia sai do buffer mesmo fora de sintonia.
- **Intensidade pela energia que sai.** A faixa suavizada (0 a 7, já sincronizada) dá a força `k`, que muda:
  - a largura e o alfa das fitas;
  - a velocidade do fluxo na textura;
  - a quantidade de brasas no pé da Porta;
  - a luz da Porta na passada de luz.

  Vazão pequena dá um fio fino e lento. Vazão no máximo dá o feixe cheio do filme.
- **A conjunção vira um pulso por cima.** Em sintonia, a cada conjunção, o estouro (clarão no horizonte e choque), a cabeça do arco correndo e o impacto (clarão, sigilo, choque no chão e brasas) aparecem por cima do fluxo base, como no filme. Sem vazão, a conjunção acontece só no sol, como hoje.
- **Sem cortes.**
  - O fluxo base acende e apaga com a faixa suavizada (cerca de 1 s), sem corte seco.
  - O pulso da conjunção não apaga o fluxo base no fim da dissolução.

## 3. Sons da Armilar

Pedido do dono em 16/11, à noite: cada animação ganha um som único. Na mesma noite ele corrigiu o pedido: os sons têm de ser **criados para o mod**, como assets próprios, e não tirados dos arquivos do Minecraft.

### Formato (guias oficiais e da comunidade, conferidos em 16/11)
- **Arquivo:** Ogg Vorbis, `.ogg`. É o único formato que o Minecraft aceita ([NeoForge 1.21.1, Sounds](https://docs.neoforged.net/docs/1.21.1/resources/client/sounds); [Fabric, Creating Custom Sounds](https://docs.fabricmc.net/1.21.1/develop/sounds/custom)).
- **Mono:** o arquivo tem de ser mono. O estéreo não sofre atenuação e toca sempre na posição do jogador (NeoForge, que cita o MC-146721; Fabric; [Minecraft Wiki, Sounds.json](https://minecraft.wiki/w/Sounds.json)).
- **`sounds.json`:** fica em `assets/wirelessautomate/sounds.json`. Por evento, a lista `sounds` (com `name`, `volume`, `pitch`, `weight`, `stream`, `attenuation_distance`, `preload`) e a `subtitle`.
  - Sorteio entre entradas: com mais de uma entrada, uma é escolhida, o que dá variações.
  - `stream`: só para sons longos.
  - `attenuation_distance`: padrão 16.
- **Registro:** `DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ...)` com `SoundEvent.createVariableRangeEvent(...)`. Os sons contínuos usam `AbstractTickableSoundInstance` com `looping = true` e param com `stop()`.
- **Loops sem estalo:** cortar o começo e o fim de partida e de parada, e emendar o fim no começo com crossfade (Fabric, Dynamic Sounds).

### Como são feitos
- **Gerador próprio.** Um gerador em Python (`scripts/sounds/gerar_sons.py`) sintetiza cada som do zero, com `numpy`, osciladores, FM, ruído filtrado e envelopes, e com sementes fixas. Os sons ficam reproduzíveis, como as texturas do `gerar_texturas.py`. Nenhum som do Minecraft é usado nem copiado.
- **Saída:**
  - WAV mono, 48 kHz, 16 bits;
  - conversão com o `ffmpeg` (`libvorbis`, qualidade 5, mono) para `assets/wirelessautomate/sounds/armilar/<som>.ogg`;
  - pico normalizado em −1 dBFS nos eventos, e os loops mais baixos, no mesmo nível dos sons de bloco do vanilla;
  - silêncio cortado e fades curtos contra estalo;
  - loops com período exato e crossfade.
- **Variações:** os eventos que repetem (grão, impacto, estouro) têm 2 ou 3 variações, sorteadas pelo `sounds.json`.
- **Paleta do som:** a mesma da imagem. Latão das órbitas (sinos e metal com parciais inarmônicas), sol de plasma (zumbido grave com batimento lento e crepitar), cristais (vidro e cristal agudo) e energia (zumbido elétrico que corre).
- **Prévia:** o gerador grava também os `.ogg` numa pasta de prévia, e o controlador manda os arquivos ao dono para ouvir antes do commit.

### Onde e quando tocam
- **No cliente.** Os sons tocam só no cliente, a partir do estado que já chega pela `ChamberView` e do relógio da fase. A fase sai do `gameTime`, então todo cliente ouve no mesmo instante, e nada novo vai pela rede.
- **No ponto do mundo.** Cada som sai do ponto em que a coisa acontece: o sol, a Porta de Energia, o horizonte ou o cristal da câmara. A categoria é `BLOCKS`.
- **Contínuos.** Os sons contínuos acompanham o estado (volume e tom) e param quando a Armilar some, desmonta ou sai do alcance. Sem som órfão.
- **Limites.** Nenhum som toca mais de 3 vezes por segundo. Os eventos são presos a transições: o mesmo evento não dispara de novo no mesmo instante, nem quando a cena é recriada.

### Os sons

| Etapa | Evento | O som (sintetizado) | Quando |
| --- | --- | --- | --- |
| Montagem | `armilar.assemble` | arpejo subindo de três sinos de latão, um por órbita que entra, e um brilho de cristal no fim (cerca de 3 s) | uma vez, ao formar |
| Sol aceso | `armilar.sun_loop` | zumbido grave de plasma (55 e 110 Hz com batimento lento) e um crepitar suave (loop de 4 s) | contínuo, enquanto montada; o volume segue a eficiência |
| Trava da sintonia | `armilar.tune_lock` | acorde de cristal que se afina: três notas que convergem para a quinta e ressoam | quando `tuned` vira verdadeiro |
| Saída da sintonia | `armilar.tune_lost` | o mesmo acorde se desafinando e descendo, com um chiado curto | quando `tuned` vira falso |
| Conjunção, preparação | `armilar.conj_charge` | carga subindo: varredura de tom com brilho, de 0,6 s | no começo da janela, em sintonia |
| Conjunção, estouro | `armilar.conj_burst` | golpe de sino de latão com estalo de energia (2 variações) | no pico do estouro |
| Conjunção, arco | `armilar.conj_arc` | descarga elétrica que corre e desce (zap com crepitar) | quando a cabeça do arco sai, com vazão |
| Conjunção, impacto | `armilar.conj_impact` | baque grave com anel metálico e chiado (3 variações) | na Porta, com vazão |
| Feixe contínuo | `armilar.flow_loop` | zumbido elétrico que corre, com modulação lenta (loop de 3 s); o tom e o volume sobem com a vazão | contínuo na Porta enquanto sai energia |
| Buffer cheio | `armilar.full_loop` | pressão: pulso grave a 0,5 Hz, no ritmo do sol (loop de 2 s) | contínuo e baixo enquanto `full` |
| Grão da recarga | `armilar.recharge_grain` | tique curto de faísca (3 variações) | um por grão que nasce |
| Fim da recarga | `armilar.recharge_done` | brilho de cristal se enchendo: notas subindo e um sino | no clarão, no cristal |
| Cristal racha | `armilar.crystal_crack` | estalo de vidro com fragmentos agudos | quando a câmara vira rachada |
| Cristal fica Opaco | `armilar.crystal_dull` | nota de cristal que perde o brilho e abafa | quando a câmara vira Opaca |

## Verificação

- **Servidor:** GameTests nas 4 runs, com os números de antes. Um GameTest novo prova que um consumidor que puxa a cada tick recebe energia em todos os ticks com a geração ligada, e não só um tick a cada 20. Bench antes e depois.
- **Cliente:** e2e com a extração real contínua, pela Porta drenada a cada tick, e capturas em várias fases fora da conjunção. O feixe aparece em todas. Capturas com faixa 2, 5 e 7 para a intensidade. Contagem de fitas por fase como asserção. Sem pacote e com Complementary.
