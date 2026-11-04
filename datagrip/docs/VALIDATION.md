# Validação 0.3.1

Executada localmente em 2026-11-04 contra DataGrip 2026.2.6.1, build 262.11879.103, Java 25, Gradle 9.8.2 e IntelliJ Platform Gradle Plugin 2.19.0.

90 testes passaram, zero falhas. Incluem carregamento automático e exclusivo da conexão selecionada, proteção contra fallback para outro servidor quando a conexão foi removida e ausência de mutação ao abrir o gerenciador. A regressão identificou e corrigiu o seletor de conexão sendo habilitado após a leitura terminar.

Build, verificação estrutural e Plugin Verifier passaram. Veredito: Compatible, sem uso de APIs depreciadas.

A abertura de consultas cria explicitamente o arquivo SQL antes de montar o JdbcConsole. O fluxo anterior com apenas fromDataSource podia resultar em arquivo nulo no SDK 262; a mesma correção foi aplicada ao adaptador RSD interno, sem dependência entre os produtos.

A ação Gerenciar usuários e permissões está registrada no DatabaseViewPopupMenu e DatabaseViewToolbar. Reutiliza o identificador exato da conexão e mantém as sessões gerenciadas pelo DataGrip. Janelas são reutilizadas por identificador, e fechadas com o projeto. Abrir apenas consulta a lista de contas; mutações continuam exigindo revisão e Aplicar alterações.

O pacote instalável e checksum ficam em artifacts/datagrip na raiz principal, ignorados pelo Git. A publicação usa tag datagrip-v0.3.1 e a pipeline do módulo.

Não houve mutação em MySQL/Aurora real ou uso de credenciais reais nesta validação. A homologação de permissões do servidor, SSH/TLS e operações reais segue pendente. O IDE de uso diário não foi alterado.
