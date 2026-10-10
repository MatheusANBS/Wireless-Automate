---
name: wa-release
description: Fecha e publica uma versão do Wireless Automate - mod_version, changelog separado e changelog unificado do CurseForge, descrição, README, progresso, verificação completa, merge no main, tag, release no GitHub com o jar e limpeza dos jars velhos. Use quando o dono disser "fechamento da X", "gera o release", "sobe a versão", "changelog", "tag", "publica", "segue pro fechamento", ou pedir o release depois de commitar, mesmo sem dizer o número da versão. Não use para fazer a feature em si (wa-feature) nem para as imagens da página (wa-curseforge-imagens).
---

# Fechamento de versão

Uma versão só existe quando muda o que o jogador recebe no jar. Antes de tudo, veja o que já existe:

```bash
grep mod_version gradle.properties
ls docs/curseforge/changelog-*.md
gh release list --limit 5
git log --oneline <última tag>..HEAD
```

Se a release e os dois changelogs da versão atual já existem e o que mudou desde a tag é só documentação, imagens ou ferramenta de desenvolvimento, **não crie versão nova**. Diga isso ao dono e mostre o que já existe. Um jar igual com número novo confunde quem baixa. Foi o caso das imagens do CurseForge depois da 1.1.0.

## Número da versão

- Correção sem mudança de protocolo nem de mundo: patch (1.0.1 → 1.0.2).
- Feature nova, ou protocolo mudou (`ModPayloads.VERSION`): minor (1.0.x → 1.1.0). Avise no changelog que servidor e clientes precisam da mesma versão.
- Se não estiver claro, pergunte, com a opção recomendada primeiro.

## Arquivos

1. **`gradle.properties`:** `mod_version` e, se o escopo mudou, `mod_description`. Nunca suba `neo_version` (é a mínima exigida e precisa continuar 21.1.248, a mais antiga que o dono usa; o ATM10 traz a 21.1.251).
2. **Changelog separado:** `docs/curseforge/changelog-<versão>.md`, em inglês, com o público do CurseForge em mente:
   - primeira linha com o resumo da versão;
   - blocos em negrito por tema ("**Ars Nouveau Source** (optional, needs …)"), bullets curtos com números reais (vazões e capacidades da config padrão);
   - "**Other changes**";
   - por fim, "**Compatibility note:**" (protocolo, mundos antigos, NeoForge mínimo, mods opcionais).
   Use `docs/curseforge/changelog-1.1.0.md` como modelo.
3. **Changelog unificado:** `docs/curseforge/changelog-curseforge-<versão>.md`. Renomeie o da versão anterior com `git mv changelog-curseforge-<anterior>.md changelog-curseforge-<nova>.md` e acrescente no topo `## <versão>` com o mesmo texto do separado. Fica um arquivo só, com todas as versões, a mais nova em cima.
4. **`docs/curseforge/descricao.md`, `README.md` e `README.en.md` (a versão em inglês, sempre igual à portuguesa):** o que a versão acrescenta (tabelas de tiers e capacidades, linha de compatibilidade). Confira cada número no código (`ResourceType`, `StorageKind`, `RouterTier`). Não invente.
5. **`docs/progresso.md`:** estado, próximo passo e linha no histórico.

## Verificação

```bash
./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource
WA_E2E="$PWD/run/e2e" ./gradlew runClient    # result.txt = OK
```

Depois do build, confira o `neoforge.mods.toml` gerado (versão, dependências opcionais) e apague de `build/libs/` os jars de versões antigas, para o jar da release ser o único e o certo. Um jar 1.0.2 velho já foi sobrescrito por builds de dev.

## Publicação (com o ok do dono para merge e push)

Pergunte como integrar, com "Merge no main + release (Recomendado)" primeiro. Depois:

```bash
git checkout main && git merge --no-ff <ramo>        # ou já no main
git commit ...                                       # "X.Y.Z: versão, changelog, ..."
git push origin main
git tag vX.Y.Z && git push origin vX.Y.Z
gh release create vX.Y.Z build/libs/wirelessautomate-X.Y.Z.jar \
  --title "Wireless Automate X.Y.Z" --notes-file docs/curseforge/changelog-X.Y.Z.md
```

Confira com `gh release view vX.Y.Z` (o jar anexado) e veja se o CI do commit passou.

Depois do merge, ofereça apagar os ramos da etapa (local e remoto) e as worktrees de agentes. Antes de apagar, confira com `git log main..<ramo>` e `git cherry -v main <ramo>` que nada fica de fora. Apague só com o ok.

## O que o dono faz

Subir o jar no CurseForge e colar o changelog. Entregue os caminhos: o jar em `build/libs/` e o texto em `changelog-<versão>.md`. Se a página do projeto precisar de imagens novas, use `wa-curseforge-imagens`.
