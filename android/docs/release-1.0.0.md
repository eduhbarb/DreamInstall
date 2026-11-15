# DreamInstall 1.0.0

Primeira versão do instalador de títulos do 3DS para Android.

## O que faz

- Instala `.cia` direto no cartão SD, sem rede e sem passar pelo FBI.
- Fila com nome, ícone, título, versão, tamanho e estado.
- Verificação SHA-256 de cada conteúdo.
- Progresso por título e por lote, velocidade, tempo restante, log e cancelamento seguro.
- Atualiza o `title.db` com o `save3ds` (arm64) e faz backup antes em `/ci-backup/<data>/`.
- Grava `cifinish.bin` v3 para o `custom-install-finalize` no 3DS.
- Copia o `custom-install-finalize.3dsx` para `/3ds`.
- Tema com oito acentos, pt-BR e inglês.
- Sem permissão de internet.

## Requisitos

- Android 11+, arm64.
- `boot9.bin` completo (64 KiB), `movable.sed` e, de preferência, `seeddb.bin`.
- Leitor de cartão USB-C/OTG.

## Instalação

1. Baixe o APK abaixo.
2. Conceda **Acesso a todos os arquivos** na primeira abertura.
3. Adicione as chaves, escolha o cartão SD e instale.
4. No 3DS, rode **custom-install-finalize** pelo Homebrew Launcher.

O APK é assinado com a chave de desenvolvimento local. Em MIUI/HyperOS pode ser necessário
liberar **Instalar via USB** ou abrir o APK pelo gerenciador de arquivos.

## Limites conhecidos

- O ticket fica no NAND: o passo no 3DS continua obrigatório.
- Só `arm64-v8a`; Android 11+; sideload.
- DSiWare não é suportado.
- Sem download embutido.

## Validação

Instalação comparada com o `custom-install` original no PC usando os mesmos fixtures
sintéticos (um título com seed, exheader/ExeFS criptografados):

- 9 de 9 arquivos gerados idênticos byte a byte.
- Entradas do `title.db` idênticas.
- `cifinish.bin` idêntico.
- 10 testes JVM de vetores dourados e parser passando.
- Teste instrumentado no aparelho passando.

## Créditos

custom-install (MIT, Ian Burgwin), save3ds (MIT/Apache-2.0, Weiyi Wang), pyctr (MIT, Ian
Burgwin), Fredoka e Hanken Grotesk (SIL OFL 1.1).

Não afiliado à Nintendo. Use apenas conteúdo que você tem o direito de usar.
