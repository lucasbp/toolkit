# Toolkit

Ferramentas pessoais de LucasBP, organizadas por aplicativo ou domínio.

## Módulos

| Pasta | Ferramenta |
|---|---|
| [datagrip](datagrip/README.md) | Server Management: gestão visual MySQL/Aurora e consultas rápidas no DataGrip |

Cada módulo tem build, testes e pacote próprios. O primeiro módulo é distribuído como ZIP instalável em [Releases](https://github.com/lucasbp/toolkit/releases). Para instalar, baixe o arquivo `server-management-<versão>.zip` da Release, sem extrair, e use Install Plugin from Disk no DataGrip. Os arquivos Source code gerados pelo GitHub não são o plugin.

Os pacotes locais ficam em `artifacts/<módulo>`, ignorados pelo Git. As Releases do DataGrip usam tags `datagrip-v<versão>` e publicam o ZIP exato e seu SHA-256.

As versões iniciais são distribuídas como pré-lançamentos enquanto a homologação interativa ainda está pendente. Build, testes automatizados e verificação binária não substituem o teste em banco real. Consulte os limites e resultados no README de cada módulo.
