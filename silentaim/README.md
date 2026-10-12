# Silent Aim (standalone)

A standalone Fabric client mod that aims at the closest valid target **server-side** without moving
your camera. It is self-contained: it does not depend on the Echo client, its `RotationUtils`, its
`RotationHandler` or any of its shared state.

| | |
|---|---|
| Minecraft | 1.21.1 (Fabric) |
| Mod version | `1.21` |
| Loader | Fabric Loader 0.16.9+ |
| Requires | Fabric API |
| Environment | client only |

## Build

```bash
cd silentaim
./gradlew build
```

The mod jar is written to `build/libs/silentaim-1.21.jar`. Drop it (plus Fabric API) into `.minecraft/mods`.

## Usage

- Press **R** (default) to toggle. A small HUD line in the top-left shows the state and current target.
- Settings are persisted to `config/silentaim.json` — enable the mod once in-game to generate it, then edit:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Master switch (keybind writes this). |
| `silent` | `true` | `true` = rotation only reaches the server, camera stays put. `false` = visible aim assist. |
| `targetPlayers` | `true` | Include other players. |
| `targetMobs` | `false` | Include hostile/passive mobs. |
| `throughWalls` | `false` | Ignore line of sight when picking and hitting targets. |
| `showHud` | `true` | Draw the HUD indicator. |
| `randomize` | `false` | Add a small random offset before smoothing. |
| `randomJitter` | `0.35` | Size of that offset in degrees. |
| `range` | `4.5` | Maximum target distance in blocks. |
| `fov` | `180.0` | Full cone around the crosshair targets must be inside, in degrees. |
| `speed` | `180.0` | Rotation smoothing, degrees per tick. `180` is effectively instant. |

## How it works

The mod keeps its own "server rotation" and only swaps it in while the outgoing movement packets
are being built, so the server sees the aim while the camera, hitbox raycast and rendering keep
using the rotation you actually have:

- `SilentAim` — holds the target and the silent yaw/pitch, does the smoothing.
- `aim/TargetSelector` — range + FOV + optional line-of-sight filtering, scores candidates by
  angular offset from the crosshair.
- `aim/RotationMath` — yaw/pitch calculation and shortest-path angle stepping.
- `mixin/ClientPlayerEntityMixin` — applies the silent rotation at the head of
  `sendMovementPackets` and restores the real one at the tail.
- `mixin/MinecraftClientMixin` — right before `doAttack`, pushes a look packet with the silent
  rotation and re-aims the crosshair at the selected target so the hit is sent.

## Notes

- Targets are scanned with `World#getEntitiesByClass` around the player, so this stays cheap.
- Line of sight is a single `RaycastContext` block raycast from the eyes to the target.
- Creative/spectator players and dead or invisible entities are skipped.
