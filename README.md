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
| `simpleenemymod-1.20.1-0.1.6-beta.jar` | SEM build output (compileOnly for unit bridge) |
| `tacz-1.0.jar` | TaCZ (compileOnly for SEM gun overlay APIs) |
| `simplebedrockmodel-2.5.1.jar` | SBW's bundled bone runtime: `META-INF/jarjar/simplebedrockmodel-2.5.1-forge-mc1.20.1.jar` inside the SBW `-all` jar, renamed (vehicle hook replay) |
| `mae-1.1.4.jar` | `META-INF/jarjar/mae-1.1.4.jar` inside that simplebedrockmodel jar (animation pose blender) |

`gradle.properties` picks the SBW dep via `sbw_dep_version` / `sbw_compat_label`.

At runtime, drop Coltan + GemRender + matching SBW into `mods/` (or `run/mods/`). Use Superb Warfare's **`-all`** jar (or its JiJ deps) plus **Kotlin for Forge 4.11+**.

## Soft dependencies

Declared optional in `mods.toml`:

- `superbwarfare` (AFTER) — version range pinned per branch
- `gemrender` (CLIENT, AFTER)
- `simpleenemymod` (AFTER) — SEM unit bridge when GemRender is present

Without GemRender + SBW, the SBW bridge stays inactive. Without GemRender + SEM, the SEM unit bridge stays inactive.

## Client debug flags

Silent-failure / catalog diagnostics (off by default). Enable without restart:

1. Create `config/coltan/debug.txt` with one line, e.g. `all` or `vehicle,lod,gun,fail`
2. Or JVM `-Dcoltan.debug=all` / env `COLTAN_DEBUG=all`

Categories: `boot` `vehicle` `lod` `gun` `armor` `projectile` `munition` `block` `particle` `skin` `claim` `cache` `fail`

Logging is spam-safe: **once** per key, **whenChanged** (LOD swaps / skins), **every 5s** counters (particle diverts). Any enabled category also surfaces `[coltan:fail]` one-shots. Example file: `src/main/resources/coltan-debug.txt.example`.

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

## Soft-compat: tacz_sewv + SEM

- `ColtanVehicleSkins.setResolver(...)` — sticky faction hull paint after `skipVanillaRender` bypasses `GeoVehicleRenderer`
- `ColtanArmorSkins.setResolver(...)` — faction crew armor paint after Coltan bypasses `GeoArmorRendererV2`

tacz_sewv registers paint/armor bridges only when **`ColtanCompat.bridgeActive()`** (coltan **and** gemrender). Without that stack, SEWV keeps Geo mixins.

### SEM units (GemRender)

When **Coltan + GemRender + SimpleEnemyMod** are loaded, `SemGemCompat` claims `usunit` / `ruunit` / `pmcunit` via Flywheel `skipVanillaRender` and draws each unit as six rigid Flywheel instances (head/body/arms/legs, `SemUnitRigidVisual`). There is no Bedrock geo and no mesh/coordinate conversion at all: `SemUnitPartMesh` builds each part's mesh directly from SEM's own vanilla cuboid data (`UnitModelDefinitions`, reproducing `ModelPart.Cube`'s box-UV layout exactly — see `VanillaCuboidMesh`), and every frame `SemUnitShadowPose` drives SEM's real `setupAnim` on a hidden shadow model of the entity's own type and reads each part's transform straight off vanilla's own `ModelPart.translateAndRotate`. That transform is copied directly onto that part's Flywheel instance — nothing ever leaves vanilla's coordinate space, so there's no format-conversion step for a pivot/rotation bug to hide in (unlike the earlier Bedrock-geo attempt, which never got the coordinate round-trip right). Locomotion/hurt/death/aim always match stock SEM exactly, since it's SEM's real code running. TaCZ guns are reattached by `SemUnitGunOverlay` off the `rightArm` part's world matrix. PMC armor / Gecko layers are **not** bridged in v1. Without GemRender, SEM keeps its stock `MobRenderer` path.

Trade-off: each unit's pose is genuinely unique per-instance CPU work (like vanilla always required) and each body part is its own rigid mesh (no seamless single skin), so there's no shared-pose-cache trick here — the win is Flywheel's static GPU-resident meshes + batched instanced draws over vanilla's per-frame immediate-mode rebuild, not shared pose evaluation.

### Mortar

SBW’s mortar yaws the hull only and elevates `move_paoguan` / bipod `move_jiaojia`. Coltan override: `assets/coltan/sbw_bridge/superbwarfare/mortar.json` (`hullAxis: yawOnly` + mortar drivers).

### Seat-aimed bound bones and powered loop clips

- **BoundBones / BoundBonesYaw / BoundBonesPitch** follow the *seat's own aim*, exactly like SBW's `GeoVehicleRenderer` (delta between `getShootVec(seat)` and the weapon's `ShootPos.DefaultBarrelDirection`, applied while that seat is occupied). Any number of independently aimed hull mounts work; they no longer depend on the passenger-weapon-station angles. Turret/barrel and `passengerWeaponStation*` bones are unchanged.
- **`loopClips`** in a vehicle override (`assets/coltan/sbw_bridge/<ns>/<id>.json`, e.g. `{"loopClips": ["animation.k_130.radar"]}`) loops those animation clips while `getEnergy() > 0` and the hull is not a wreck. SBW plays such clips from entity code through `VehicleAnimationContext`, which the bridge never sees. The clip clock freezes when power drops, so a radar stops in place instead of snapping back.

### Vehicle recoil, flares and ambient clips

- **Hull recoil:** the `base` bone is shaken from `recoilShake` / `yawWhileShoot` exactly like SBW's `GeoVehicleRenderer` (driven by a weapon's `RecoilTime` / `RecoilForce`).
- **Barrel recoil and flare pulses:** `animation.<weapon>.fire` / `.idle` clips (weapon key camelCase → snake_case) are layered over the pose, so any bone they animate (e.g. a sliding `barrel_action`) moves.
- **Muzzle flares:** bones named `flare*` are drawn by `SbwVehicleFlare` as an emissive overlay (SBW's `muzzle_flare` disc + three blades, `textures/particle/flare.png`) at the bone's live pose. Visibility and size come only from the bone's animated scale, so an `.idle` clip must key them to scale 0.
- **Per-seat bound bones and looping clips:** see `BoundBones*` aiming and the `loopClips` override key (`assets/coltan/sbw_bridge/<ns>/<id>.json`, plays while the vehicle has energy).

### Addon vehicles: render modes

Every SBW vehicle type (stock or from an addon pack) gets one of three modes, chosen from its renderer when the catalog is built (the `vehicle` debug category logs each choice and why):

| Mode | When | What draws it |
|------|------|---------------|
| `native` | Stock pose hooks (SBW's own renderers, or addons that only change track curves / zoom hiding) | Coltan's procedural layers on GemRender (cheapest) |
| `replay` | The renderer overrides `transformCustomModelPart` / `tickVariables` outside SBW, or the vehicle has an SBW transform script | SBW's own pose pipeline (animation blend, `tickVariables`, `transformCustomModelPart`) runs on the entity's model instance; every bone is copied into the GemRender pose. Only SBW's vertex submission is skipped. Every bone becomes its own GemRender part. |
| `passthrough` | Not a `GeoVehicleRenderer` (e.g. GeckoLib `VehicleRenderer` packs), or overrides a draw-path hook (`render`, `renderEmissive`, `renderCustomPart`, `customLaserLength`, `rotateVehicleAxis`, `getCurrentModelEntry`) | The vehicle's own renderer, untouched |

A pack can force a mode with `"renderMode": "native" | "replay" | "passthrough"` (or `"auto"`) in `assets/coltan/sbw_bridge/<ns>/<id>.json`. That file is plain data, so it costs nothing when Coltan isn't installed. In replay mode, entity-driven animations (radars, hatches) play from SBW itself, so `loopClips` / `stateClips` are only used by native mode. If a replay throws, that vehicle logs once and falls back to native layers.

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
| SEWV + Coltan + GemRender + SBW | Vehicle + armor + gun + BER + projectile + munition + particles; sticky paint |
| SEWV + Coltan without GemRender | Coltan inactive; SEWV unchanged (`bridgeActive()` false) |
| SEWV + GemRender without Coltan | No Coltan bridge; SEWV Geo path unchanged |
| SEM units holding TACZ (no GemRender) | Stock SEM `GunLayerRenderer` + TACZ BEWLR |
| SEM + Coltan + GemRender | GemRender unit body (rigid parts, shadow-pose synced) + Coltan gun overlay; PMC armor layers skipped |
| SEM + Coltan without GemRender | SEM bridge inactive; stock SEM renderers |

Prefer **either** Coltan+GemRender **or** Komodo for vehicle acceleration until coexistence is playtested.
Known Geo-only gaps under Coltan: dogTag icon overlay force, rappel wires.
