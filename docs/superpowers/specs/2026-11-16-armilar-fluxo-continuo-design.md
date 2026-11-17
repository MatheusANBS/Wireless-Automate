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

Pedido do dono em 16/11, à noite: cada animação ganha um som único. Na mesma noite ele corrigiu o pedido: os sons têm de ser **assets próprios do mod**, e não tirados dos arquivos do Minecraft. Em 17/11 ele reprovou as duas primeiras passadas (síntese simples, que soava a "programa de tv dos anos 30", e gravações CC0 processadas) e pediu algo novo: mágico como os sons do Ars Nouveau, numa mistura magitech, com voz própria para cada elemento (cristais, feixe etc.) e loops que sejam naturalmente bons para repetir. A terceira passada, aprovada em 17/11 ("ficou perfeito"), é síntese própria do zero pelo gerador em `scripts/sounds/`, sem gravação de fora.

### Formato (guias oficiais e da comunidade, conferidos em 16/11)
- **Arquivo:** Ogg Vorbis, `.ogg`. É o único formato que o Minecraft aceita ([NeoForge 1.21.1, Sounds](https://docs.neoforged.net/docs/1.21.1/resources/client/sounds); [Fabric, Creating Custom Sounds](https://docs.fabricmc.net/1.21.1/develop/sounds/custom)).
- **Mono:** o arquivo tem de ser mono. O estéreo não sofre atenuação e toca sempre na posição do jogador (NeoForge, que cita o MC-146721; Fabric; [Minecraft Wiki, Sounds.json](https://minecraft.wiki/w/Sounds.json)).
- **`sounds.json`:** fica em `assets/wirelessautomate/sounds.json`. Por evento, a lista `sounds` (com `name`, `volume`, `pitch`, `weight`, `stream`, `attenuation_distance`, `preload`) e a `subtitle`.
  - Sorteio entre entradas: com mais de uma entrada, uma é escolhida, o que dá variações.
  - `stream`: só para sons longos.
  - `attenuation_distance`: padrão 16.
- **Registro:** `DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ...)` com `SoundEvent.createVariableRangeEvent(...)`. Os sons contínuos usam `AbstractTickableSoundInstance` com `looping = true` e param com `stop()`.
- **Loops sem estalo:** o guia sugere cortar o começo e o fim de partida e de parada e emendar o fim no começo com crossfade (Fabric, Dynamic Sounds). Aqui não há o que cortar: os loops já nascem periódicos (ver abaixo).

### Como são feitos
- **Síntese própria, em numpy.** `scripts/sounds/dsp.py` é o motor e `scripts/sounds/armilar.py` desenha os 14 sons; `scripts/sounds/gerar.py` grava tudo. Nada do Minecraft nem de banco de sons é usado. O motor tem:
  - coro de sintetizador: vozes desafinadas, série harmônica moldada por formantes de vogal ("u", "o", "a", "i") e sopro de ar pela mesma vogal;
  - vento cantado: ruído passado por ressonâncias estreitas nas notas, que soa a assobio vivo e nunca a senoide pura;
  - cristal: parciais quase harmônicos e de vidro, cada um dividido em cópias a poucos Hz, que cintilam;
  - chuva de brilho: grãos curtos de cristal agudo nas notas do acorde;
  - sopro com manchas tonais (como o vento dos feitiços do Ars), giro de órbita (sopro que bate na rotação), trava de latão, faísca elétrica aguda, zumbido de transformador e baque grave macio;
  - reverb de convolução com cauda que escurece, e o envio só recebe acima de 280 Hz (o grave fica seco).
  Com sementes fixas: uma nova execução dá os mesmos arquivos.
- **Regras da paleta** (as lições das passadas reprovadas):
  - nenhuma varredura de oscilador puro, que soa a teremim de rádio antigo: toda subida ou descida é ar cantado ou coro;
  - nada de estalo em banda larga nos loops, que soa a disco riscado: a eletricidade é faísca aguda (acima de 4,5 kHz);
  - nada de saturação nos eventos, porque o `tanh` em agudos densos cria lama no médio-grave;
  - grãos com ataque de 2 a 6 ms, sem clique.
- **Loops periódicos por construção.** Toda frequência é múltiplo de 1/T, todo vibrato e toda modulação fecham um número inteiro de ciclos, o ruído é filtrado em STFT circular e o reverb é convolução circular. O fim emenda no começo sem crossfade. Medido no `.ogg` final: o salto da emenda fica dentro do salto normal entre duas amostras.
- **Saída:**
  - WAV mono, 48 kHz, 16 bits, com dither;
  - conversão com o `ffmpeg` (`libvorbis` q6, mono) para `assets/wirelessautomate/sounds/armilar/<som>.ogg`;
  - os eventos de −3 a −9 dBFS de pico e de −18 a −27 LUFS (os grãos são curtos demais para o LUFS); os loops bem mais baixos, de −31,5 a −34 LUFS, para ficarem por baixo da música e dos eventos;
  - silêncio cortado e fades curtos contra estalo.
- **Variações:** os eventos que repetem têm variações sorteadas pelo `sounds.json`: o estouro 2, o impacto 3, o grão 3 e a rachadura 2. Elas mudam a cor (acorde, vogal, notas), e não só o tom.
- **Paleta do som:** magitech. A magia é tonal, toda em ré lídio (acorde de ré com nona e sétima maior): coro, cristal, vento cantado e brilho. A tecnologia é o latão e a eletricidade: o giro das órbitas, a trava, a faísca e o zumbido.
- **Prévia:** `python -I scripts/sounds/gerar.py --previa <pasta>` grava também os mp3 de prévia (um por som e um com todos em sequência, com os loops repetidos) e a folha de espectros. Medidas em `docs/preview/armilar-sons.md`.

### Onde e quando tocam
- **No cliente.** Os sons tocam só no cliente, a partir do estado que já chega pela `ChamberView` e do relógio da fase. A fase sai do `gameTime`, então todo cliente ouve no mesmo instante, e nada novo vai pela rede.
- **No ponto do mundo.** Cada som sai do ponto em que a coisa acontece: o sol, a Porta de Energia, o horizonte ou o cristal da câmara. A categoria é `BLOCKS`.
- **Pelo tick do cliente, não pelo quadro.** O desenho do bloco só roda com a Armilar na tela, e o som tem de seguir com o jogador olhando para o outro lado. Por isso quem toca é um `ClientTickEvent.Post` (`ArmilarSounds`), com uma caixa por Núcleo (`ArmilarSoundBox`) e a lógica de gatilhos pura (`ArmilarSoundTriggers`); a instância de som nova só nasce no evento, nada é alocado por quadro.
- **Contínuos.** Os três loops (`ArmilarLoopSound`, um de cada por Armilar, nunca duplicado) acompanham o estado e entram e saem por rampa de volume de cerca de 1 s, sem corte seco: o volume do sol segue a ignição da montagem e a eficiência; o do feixe e o tom (de 0,9 a 1,15) sobem com a vazão suavizada; o da pressão fica baixo enquanto `full`. Param quando a Armilar desmonta, quando o bloco some ou descarrega (a caixa é descartada e o loop para na hora), na troca de dimensão, ao sair do mundo e ao passar do alcance (o `attenuation_distance` do `sounds.json`, com 2 blocos de folga). Sem som órfão.
- **Limites.** Nenhum som toca mais de 3 vezes por segundo. Os eventos são presos a transições: o mesmo evento não dispara de novo no mesmo instante, nem quando a cena é recriada (o primeiro tick só toma o estado de partida; a montagem só toca se o `formedAt` é de até 3 s atrás e ainda não tocou para ele, com a memória por posição).

### Os sons

| Etapa | Evento | O som | Quando |
| --- | --- | --- | --- |
| Montagem | `armilar.assemble` | as órbitas pegam giro (o ar dos aros) e três travas de latão fecham; o ar sobe e o brilho é puxado para dentro (cauda de reverb ao contrário); em 1 s abre a flor: acorde de coro em ré com nona, arpejo de cristal e chuva de brilho, com um baque leve (cerca de 3 s) | uma vez, ao formar (no sol) |
| Sol aceso | `armilar.sun_loop` | coro grave em ré e lá que respira de "u" para "o", halo de vento cantado agudo e brasas raras de brilho (loop de 8 s, bem baixo) | contínuo, enquanto montada; o volume segue a eficiência (no sol) |
| Trava da sintonia | `armilar.tune_lock` | o giro acelera e trava; quatro vozes desafinadas de 22 a 38 centésimos convergem para o acorde puro, com cristal e brilho (cerca de 1,6 s) | quando `tuned` vira verdadeiro (no sol) |
| Saída da sintonia | `armilar.tune_lost` | o mesmo acorde se abre e cai cerca de 2 semitons, o giro desacelera, faíscas finas e passa-baixa que fecha (cerca de 1,5 s) | quando `tuned` vira falso (no sol) |
| Conjunção, preparação | `armilar.conj_charge` | brilho de cristal puxado para dentro, vento cantado que sobe 3 vezes de tom e coro que cresce; termina no pico, colado no estouro (0,62 s) | no começo da janela, em sintonia (no sol) |
| Conjunção, estouro | `armilar.conj_burst` | faísca curta, sopro que desce, acorde de coro com cristais e chuva densa de brilho, com baque leve; a variação 2 troca o acorde (si menor sobre ré) e a vogal (cerca de 1,7 s, 2 variações) | no pico do estouro (no horizonte) |
| Conjunção, arco | `armilar.conj_arc` | assobio cantado que desce, faíscas em queda, sopro curto e um toque de cristal (cerca de 0,6 s) | quando a cabeça do arco sai, com vazão (no horizonte, onde o arco parte) |
| Conjunção, impacto | `armilar.conj_impact` | baque macio, sopro abafado, cristal numa nota do acorde, respingo de brilho e faíscas que somem (cerca de 0,8 s, 3 variações) | na Porta, com vazão (na Porta de Energia) |
| Feixe contínuo | `armilar.flow_loop` | rio de ar que canta um acorde de lá, com faíscas agudas, brilho e um zumbido elétrico baixo em lá (loop de 6 s); o tom e o volume sobem com a vazão | contínuo na Porta enquanto sai energia (na Porta de Energia) |
| Buffer cheio | `armilar.full_loop` | pressão contida: dois rés graves a 0,25 Hz que incham e desincham devagar, coro escuro em "u" e um fio agudo tenso (loop de 8 s, baixo, sem pulso) | contínuo e baixo enquanto `full` (no sol) |
| Grão da recarga | `armilar.recharge_grain` | um cristal agudo com um ou dois ecos de cristal mais altos (cerca de 0,4 s, 3 variações em lá, dó sustenido e mi) | um por grão que nasce (na Porta de Cristais) |
| Fim da recarga | `armilar.recharge_done` | ar cantado que sobe, arpejo de cristal de lá a lá, um sino de cristal agudo que cintila, coro claro em "i" e brilho (cerca de 1,9 s) | no clarão, no cristal (no cristal) |
| Cristal racha | `armilar.crystal_crack` | estalo de vidro, lascas agudas espalhadas, duas notas dissonantes que caem e faíscas que vazam (cerca de 0,6 s, 2 variações) | quando a câmara vira rachada (no cristal) |
| Cristal fica Opaco | `armilar.crystal_dull` | nota de cristal que cai um semitom e perde o brilho (passa-baixa fechando), com um ar cantado que apaga e um coro grave curto (cerca de 1,6 s) | quando a câmara vira Opaca (no cristal) |

## Verificação

- **Servidor:** GameTests nas 4 runs, com os números de antes. Um GameTest novo prova que um consumidor que puxa a cada tick recebe energia em todos os ticks com a geração ligada, e não só um tick a cada 20. Bench antes e depois.
- **Cliente:** e2e com a extração real contínua, pela Porta drenada a cada tick, e capturas em várias fases fora da conjunção. O feixe aparece em todas. Capturas com faixa 2, 5 e 7 para a intensidade. Contagem de fitas por fase como asserção. Sem pacote e com Complementary.
