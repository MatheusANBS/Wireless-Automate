---
name: wa-shaders
description: Use quando o dono pedir qualquer efeito visual, animação, shader, partícula, brilho ou bloom no Wireless Automate, ou trouxer uma referência visual (GIF, vídeo, print, outro mod) para replicar, e quando um efeito sair "borrado", "estranho", "de baixa qualidade", "diferente da referência" ou for reprovado. Vale para a câmara do Ressonador e para qualquer bloco, item, tela ou mundo.
---

# wa-shaders: replicar qualquer ideia visual

## Princípio

**Nenhuma tecnologia é a padrão.** Core shader próprio, pós-processamento, framebuffer, partículas do vanilla, GeckoLib, Veil, Photon, Lodestone, Shine: cada efeito escolhe a sua pelo que a evidência mostra (fidelidade à referência, custo medido, compatibilidade, licença), e o dono decide. Preferência, costume ou "o que o mod já usa" não é critério. A meta é o resultado da referência; se ela não sai com a técnica atual, troca a técnica, não a meta.

## Método

1. **Decompor a referência.** Extraia os quadros (`scripts/quadros.py`: folha de quadros com o tempo de cada um, e recortes ampliados de um trecho). Monte a tabela da linha do tempo: cada elemento, quando nasce e some (em segundos), cor, forma, movimento e o que dispara o quê. Descreva o que vê em cada trecho antes de pensar em código. Referência sem animação (print): peça o arquivo original.
2. **Pesquisar as opções, todas.** Para cada elemento, liste os caminhos possíveis, nativos e com dependência (`references/dependencias.md`), e a matemática conhecida (`references/tecnicas.md`). Faça buscas de ângulos diferentes (técnica, matemática, implementação de referência, estado atual da dependência no NeoForge 1.21.1) e registre no spec: fonte, o que dá, o que custa e por que entra ou sai. Para cada técnica, pelo menos duas alternativas comparadas (um catálogo com uma opção só não é escolha). Se a dependência fica obrigatória ou opcional (skill `wa-mod-opcional`) é decisão do dono, com os prós e contras na mesa.
3. **Comparar com números, não com gosto.** Quando houver dúvida real entre um caminho nativo e um com dependência, faça os dois em protótipo (ramo à parte), meça o tempo de quadro na mesma rodada e ponha os dois ao lado da referência. Leve a tabela ao dono: fidelidade, custo, compatibilidade (Iris, Embeddium, ATM10), licença, manutenção e esforço.
4. **Construir num e2e só do efeito** (`WA_E2E_ONLY=<efeito>`), com o tempo do efeito parado em cada fase da referência por um gancho de teste, para cada captura bater com um quadro.
5. **Verificar a fidelidade lado a lado** (`scripts/lado_a_lado.py`: quadro da referência e captura da mesma fase). Leia as imagens e liste as diferenças por elemento (forma, cor, escala, tempo, brilho). Corrija a maior diferença primeiro. Quando as rodadas de ajuste param de diminuir uma diferença, a técnica é a errada: volte ao passo 2.
6. **Mostrar e parar.** Nada de commit antes do "aprovado" do dono; um commit por elemento.

## Fatos do projeto que valem para qualquer escolha

- O mod roda no ATM10 (NeoForge 1.21.1, Java 21) com Iris/Oculus e Embeddium/Sodium possíveis: testar com eles quando a técnica mexe em pipeline. Shaderpack do Iris ativo (`ShaderPackCheck`) costuma quebrar core shaders e framebuffers próprios: o efeito precisa de um caminho para esse caso.
- Performance é requisito (skill `wa-performance`): medir sempre na mesma rodada, sem alocação por quadro, custo zero sem o efeito à vista.
- Armadilhas do pipeline, do GLSL e do e2e: `references/armadilhas.md`.
- O que o dono já aprovou e reprovou, e em que contexto: `references/historico.md`. É contexto, não regra: não descarte nem favoreça uma técnica por ela constar lá; um pedido novo do dono vale mais que uma decisão antiga; quando conflitam, pergunte.
- Os arquivos de `references/` são pontos de partida, não verdade: versões, licenças e fórmulas se conferem na fonte antes de entrar no spec.

## Erros comuns

| Erro | Correção |
| --- | --- |
| Escolher a técnica antes de decompor a referência | Passo 1 primeiro: a tabela da linha do tempo decide o que precisa existir |
| Descartar dependência sem medir | Protótipo e números (passo 3) |
| Ajustar constantes indefinidamente | Ajuste que não diminui a diferença = trocar a técnica |
| Ficar na primeira técnica do catálogo | Comparar pelo menos duas por elemento |
| Comparar de memória com a referência | Sempre lado a lado, na mesma fase |
| Prometer o impossível (colmeia só de hexágonos numa esfera) | Conferir a matemática: `references/tecnicas.md` diz os limites |
