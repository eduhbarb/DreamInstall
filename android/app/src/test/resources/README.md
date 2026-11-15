# Synthetic test fixtures

These files are generated for automated tests and the on-device end-to-end run. They are **not**
dumps from a real console and must never be used in the app.

- `boot9.bin` — 64 KiB of deterministic random bytes, not a boot9 dump.
- `movable.sed` — 0x140 synthetic bytes, not a console movable.sed.
- `seeddb.bin` — one fake entry with the title id `11223344556677`.
- `game.cia` and `seed.cia` — tiny synthetic CIAs with encrypted test content.
- `smdh_icon.bin` — RGB565 test icon data.

The release APK does not include any of these files.
