# SQL Toolkit

Plugin DataGrip para administrar contas MySQL/Aurora por uma tela visual e acelerar consultas do dia a dia. Módulo `datagrip/` do repositório público [lucasbp/toolkit](https://github.com/lucasbp/toolkit).

## Usuários e permissões

No Database Explorer, selecione uma conexão MySQL/Aurora, schema ou tabela. Use o botão direito e Gerenciar usuários e permissões, ou o ícone de usuário na barra do explorador. O gerenciador abre vinculado à conexão selecionada e carrega as contas automaticamente. Reabrir a mesma conexão traz a janela existente à frente; conexões diferentes têm janelas independentes. A lista permite buscar nome ou host. O gerenciador abre em uma janela própria, redimensionável, para evitar controles apertados na lateral do IDE. A área à direita apresenta a conta exata e suas permissões; formatos de grant reconhecidos são organizados por objeto, e o texto integral retornado pelo servidor continua disponível na aba SQL atual.

- Editar usuário prepara alteração de nome, host, bloqueio de login e nova senha aleatória. A senha é gerada pelo servidor, preservando o método de autenticação existente quando suportado. Métodos externos, como IAM, não são substituídos.
- Roles permite alterar atribuições e roles ativas por padrão. Cada inclusão ou remoção vira uma ação explícita; as demais atribuições são preservadas.
- Alterar permissões permite escolher schema inteiro ou tabela específica, conceder ou remover um privilégio e reunir mudanças numa revisão. A lista de schemas e tabelas usa os metadados já conhecidos pelo DataGrip; campos editáveis permitem informar objetos ainda não introspectados.

Revisar alterações mostra a conta, a conexão e o SQL preparado. Aplicar alterações relê o estado antes de executar; se outro processo mudou a conta ou a leitura estiver incompleta, bloqueia a aplicação. Os comandos são sequenciais, não há promessa de rollback atômico de DCL. Falhas parciais informam quantas operações foram confirmadas e recarregam o estado; não há repetição automática.

FLUSH PRIVILEGES vem marcado por padrão, conforme o fluxo solicitado. Ele é tentado depois das alterações confirmadas, inclusive quando um comando posterior falha. Falha apenas no flush não é apresentada como falha de todas as alterações. GRANT, REVOKE e comandos de contas já atualizam as permissões sem precisar desse flush; ele exige privilégios próprios no servidor. [Referência MySQL](https://dev.mysql.com/doc/refman/8.0/en/privilege-changes.html).

Novo usuário / role cria uma conta pelo formulário. Usuários usam senha aleatória do servidor, recurso disponível a partir de MySQL 8.0.18 ou equivalente Aurora; a senha aparece em uma janela e não é gravada pelo plugin. Não há campo de senha literal no histórico SQL do plugin. [Referência de senhas](https://dev.mysql.com/doc/refman/8.0/en/password-management.html).

## Consultas rápidas

No explorador de bancos, clique com o botão direito em uma tabela MySQL e abra Consultas rápidas:

- SELECT das primeiras 1000 linhas.
- SELECT com os nomes das colunas, limitado a 1000 linhas.
- Contar registros.

Essas ações abrem a consulta pronta em um novo console vinculado à conexão da tabela. Você pode executar pelo comando normal do DataGrip e editar o SQL gerado. Sem ORDER BY, as 1000 linhas não têm ordem garantida.

A aba Consultas rápidas também permite escolher conexão e tabela sem escrever SQL. Montar SELECT oferece filtro de igualdade tipado, ordenação e limite configurável, com padrão de 1000 registros. Quoting preserva barras, contrabarras e backticks em nomes; texto de filtro usa literal UTF-8 hexadecimal para não depender de NO_BACKSLASH_ESCAPES.

## Conexão e requisitos

Usa a conexão configurada no DataGrip, incluindo seu fluxo de credenciais, SSH e TLS. Cria uma sessão gerenciada para a operação e a libera ao terminar. Não faz commit ou rollback de consoles existentes. Respeita somente leitura no IDE e no servidor.

A gestão precisa de leitura de mysql.user, SHOW GRANTS e, em MySQL 8, das tabelas de roles/defaults, além dos privilégios necessários a cada mudança. Informação indisponível não é tratada como permissão vazia. MySQL 5.7 não oferece roles; capacidades são detectadas. MariaDB não é alvo de homologação desta versão.

Os statements têm timeout de 30 segundos quando suportado pelo driver. O estabelecimento inicial da conexão e o prompt de senha seguem o SDK do DataGrip. Interrupção da espera ou timeout não comprovam rollback ou cancelamento no servidor. Leituras têm limite de 10.000 linhas e 16 Mi caracteres, com erro explícito ao exceder.

Alvo de compilação e verificação: DataGrip 2026.2.6.1, build 262.11879.103, Java 25. Faixa declarada somente linha 262 a partir de 262.11879. Não declara suporte a outros IDEs ou outras linhas nesta versão.

## Pacote e pipeline

O ZIP e o SHA-256 ficam em `artifacts/datagrip` na raiz do projeto, inclusive quando o build roda numa worktree. Essa pasta é ignorada pelo Git. O pacote atual se chama `datagrip-toolkit-0.3.1.zip` e será anexado como asset da Release de homologação após a pipeline passar.

Instale no DataGrip em Settings, Plugins, engrenagem, Install Plugin from Disk. Selecione o ZIP do plugin e reinicie se solicitado. A Tool Window chama-se SQL Toolkit. O pacote inicial não tem assinatura Marketplace.

`build.ps1` usa o Gradle Wrapper 9.8.2 com checksum da distribuição, testa, verifica estrutura e empacota. Aceita LocalIdePath, JdkPath e ArtifactDirectory sem mudar a configuração global da máquina. O wrapper original inclui sua licença no JAR.

O workflow `.github/workflows/plugin.yml` roda em PR ou push para main quando o módulo muda, em tags `datagrip-v*` e por disparo manual. Testa, verifica a compatibilidade binária e entrega ZIP, checksum e relatórios. Tags compatíveis com a versão do build publicam o ZIP exato e seu checksum em uma Release de homologação. Falha em teste ou verificação impede a publicação. Não publica no Marketplace.

## Estado da entrega

Veja [docs/VALIDATION.md](docs/VALIDATION.md) para resultados e limites dos testes. Não houve acesso a MySQL/Aurora real nem instalação no DataGrip de uso diário nesta implementação. A prévia visual usa Swing com dados fictícios; no aplicativo o painel acompanha o tema do DataGrip.
