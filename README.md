# Coltan

Forge 1.20.1 soft-dep bridge: when **GemRender** and **Superb Warfare** are both installed, Coltan draws selected SBW vehicles, military armor, guns, Bedrock BER blocks, projectiles, munition items, and high-volume soft particles through GemRender.

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
| Vehicles | Flywheel + rigid parts (`SbwVehicleGemVisual`) | Discovered SBW vehicle types (incl. piloted drone via nested `Model`) |
| Armor | DirectRenderer (`GemRenderArmorModel`) | RU/US/GE helmets & chests (not Handsome Goggles) |
| Guns | DirectRenderer (`GemRenderItemRenderer`) | Claim-all SBW `GunGeoItem`s (except `_exclude.json`); sampled assets + FP ADS/recoil/scope from probe/seed |
| BER blocks | Flywheel skinned (`SbwBlockGemVisual`) | Containers, FuMO-25, vehicle assembling & blueprint research tables |
| Projectiles | Flywheel skinned (`SbwProjectileGemVisual` / `GemRenderEntityVisual`) | Entity types with `models/bedrock/projectile/{path}.geo.json` (missiles, rockets, bombs, mines, swarm drone); tracers excluded |
| Munitions | DirectRenderer (`SbwMunitionItemRenderer`) | Hand grenade, TM-62, PTKM-1R held items |
| Particles | `ParticleEmitter` / `ParticlePool` (`SbwParticleBridge`) | Diverted SBW soft sheets: CustomFlare, CustomCloud, CannonMuzzleFlare, CustomSmoke |

Projectile exclude list: `assets/coltan/sbw_projectile_bridge/_exclude.json` (default: tracer shells). Vehicle / gun / block excludes stay under their existing `sbw_*_bridge/_exclude.json` paths.

### Particle bridge

When GemRender + SBW are loaded, `SbwClientLevelParticleMixin` cancels ParticleEngine adds for the four soft-sheet option types and spawns once into GemRender emitters (no double draw). Covers missile/rocket trails, explosion soft flares/clouds, vehicle dust, shell trails, cannon muzzle flashes, and M18/smoke-decoy CustomSmoke.

Left on ParticleEngine / Immediate: vanilla types (`EXPLOSION`, campfire smoke, …), `FIRE_STAR` / bullet decals, FlareDecoy camera billboard, gun/projectile mesh flare quads (`SbwGunFlare` / `SbwProjectileFlare`).

### Static blocks stay chunk-meshed

`dragon_teeth` and other Forge-OBJ / vanilla JSON cube blocks are **not** GemRender targets. Flywheel has no chunk-mesh instancing path, so those keep the vanilla/Forge baked model. Converting an OBJ decoration to JSON outside Coltan is optional content work, not a GemRender bridge.

Permanent mesh skips: melon bomb (vanilla block), FlareDecoy billboard entity renderer, plain 2D ammo items. Projectile flare emissive eyes-pass is drawn by `SbwProjectileFlare`.

## Soft-compat: tacz_sewv paint

- `ColtanVehicleSkins.setResolver(...)` — sticky faction hull paint after `skipVanillaRender` bypasses `GeoVehicleRenderer`
- `ColtanArmorSkins.setResolver(...)` — faction crew armor paint after Coltan bypasses `GeoArmorRendererV2`

tacz_sewv registers both when Coltan is present. Without Coltan, SEWV keeps its Geo mixins.

## Per-frame cost (vehicles, GemRender §4)

`SbwVehicleGemVisual` buckets each animation-layer parameter (coarser via `PoseLod` at distance) and calls `setChanged()` only on dirty parts. A parked / idle AI-crewed hull early-outs instead of re-uploading every part every frame. Hide/zoom transitions, LOD swaps, and texture overrides force a full dirty pass.

Vehicle mesh LOD is general (not BMP-2-only): `RendererSampler` builds tiers from every `Models[]` row plus any `models/bedrock/vehicle_lod/{id}.lodN.geo.json` on disk, fills missing `LODDistance` with `32 * N`, and `lodIndexForDistance` picks the **highest** tier past SBW's `vehicle_lod_distance` gate. Hulls with no LOD assets (most tanks) stay on the full mesh — that is missing SBW content, not a Coltan special-case.

Armor uses GemRender's DirectRenderer batching: many wearers of the same piece share one draw.

## GemRender patches (mixins)

Coltan does **not** ship a fork of GemRender. Client mixins (see `coltan.mixins.json`) fix things Coltan needs:

- **SBW armor `initializeClient`** — five military pieces return `GemRenderArmorModel` instead of `GeoArmorRendererV2`
- **SBW gun `getClientExtensions`** — claimed `GunGeoItem`s return `GemRenderItemRenderer` instead of GeckoLib `CustomGunRenderer`
- **SBW munition `initializeClient`** — hand grenade / TM-62 / PTKM-1R return GemRender BEWLR instead of SBW item renderers
- **SBW particle divert** — `ClientLevel.addParticle` cancels CustomFlare/Cloud/Smoke/CannonMuzzle and feeds `SbwParticleBridge`

Poly_mesh UV V-flip for Bedrock shells lives in **GemRender** (`BedrockPolyMesh`); rebuild and copy `libs/gemrender-*.jar` after that change or turrets/noses stay blank.

## Gun bridge (claim-all)

Every Superb Warfare `GunGeoItem` (except `EmptyGunItem` and entries in `_exclude.json`) is claimed at client init. Tier A samples `GunResource` assets + geo bones; Tier B (`GunFpProbe`) enriches ADS / recoil / scope maps via ASM scrape of classic renderers/models, then seed fallback (`GunAdsProfile`), then optional overrides.

| | Path |
|--|------|
| Disk cache | `config/coltan/bridge-cache/gun/{namespace}/{path}.json` |
| Exclude list | `assets/coltan/sbw_gun_bridge/_exclude.json` |
| Optional overrides | `assets/coltan/sbw_gun_bridge/{namespace}/{path}.json` — `ads` seed name (`ak47` / `m4Family` / `hk416` / `glockLike`), numeric ADS/recoil fields, `scopeCrosshair` / `scopeZoomHide` / `scopeAds` |
| Legacy ads map | `assets/coltan/sbw_gun_bridge/_phase2.json` — `ads` field only (allowlist retired) |

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
| SEWV + Coltan + GemRender + SBW | Vehicle + armor + gun + BER block + projectile + munition + soft-particle bridges active; sticky paint via `ColtanVehicleSkins` / `ColtanArmorSkins` |
| SEWV + Coltan without GemRender | Coltan inactive; SEWV unchanged |
| SEWV + GemRender without Coltan | No Coltan bridge; SEWV Geo path unchanged |

Prefer **either** Coltan+GemRender **or** Komodo for vehicle acceleration until coexistence is playtested.
Known Geo-only gaps under Coltan: dogTag icon overlay force, rappel wires.
