# Coltan

Forge 1.20.1 soft-dep bridge: when **GemRender** and **Superb Warfare** are both installed, Coltan draws selected SBW vehicles and military armor through GemRender.

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

## What is bridged

| Path | GemRender API | Scope |
|------|---------------|--------|
| Vehicles | Flywheel + rigid parts (`SbwVehicleGemVisual`) | Discovered SBW vehicle types |
| Armor | DirectRenderer (`GemRenderArmorModel`) | RU/US/GE helmets & chests (not Handsome Goggles) |

## Soft-compat: tacz_sewv paint

- `ColtanVehicleSkins.setResolver(...)` — sticky faction hull paint after `skipVanillaRender` bypasses `GeoVehicleRenderer`
- `ColtanArmorSkins.setResolver(...)` — faction crew armor paint after Coltan bypasses `GeoArmorRendererV2`

tacz_sewv registers both when Coltan is present. Without Coltan, SEWV keeps its Geo mixins.

## Per-frame cost (vehicles, GemRender §4)

`SbwVehicleGemVisual` buckets each animation-layer parameter (coarser via `PoseLod` at distance) and calls `setChanged()` only on dirty parts. A parked / idle AI-crewed hull early-outs instead of re-uploading every part every frame. Hide/zoom transitions, LOD swaps, and texture overrides force a full dirty pass.

Armor uses GemRender's DirectRenderer batching: many wearers of the same piece share one draw.

## GemRender patches (mixins)

Coltan does **not** ship a fork of GemRender. Client mixins (see `coltan.mixins.json`) fix things Coltan needs:

- **poly_mesh UV V flip** — Bedrock UVs are top-left; without the flip, cutout turret shells sample empty texels and vanish
- **SBW armor `initializeClient`** — five military pieces return `GemRenderArmorModel` instead of `GeoArmorRendererV2`

If GemRender later ships the UV fix upstream, remove or gate that mixin to avoid a double flip.

## Flywheel note

GemRender jar-in-jars Flywheel **1.0.6-281**; Superb Warfare jar-in-jars Flywheel **1.0.5** (for Ponder). Both appear as JarJar candidates; Forge should pick one. If Ponder scenes break with both mods, that conflict is the first place to check.

## Verified locally

| Setup | Result |
|-------|--------|
| Coltan alone | Loads; logs `bridge inactive (missing soft dependency)` |
| Coltan + jars in `libs/` | `compileJava` / `build` succeed |
| Coltan + GemRender + matching SBW in `run/mods` | Mods discovered; SBW needs Kotlin for Forge to finish loading |

### Soft-compat check matrix (with tacz_sewv)

| Install | Expect |
|---------|--------|
| SEWV alone | Unchanged Geo path + skins |
| SEWV + Komodo | Existing dormancy compat (`MixinKmodoDormancy`) |
| SEWV + Coltan + GemRender + SBW | Vehicle + armor bridges active; sticky paint via `ColtanVehicleSkins` / `ColtanArmorSkins` |
| SEWV + Coltan without GemRender | Coltan inactive; SEWV unchanged |
| SEWV + GemRender without Coltan | No Coltan bridge; SEWV Geo path unchanged |

Prefer **either** Coltan+GemRender **or** Komodo for vehicle acceleration until coexistence is playtested.
Known Geo-only gaps under Coltan: dogTag icon overlay force, rappel wires.
