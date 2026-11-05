# Validação Server Management 0.3.3

Executada localmente em 2026-11-04 contra DataGrip 2026.2.6.1, build 262.11879.103, Java 25, Gradle 9.8.2 e IntelliJ Platform Gradle Plugin 2.19.0.

94 testes passaram, zero falhas. Mantém a cobertura de contas, roles, quoting, revisão, aplicação e FLUSH; acrescenta resolução de contexto do explorador/console, exigindo escolha quando os identificadores divergem ou não há contexto. A abertura contextual continua limitada ao identificador escolhido, sem fallback após remoção da conexão. Abrir não faz mutações.

A gestão usa FileEditorProvider e uma aba central por conexão, com reaproveitamento do arquivo virtual. O seletor inicial também é uma aba central. Usuários, Consultas rápidas e Montar SELECT ficam vinculados à mesma conexão. Fechar o editor cancela os workers locais. Consoles SQL existentes não são fechados, modificados ou executados por abrir a gestão. Services é recolhido enquanto a gestão está selecionada e restaurado ao sair somente quando estava visível antes.

A interface e o pacote usam Server Management, com ícone SVG de servidor e engrenagem nos temas claro/escuro, incluindo o menu e a aba do editor. O plugin ID permanece io.github.lucasbp.datagrip-toolkit, preservando a atualização da instalação anterior. ZIP atual: server-management-0.3.3.zip.

Build e verificação estrutural passaram. Plugin Verifier confirmou Compatible sem APIs depreciadas, internas ou experimentais após substituir o listener inicial por HierarchyListener do Swing.

Em instância isolada do DataGrip, projeto vazio e sem credenciais, foram observados: botão da barra abrindo seletor na área central; nome e ícone no menu de janelas; menu abrindo diretamente a aba central; recolhimento de Services ao reabrir gestão; restauração ao fechar a aba. Não se usou um banco real. O fluxo completo de contas nessa nova aba foi validado pelos testes com backend fictício, sem uma sessão MySQL real no IDE.

A pipeline foi ajustada para o nome novo do ZIP, mantendo tags datagrip-v*. ZIP local e checksum em artifacts/datagrip na raiz principal, ignorados pelo Git. A versão 0.3.3 ainda não foi publicada no GitHub nesta validação.

A homologação de permissões reais, SSH/TLS e diferenças dos servidores segue pendente. O IDE de uso diário não foi alterado. A instância isolada foi encerrada.
