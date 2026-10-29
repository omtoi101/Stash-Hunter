# Stash-Hunter

A [Meteor Client](https://meteorclient.com/) addon that finds stashes on anarchy servers for you. It flies an elytra over an area you choose, scans the loaded chunks for clusters of containers and other base blocks, and reports what it finds to Discord and as Meteor waypoints.

It can also follow trails of newly generated chunks toward player activity, repair its elytra with XP bottles mid-flight, and recover from rubber-banding and sudden altitude loss, so it can run for hours without you.

Built for **Minecraft 26.2**. Licensed under the Apache License 2.0.

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [How it works](#how-it-works)
- [Commands](#commands)
- [Modules and settings](#modules-and-settings)
- [Discord notifications](#discord-notifications)
- [HUD](#hud)
- [Files it creates](#files-it-creates)
- [Troubleshooting](#troubleshooting)
- [Building from source](#building-from-source)

## Requirements

- Minecraft **26.2** with [Fabric Loader](https://fabricmc.net/)
- [Meteor Client](https://meteorclient.com/) for 26.2
- Java **25**
- *(Optional)* [Baritone](https://github.com/MeteorDevelopment/baritone) for 26.2 (the Meteor fork), for obstacle-aware pathfinding

Fabric API is **not** required.

## Installation

1. Download the latest `.jar` from the [Releases](https://github.com/omtoi101/Stash-Hunter/releases) page.
2. Put it into your `mods` folder next to Meteor Client.
3. *(Optional)* Add the Baritone mod as well. Without it, Stash-Hunter uses its built-in flight controller.
4. Start Minecraft with Fabric. The modules appear in Meteor's **Stash-Hunter** category.

## Quick start

1. **Equip an elytra.** The flight refuses to start without one.
2. **Optional:** Enable `auto-elytra-repair` for long runs. It needs a **Mending** elytra, **XP bottles** in your inventory and blocks for Meteor's Scaffold module (see [Auto Elytra Repair](#auto-elytra-repair)).
3. **Enable `NewerNewChunks`** if you want the bot to follow chunk trails.
4. Paste your **Discord webhook URL** into the `stash-hunter` module's `discord-webhook-url` setting. You can skip this step; without a URL, finds still show up in chat and as waypoints.
5. Get airborne and start scanning, for example a 20,000 × 20,000 area centred on you:

   ```
   .sh start ~-10000 ~-10000 ~10000 ~10000 200
   ```

The command enables the `stash-hunter` module and Meteor's ElytraFly if they are off. Use `.sh status` to check progress and `.sh pause` / `.sh resume` to take a break.

## How it works

**Flight pattern.** The area is split into north-south strips (`stripWidth` blocks apart). The bot flies them back and forth at `flight-altitude`, like a lawnmower. Steering is done by Baritone's elytra pathing when it is installed and `use-baritone-pathing` is on, otherwise by the built-in controller.

**Chunk trails.** While `NewerNewChunks` is active, the bot looks at the new chunks around it:

- If they form a line or a consistent direction (a likely player trail), it follows that trail for up to 30 seconds.
- A single new chunk straight ahead without a clear trail is avoided.
- At the edge of unloaded terrain, it follows the boundary.

After each detour it returns to its strips.

**Scanning.** Every `scan-interval` ticks the addon scans a cylinder of `scan-radius` blocks around the player over the **full world height**, so stashes far below the flight altitude are found too.

- **Storage-only mode (default):** looks for chests, trapped chests, barrels and shulker boxes of every colour.
- **Full mode:** also counts furnaces, blast furnaces, smokers, brewing stands, enchanting tables, anvils, crafting tables, beacons, conduits, hoppers, dispensers, droppers and beds.

**Clustering and filtering.**

- Found blocks are grouped into clusters. Neighbouring blocks may be up to `max-cluster-distance` apart, and one cluster is at most `max-cluster-size` across.
- A cluster counts as a stash if it has at least `block-detection-threshold` blocks and passes the filters: `max-volume-threshold`, `min-density-threshold` and, if enabled, `filter-natural-structures`.
- These filters are heuristics. Structure loot (dungeons, mineshafts, villages) can still be reported now and then.

**Reporting.**

- Each stash is reported once. Another cluster within `report-dedupe-radius` of a reported stash, or of an existing stash waypoint, is not reported again.
- If the stash's density reaches `notification-density-threshold`, you get a Discord message and a Meteor waypoint named `<count> Blocks at <x, y, z>` in the current dimension. Lower-density finds are only mentioned in chat.

**Staying alive.** The flight is paused automatically (and saved, so `.sh resume` continues it) when you die, change dimension or leave the server. If the elytra breaks or goes missing and isn't replaced or repaired in time, the bot sends an "Out of Elytras" notification and disconnects, instead of falling.

## Commands

`.stashhunter` can be shortened to `.sh`.

| Command | What it does |
| --- | --- |
| `.sh start <x1> <z1> <x2> <z2> [stripWidth]` | Scans the rectangle between the two corners. |
| `.sh stop` | Stops the current flight. |
| `.sh pause` | Stops the flight and saves it as a trip. |
| `.sh resume [timestamp]` | Continues the latest saved trip, or the one with the given timestamp. |
| `.sh list` | Lists the timestamps of all saved trips. |
| `.sh status` | Shows the status, the current waypoint and the current target. |
| `.sh repair` | Shows the Auto Elytra Repair state and your elytra's durability. |
| `.sh repair toggle` | Turns Auto Elytra Repair on or off. |
| `.sh help` | Prints a short command reference in chat. |
| `.clear-stashes` | Forgets the already reported stashes, so they can be reported again. Existing stash waypoints still prevent a repeat report until you delete them. |
| `.clear-players` | Forgets the recently reported players, so they are reported again. |

### `start` in detail

- **Corners** can be given in three forms:
  - **Absolute:** `1000 5000`
  - **Relative to your position:** `~ ~`
  - **Relative with an offset:** `~-5000 ~2500`
- **Area size:** each side must be between 100 and 50,000 blocks.
- **`stripWidth`:** must be between 50 and 500. The default is 128. Smaller strips leave fewer gaps but take longer.

```
.sh start 1000 1000 5000 5000 150          # absolute coordinates
.sh start ~ ~ ~4000 ~4000 150               # from your position towards +X/+Z
.sh start ~-2000 ~-2000 ~2000 ~2000 120     # 4,000 × 4,000 around you
```

## Modules and settings

All modules are in Meteor's **Stash-Hunter** category.

### Stash Hunter (`stash-hunter`)

Scans for stashes, drives the flight and sends the notifications. It must be enabled for a flight to move; `.sh start` and `.sh resume` enable it for you.

| Setting | Description | Default |
| --- | --- | --- |
| `discord-webhook-url` | Discord webhook for all notifications of this module. | *(empty)* |
| `block-detection-threshold` | Minimum number of blocks for a cluster to count as a stash. | 10 |
| `scan-radius` | Horizontal scan radius in blocks; the scan always covers the full world height. | 64 |
| `storage-only-mode` | Only look for storage containers (see [How it works](#how-it-works)). | true |
| `max-volume-threshold` | Clusters with a larger bounding box are treated as natural structures. | 5000 |
| `filter-natural-structures` | Apply the natural-structure filters (volume, density, woodland mansion check). | true |
| `min-density-threshold` | Minimum blocks per bounding-box volume for a cluster to count. | 0.002 |
| `notification-density-threshold` | Minimum density for a Discord message and waypoint. 0 reports every find. | 0.005 |
| `max-cluster-distance` | Maximum distance between neighbouring blocks of one cluster. | 30 |
| `max-cluster-size` | Maximum diameter of one cluster, so neighbouring stashes aren't merged. | 64 |
| `report-dedupe-radius` | A stash this close to a reported one or to a stash waypoint isn't reported again. | 48 |
| `flight-altitude` | Y level to fly at. | 160 |
| `scan-interval` | Ticks between scans (20 ticks = 1 second). | 40 |
| `player-detection` | Report other players within 100 blocks (at most once per player every 5 minutes). | true |
| `notify-on-death` | Notify on death and respawn. | true |
| `notify-on-completion` | Notify when an area is fully scanned. | true |
| `notify-on-disconnect` | Notify when you leave or lose the server. | true |
| `mention-everyone` | Ping `@everyone` for stash finds, deaths, disconnects and other important messages. | false |
| `use-baritone-pathing` | Let Baritone steer when it is installed. No effect without Baritone. | true |

### Auto Elytra Repair (`auto-elytra-repair`)

When the elytra's remaining durability drops to `repair-threshold`, the module runs a repair sequence:

1. It stops the autopilot and waits until you have slowed down.
2. It places a block below you with Meteor's **Scaffold**.
3. It hovers on that block and lets Meteor's **AutoEXP** throw XP bottles until the elytra is repaired.
4. It climbs back to cruise altitude and resumes the flight.

**What you need:**
- A **Mending** elytra
- **XP bottles** anywhere in your inventory
- **Blocks** for Scaffold

**When it gives up:**
- **No XP bottles:** the repair does not start.
- **No progress for 5 seconds:** the repair stops. If the elytra is still below the threshold, the bot disconnects.
- **Elytra about to break (≤ 10 durability) while still gliding:** the bot disconnects rather than risk a fall.

| Setting | Description | Default |
| --- | --- | --- |
| `repair-threshold` | Remaining durability that starts a repair (minimum 30, so the sequence has headroom). | 50 |
| `notify-repairs` | Send Discord messages about repairs. | true |
| `repair-timeout` | Maximum repair time in seconds. | 300 |
| `debug-mode` | Print detailed progress in chat. | false |

### Stuck Detector (`stuck-detector`)

Detects elytra rubber-banding: you are gliding but haven't moved for `detection-threshold` seconds. With `auto-fix` on, it tries these steps in order:

1. It toggles ElytraFly off and on.
2. If you still haven't moved after 2 seconds, it stops gliding and briefly holds jump.

| Setting | Description | Default |
| --- | --- | --- |
| `discord-webhook-url` | Webhook for stuck notifications; can differ from the main one. | *(empty)* |
| `detection-threshold` | Seconds without movement before you count as stuck. | 3 |
| `auto-fix` | Try to fix it automatically. | true |

### Altitude Loss Detector (`altitude-loss-detector`)

Notices when you drop faster than `altitude-drop-threshold` blocks per second while gliding or while the bot is flying, for example when the elytra closed. It holds jump for 5 seconds to redeploy the elytra.

| Setting | Description | Default |
| --- | --- | --- |
| `altitude-drop-threshold` | Blocks lost within one second that trigger it. | 10 |
| `auto-fix` | Hold jump to recover. | true |

### NewerNewChunks (`NewerNewChunks`)

Marks chunks as new (generated recently) or old (visited before), based on what the server sends. It renders them as coloured overlays and provides the data for the trail following. The module's settings page also has a **DELETE CHUNK DATA** button that deletes the saved data for the current dimension. You have to press it twice within 5 seconds.

| Setting | Description | Default |
| --- | --- | --- |
| `PaletteExploit` | Detect new chunks from the chunk section palettes. Servers on 1.18+ only. | true |
| `Detection for chunks that haven't been explored since <=1.17` | Mark chunks that are being upgraded from an old version. Needs `PaletteExploit`. | true |
| `Pre 1.17 Overworld OldChunk Detector` | Mark Overworld chunks generated before 1.17 (by their blocks). | true |
| `Pre 1.16 Nether OldChunk Detector` | Mark Nether chunks generated before 1.16 (missing newer Nether blocks). | true |
| `Pre 1.13 End OldChunk Detector` | Mark End chunks generated in old versions (heuristic). | true |
| `Chunk Detection Mode` | `Normal`, `IgnoreBlockExploit` or `BlockExploitMode`. The non-normal modes are for old servers whose build limits were raised. | Normal |
| `dynamic-trail-detection` | Let the flight follow trails of new chunks. | true |
| `LiquidExploit` | Estimate new chunks from flowing liquids. | false |
| `BlockUpdateExploit` | Estimate new chunks from block updates (may include old chunks; use `BlockExploitMode`). | false |
| `RemoveOnModuleDisabled` | Clear the cached chunks when the module is disabled. | true |
| `RemoveOnLeaveWorldOrChangeDimensions` | Clear the cached chunks when leaving the world or changing dimension. | true |
| `RemoveOutsideRenderDistance` | Drop cached chunks outside the render distance below. | false |
| `SaveChunkData` | Save detected chunks to disk. | true |
| `LoadChunkData` | Load saved chunks when the module is enabled. | true |
| `AutoReloadChunks` | Reload the saved chunks periodically. | false |
| `AutoReloadDelayInSeconds` | Delay for `AutoReloadChunks`. | 60 |
| `Render-Distance(Chunks)` | How far (in chunks) the overlays are drawn. | 64 |
| `render-height` | Y level at which the overlays are drawn. | 0 |
| `shape-mode` | Draw sides, lines or both. | Both |

Colours (sides / lines) can be set separately for each chunk type:

| Chunk type | Default colour |
| --- | --- |
| New chunks | red |
| Block-exploit chunks | blue |
| Old chunks | green |
| Chunks being updated from ≤ 1.17 | yellow |
| Chunks from old versions | yellow-green |

## Discord notifications

Create a webhook in your Discord server (*Server Settings → Integrations → Webhooks*) and paste its URL into `discord-webhook-url`. Depending on your settings, you receive:

| Notification | Sent when |
| --- | --- |
| **Stash Found** | A stash is found. Includes coordinates, block count, volume, density, a rating and a per-block-type breakdown. |
| **Player Detected** | Another player comes within 100 blocks. Includes name, coordinates and distance. |
| **Bot Died** / **Bot Respawned** | You die or respawn. Includes the location. |
| **Scanning Completed** | An area is fully scanned. |
| **Disconnected** | You leave or lose the server. |
| **Out of Elytras** | The elytra is gone and couldn't be repaired; the bot disconnects. |
| **Elytra Repair Started / Finished / Failed** | A repair runs (`notify-repairs`). |
| **Elytra Stuck Detected** | Rubber-banding is detected (Stuck Detector's own webhook). |

Pings are opt-in: `@everyone` is only used when `mention-everyone` is on. Rate limits and temporary Discord errors are retried automatically. While Discord is unreachable, at most 50 messages are queued.

## HUD

Add the **stash-hunter-status** element in Meteor's HUD editor. It shows the bot's status:

- **White:** idle.
- **Green:** flying. It then also shows `Flying to waypoint n/m`, `Edge Following` or `Trail Following`, the current target with its distance, and the waypoint progress.
- **Yellow:** the area is completed.

## Files it creates

| File | Content |
| --- | --- |
| `meteor-client/stash-hunter.properties` | Addon configuration (also stored in Meteor's own settings). |
| `meteor-client/stashhunter_trips.json` | Saved trips for `.sh resume`. If it is ever unreadable, it is moved to `stashhunter_trips.json.corrupt` instead of being overwritten. |
| `StashHunter/NewChunks/<server>/<dimension>/*.txt` | Chunk data saved by NewerNewChunks, one `[x, z]` chunk position per line. |

All paths are relative to your Minecraft game directory. Log output goes to the normal game log (`logs/latest.log`).

## Troubleshooting

- **"Please equip an Elytra before starting!"**: The flight only starts with an elytra in the chest slot.
- **The bot does not move after `.sh start`**: Make sure the `stash-hunter` module is enabled. The start command enables it, but disabling the module stops the flight.
- **No stashes are reported**:
  - Check `notification-density-threshold`. Finds below it only appear in chat.
  - A stash that is already reported, or has a stash waypoint nearby, is not reported again.
  - `storage-only-mode` ignores everything that isn't a container.
- **Too many false positives**: Raise `block-detection-threshold` or `min-density-threshold`, or lower `max-volume-threshold`.
- **Repairs never start**: The repair needs XP bottles in your inventory and a Mending elytra, and the module must be enabled (`.sh repair` shows its state).
- **No Discord messages**: Check the webhook URL. Rejected requests (for example a deleted webhook) are logged in `logs/latest.log`.

## Building from source

You need Java 25 and Git.

```sh
git clone https://github.com/omtoi101/Stash-Hunter.git
cd Stash-Hunter
./gradlew build        # Windows: gradlew.bat build
```

- **Output:** the jar is written to `build/libs/`.
- **What `build` checks:** it also runs the unit tests and a whitespace check (Spotless). If the check fails, `./gradlew spotlessApply` fixes the formatting.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for notable changes.

## License

Licensed under the **Apache License 2.0**. See [LICENSE](LICENSE).

## Credits

- **omtoi**: original author and maintainer.
- **\_og3**: port to Minecraft 26.2 and the current Meteor Client.
