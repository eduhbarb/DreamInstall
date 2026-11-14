# DreamInstallFinalize

Finalizador para o DreamInstaller. Roda no 3DS, instala o ticket e a seed dos títulos que o
app Android gravou no cartão e mostra o resultado na tela.

Baseado no [custom-install-finalize](https://github.com/ihaveamac/custom-install/tree/python-package/finalize)
(MIT, Ian Burgwin).

## O que faz

- Lê `/cifinish.bin` (versões 1, 2 e 3).
- Consulta os títulos e tickets instalados no SD.
- Instala o ticket que falta com `am:net` e a seed com `FSUSER_AddSeed`.
- Valida de novo depois de instalar: só marca como finalizado o que tem ticket e título.
- Apaga `/cifinish.bin` e `/dreamfinalize-names.txt` quando todos os títulos ficam válidos.
- Mostra nome, title ID e estado de cada título; **START** (ou **B**) sai para o menu.

`/dreamfinalize-names.txt` é escrito pelo app Android no formato `id<TAB>nome`, uma linha por
título. Sem ele, a tela mostra só o title ID.

## Build

Precisa de devkitPro com `3ds-dev`, makerom e bannertool.

```sh
make
bannertool makebanner -i meta/banner.png -a meta/audio.wav -o banner.bin
makerom -f cia -o DreamInstallFinalize.cia -target t -elf DreamInstallFinalize.elf \
  -rsf meta/DreamInstallFinalize.rsf -icon DreamInstallFinalize.smdh \
  -banner banner.bin -exefslogo
```

O workflow `.github/workflows/3ds.yml` faz o mesmo no container `devkitpro/devkitarm`.

## Arquivos gerados

```
DreamInstallFinalize.3dsx   roda pelo Homebrew Launcher
DreamInstallFinalize.cia    instala pelo FBI se quiser ícone na HOME Menu
```

O DreamInstaller copia os dois para `/3ds` e `/cias` no cartão quando faltam ou estão
desatualizados.

## Licença

MIT. Veja `../../android/LICENSE` e `../../LICENSE.md`.
