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

## Verificação

- **Servidor:** GameTests nas 4 runs, com os números de antes. Um GameTest novo prova que um consumidor que puxa a cada tick recebe energia em todos os ticks com a geração ligada, e não só um tick a cada 20. Bench antes e depois.
- **Cliente:** e2e com a extração real contínua, pela Porta drenada a cada tick, e capturas em várias fases fora da conjunção. O feixe aparece em todas. Capturas com faixa 2, 5 e 7 para a intensidade. Contagem de fitas por fase como asserção. Sem pacote e com Complementary.
