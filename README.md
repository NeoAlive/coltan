# Coltan

Forge 1.20.1 soft-dep bridge: when **GemRender** and **Superb Warfare** are both installed, Coltan draws selected SBW entities through GemRender.

## Which jar?

Coltan is built **per SBW line**. The SBW target is in the file name:

| Branch | Output jar | Built against | `mods.toml` range |
|--------|------------|---------------|-------------------|
| `main` | `coltan-0.1.0-V0.8.9.10.jar` | SBW **0.8.10** | `[0.8.10,)` |
| `backport/sbw-0.8.9.1` | `coltan-0.1.0-V0.8.9.1.jar` | SBW **0.8.9.1** | `[0.8.9,0.8.10)` |

Do not mix: the 0.8.10 jar talks to `DefaultVehicleResource.animation` as a field; 0.8.9.1 only has `getAnimation()`.

## Dev jars (`libs/`)

Coltan compiles against local jars (not bundled). Put these files in `libs/`:

| File | Source |
|------|--------|
| `gemrender-1.20.1-0.1.0.jar` | From GemRender: `./gradlew :1.20.1:build`, then copy `versions/1.20.1/build/libs/gemrender-0.1.0.jar` and rename |
| `superbwarfare-0.8.10.jar` | **main** — SBW 0.8.10 non-`-all` jar, renamed |
| `superbwarfare-0.8.9.1.jar` | **backport branch** — from `temp/superbwarfare-0.8.9.1-hotfix-…-all.jar`, renamed |

`gradle.properties` picks the SBW dep via `sbw_dep_version` / `sbw_compat_label`.

At runtime, drop Coltan + GemRender + matching SBW into `mods/` (or `run/mods/`). Use Superb Warfare's **`-all`** jar (or its JiJ deps) plus **Kotlin for Forge 4.11+**.

## Soft dependencies

Declared optional in `mods.toml`:

- `superbwarfare` (AFTER) — version range pinned per branch
- `gemrender` (CLIENT, AFTER)

Without either mod, Coltan loads and does nothing.

## Flywheel note

GemRender jar-in-jars Flywheel **1.0.6-281**; Superb Warfare jar-in-jars Flywheel **1.0.5** (for Ponder). Both appear as JarJar candidates; Forge should pick one. If Ponder scenes break with both mods, that conflict is the first place to check.

## Verified locally

| Setup | Result |
|-------|--------|
| Coltan alone | Loads; logs `bridge inactive (missing soft dependency)` |
| Coltan + jars in `libs/` | `compileJava` / `build` succeed |
| Coltan + GemRender + matching SBW in `run/mods` | Mods discovered; SBW needs Kotlin for Forge to finish loading |
