# Validação 0.2.0

Executada localmente em 2026-11-04 com JBR 25.0.4.1, Gradle Wrapper 9.5.0 e SDK da instalação DataGrip 2026.2.6.1, build 262.11879.103.

## Resultados

81 testes passaram, zero falhas e zero erros. Incluem planejamento de grants/roles, criação de conta, edição de identidade, bloqueio e senha, gramática de nomes, templates SQL e fluxo do painel Swing com executor simulado.

`build.ps1` passou: testes, `verifyPluginStructure`, `buildPlugin` e SHA-256. `verifyPlugin` terminou com Compatible contra DB-262.11879.103, com uma chamada de API depreciada: ReadAction.compute na leitura de metadados. A chamada ainda existe na linha suportada; não foi declarada compatibilidade com linhas futuras do IDE.

O ZIP e checksum foram copiados pelo script para `artifacts/datagrip` na árvore principal do projeto. `git check-ignore` confirmou a exclusão dessa pasta pelo .gitignore. O wrapper inclui a licença original em seu JAR e valida o checksum da distribuição.

## Casos relevantes

- Conta e host tratados separadamente; barras, contrabarras, quotes, Unicode e nomes anônimos existentes.
- Grants aditivos, revogação explícita, preservação de roles padrão, estados de permissões incompletos e somente leitura.
- Estado concorrente alterado entre revisão e aplicação bloqueia o plano. Falha parcial, flush negado e refresh negado não disparam retry automático.
- Rename, lock/unlock e reset de senha preservando o plugin autenticador. IAM e métodos externos são bloqueados no reset. Timeout de rename leva somente à leitura para localizar o estado atual.
- Formulários selecionam conta e alvo, geram revisão e aplicam com FLUSH via executor simulado. Troca de conexão invalida o plano.
- Templates SELECT 1000, nomes de colunas e COUNT preservam quoting e não interpretam nomes como fragmentos SQL.

## Artefato verificado

`datagrip-toolkit-0.2.0.zip`

SHA-256: `d2f6b986f76f4a5c3674e1b089851cb47be66af7eea896af15596bc584a8ad9d`

Faixa declarada: 262.11879 a 262.*; a verificação executada cobriu somente 262.11879.103.

## Limites

Nenhuma conexão a banco real foi aberta, nenhum comando de conta/permissão foi executado em MySQL/Aurora e o plugin não foi instalado no IDE de uso diário. Credenciais, SSH/TLS, resultados reais e permissões do servidor exigem homologação interativa. Timeout depende do driver; não significa rollback.

A prévia visual em `build/preview/admin.png` e `permissions.png` usa dados fictícios e Swing Nimbus em modo headless. No DataGrip a UI acompanha o tema do IDE. Não representa um teste da integração nativa.

O workflow está preparado, mas não foi executado no GitHub. Repositório remoto, push, PR e publicação de pacote ainda dependem de autorização.
