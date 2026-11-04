# SQL Toolkit

Projeto pessoal de LucasBP. Plugin DataGrip genérico, independente de produtos e credenciais corporativos.

- Toda PR nasce em worktree própria. Não publicar branch, release, pacote ou mensagem sem autorização do usuário.
- Código sem comentários. Explicações de implementação ficam em docs.
- Testar regras de permissões, falhas parciais e preservação de identidade. Nenhum teste de CI deve acessar banco ou credencial real.
- UI não executa operação de banco no EDT. Mutação exige seleção explícita de conta, revisão do plano e ação de aplicar.
- Usar conexões gerenciadas pelo DataGrip, preservando SSH/TLS e credenciais do IDE. Não ler senha de arquivos nem tocar a transação de consoles existentes.
- Comandos de contas usam tipos e quoting do domínio, nunca fragmentos SQL informados pelo usuário. Não editar mysql.user diretamente.
- FLUSH é etapa separada. Uma falha nessa etapa não autoriza repetir mutações confirmadas. Timeout exige reler estado.
- Build local: build.ps1. Testes, verifyPluginStructure, buildPlugin e verifyPlugin são os checks da pipeline.
- Pacotes e checksums ficam em artifacts/datagrip, ignorados pelo Git. Nunca versionar banco, senha, token ou resultado real.
