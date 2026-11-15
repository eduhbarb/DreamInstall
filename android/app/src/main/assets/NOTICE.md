# Third-party notices

DreamInstall ports the install pipeline of **custom-install** by Ian Burgwin (MIT).
Full text: `app/src/main/assets/licenses/custom-install-LICENSE.md`.

The bundled **DreamInstallFinalize** homebrew is based on
**custom-install-finalize** by Ian Burgwin (MIT), from the same project. Source and build are
in `../../3ds/DreamInstallFinalize`.

Title database access uses **save3ds** by Weiyi Wang, built without FUSE for arm64 Android
(MIT OR Apache-2.0). Full texts:
`app/src/main/assets/licenses/save3ds-LICENSE-MIT` and
`app/src/main/assets/licenses/save3ds-LICENSE-APACHE`.

Crypto and file format behaviour was implemented from the **pyctr** reference by Ian Burgwin
(MIT). Full text: `app/src/main/assets/licenses/pyctr-LICENSE`.

Bundled fonts:

- **Fredoka** (SIL Open Font License 1.1): `app/src/main/assets/licenses/Fredoka-OFL.txt`
- **Hanken Grotesk** (SIL Open Font License 1.1): `app/src/main/assets/licenses/HankenGrotesk-OFL.txt`

The visual language is inspired by **DreamFTP** by Synaelle (AGPL-3.0). No DreamFTP source
code is included in this repository.

The `DreamInstallFinalize.3dsx` and `DreamInstallFinalize.cia` binaries distributed in
`app/src/main/assets` are built from `3ds/DreamInstallFinalize` and inherit the MIT license.
