# Validação 0.2.1

Revisão local executada em 2026-11-04. Alvo: DataGrip 2026.2.6.1, build 262.11879.103, confirmado como a versão estável mais recente na API oficial JetBrains nesta data. JBR 25.0.4.1.

## Revisão e testes

88 testes passaram, zero falhas e zero erros. Além dos fluxos da versão anterior, há regressões para revisão de identidade e fechamento de conexão com resultado confirmado. A falha de fechamento não descarta senha gerada nem resultado da aplicação e não provoca repetição de mutações.

A revisão substituiu a read action bloqueante por ReadAction.nonBlocking, com cancelamento cooperativo. A ação de tabela captura metadados sob read action em background e abre o console no EDT. Renderers de nomes de conexão/tabela tratam texto literalmente. O verificador passou sem usos de API depreciada antes do ajuste visual final; a pipeline verifica o pacote publicado novamente.

O plugin foi carregado em uma instância real isolada do DataGrip, com projeto vazio em modo seguro e sem conexão a banco. A primeira inspeção revelou que o gerenciador completo ficava apertado na lateral do IDE. A UI foi ajustada para abrir o gerenciador em janela própria, redimensionável, com tamanho inicial de 1120x760. A Tool Window conserva atalhos e consultas.

## Ferramentas atuais

Gradle 9.8.2, IntelliJ Platform Gradle Plugin 2.19.0 e JUnit 6.1.3 foram conferidos nas fontes oficiais em 2026-11-04. O bytecode usa Java 25, runtime exigido pelo DataGrip alvo. Não usa versão EAP para distribuição. Dependabot acompanha Gradle e GitHub Actions semanalmente.

Fontes: API JetBrains https://data.services.jetbrains.com/products/releases?code=DG&latest=true&type=release ; Gradle https://services.gradle.org/versions/current ; plugin https://github.com/JetBrains/intellij-platform-gradle-plugin/releases/tag/2.19.0 ; migração de read actions https://plugins.jetbrains.com/docs/intellij/api-notable-list-2026.html .

## Distribuição

Monorepo público lucasbp/toolkit, módulo datagrip. Pacote datagrip-toolkit-0.2.1.zip e SHA-256 em artifacts/datagrip da raiz principal, pasta ignorada pelo Git. A Release de homologação é gerada pelo workflow da tag datagrip-v0.2.1, somente após testes, verificação estrutural e compatibilidade binária. A pipeline valida o checksum antes de publicar os dois assets.

## Limites

Não houve teste de gestão contra MySQL/Aurora real, acesso a credenciais reais ou instalação no IDE de uso diário. SSH/TLS, permissões do servidor e operações reais precisam de homologação em uma conexão descartável. Timeout não comprova rollback. A conexão original do usuário não foi alterada.

Os testes de banco usam executor simulado. As prévias automatizadas usam dados fictícios. Carregamento visual no IDE não substitui homologação das mutações.