# Coltan

Forge 1.20.1 soft-dep bridge: when **GemRender** and **Superb Warfare** are both installed, Coltan draws selected SBW entities through GemRender (v1: `superbwarfare:bmp_2`).

## Dev jars (`libs/`)

Coltan compiles against local jars (not bundled). Put these files in `libs/`:

| File | Source |
|------|--------|
| `gemrender-1.20.1-0.1.0.jar` | From GemRender: `./gradlew :1.20.1:build`, then copy `versions/1.20.1/build/libs/gemrender-0.1.0.jar` and rename to `gemrender-1.20.1-0.1.0.jar` |
| `superbwarfare-0.8.10.jar` | From Superb Warfare: `./gradlew build`, then copy the non-`-all` jar from `build/libs/` and rename to `superbwarfare-0.8.10.jar` |

At runtime, drop the same mods into the client `mods/` folder (or this project's `run/mods/`). Use Superb Warfare's **`-all`** jar (or its JiJ deps) plus **Kotlin for Forge 4.11+** — SBW will not load without that language provider.

## Soft dependencies

Declared optional in `mods.toml`:

- `superbwarfare` (AFTER)
- `gemrender` (CLIENT, AFTER)

Without either mod, Coltan loads and does nothing.

## Flywheel note

GemRender jar-in-jars Flywheel **1.0.6-281**; Superb Warfare jar-in-jars Flywheel **1.0.5** (for Ponder). Both appear as JarJar candidates; Forge should pick one. If Ponder scenes break with both mods, that conflict is the first place to check.

## Verified locally

| Setup | Result |
|-------|--------|
| Coltan alone | Loads; logs `bridge inactive (missing soft dependency)` |
| Coltan + jars in `libs/` | `compileJava` / `build` succeed |
| Coltan + GemRender + SBW `-all` in `run/mods` | Mods discovered; SBW needs Kotlin for Forge installed separately to finish loading |
