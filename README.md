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
- *(Optional)* [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) for 26.2, to get stash waypoints on the minimap and Xaero's World Map too (`save-to-xaero`)

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

After each detour it returns to its strips. Chunks the server hasn't sent yet are no reason to leave a strip: the bot flies on and scans them once they arrive.

**Scanning.** Every `scan-interval` ticks the addon scans a cylinder of `scan-radius` blocks around the player over the **full world height**, so stashes far below the flight altitude are found too. Each chunk is read once when it loads and again only when a stash block in it changes or after a minute, so a large radius stays cheap. A chunk that was inside the radius before the server sent it is scanned as soon as it arrives, even if the bot has flown on by then. If chunks never arrive (the server is too slow for your flight speed, or its view distance is smaller than `scan-radius`), the chat warns you at most every 10 minutes, because those spots stay unscanned.

- **Storage-only mode (default):** looks for chests, trapped chests, barrels and shulker boxes of every colour.
- **Full mode:** also counts furnaces, blast furnaces, smokers, brewing stands, enchanting tables, anvils, crafting tables, beacons, conduits, hoppers, dispensers, droppers and beds.

**Clustering and filtering.**

- Found blocks are grouped into clusters. Neighbouring blocks may be up to `max-cluster-distance` apart, and one cluster is at most `max-cluster-size` across.
- A cluster counts as a stash if it has at least `block-detection-threshold` blocks and passes the filters: `max-volume-threshold`, `min-density-threshold` and, if enabled, `filter-natural-structures`.
- A cluster that is too big or too sparse is not simply dropped: it is split into tighter groups, and a group that still has enough blocks and passes the filters is reported. So a stash hidden in or next to a natural structure, or a base with a few chests far away from its storage room, is still found.
- These filters are heuristics. Structure loot (dungeons, mineshafts, villages) can still be reported now and then.

**Reporting.**

- Each stash is reported once. Another cluster within `report-dedupe-radius` of a reported stash, or of an existing stash waypoint, is not reported again.
- If the stash's density reaches `notification-density-threshold`, you get a Discord message and a Meteor waypoint named `<count> Blocks at <x, y, z>` in the current dimension. Lower-density finds are only mentioned in chat.
- With `save-to-xaero` on and Xaero's Minimap installed, the same waypoint (gold, symbol `S`) is also added to Xaero's current waypoint set. Xaero's World Map reads the minimap's waypoints each time it is opened, so it shows there as well. When a stash report is corrected ("Stash Updated"), both waypoints are moved.

**Staying alive.** The flight is paused automatically (and saved, so `.sh resume` continues it) when you die, change dimension or leave the server. If the elytra breaks or goes missing and isn't replaced or repaired in time, the bot sends an "Out of Elytras" notification and disconnects, instead of falling.

## Commands

`.stashhunter` can be shortened to `.sh`.

| Command | What it does |
| --- | --- |
| `.sh start <x1> <z1> <x2> <z2> [stripWidth]` | Scans the rectangle between the two corners. |
| `.sh spiral [<x> <z>] <radius> [gap]` | Scans a square spiral outward from your position (or from `x z`; `~` works) up to `radius` blocks (50-25000), rings `gap` blocks apart (default 128, 50-500). Pause/resume like `start`. Example: `.sh spiral 3000`. |
| `.sh stop` | Stops the current flight. |
| `.sh pause` | Stops the flight and saves it as a trip. |
| `.sh resume [timestamp]` | Continues the latest saved trip, or the one with the given timestamp. |
| `.sh list` | Lists the timestamps of all saved trips. Only the 20 newest are kept; a flight that is paused again replaces its own entry. |
| `.sh status` | Shows the status, the current waypoint and the current target. |
| `.sh altitude [y]` | Shows or sets the flight altitude (100-400). A running flight moves its remaining waypoints to the new height; resumed trips also use the current value. |
| `.sh repair` | Shows the Auto Elytra Repair state and your elytra's durability. |
| `.sh repair toggle` | Turns Auto Elytra Repair on or off. |
| `.sh help` | Prints a short command reference in chat. |
| `.clear-stashes` | Forgets the already reported stashes of the current server (also on disk), so they can be reported again. Existing stash waypoints still prevent a repeat report until you delete them. |
| `.clear-players` | Forgets the recently reported players, so they are reported again. |
| `.chest-index` | Shows how many containers the `chest-index` module has recorded on this server. |
| `.chest-index search <item>` | Lists the 10 indexed containers with the most items whose id contains `<item>` (e.g. `diamond`, `totem`, `elytra`), shulker box contents included, with position and dimension. |
| `.chest-index clear` | Forgets every indexed container of this server (also on disk). |

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
| `max-volume-threshold` | Clusters with a larger bounding box are split into tighter groups; a group that is still larger counts as a natural structure. | 5000 |
| `filter-natural-structures` | Apply the natural-structure filters (volume, density, and the structure filter below). | true |
| `structure-filter-radius` | With `activated-spawner-detector` on: ignore clusters whose center is within this many blocks of a natural-structure spawner (dungeon, mineshaft, trial chamber, ...). Spawners with blocks placed/removed around them (farms) don't count. 0 = off; 6-8 covers dungeon chests. | 0 |
| `include-spawners` | Also count mob spawners as stash blocks (spawner farms are a base signal). A plain dungeon (spawner + up to two chests) is still ignored while `filter-natural-structures` is on. | false |
| `min-density-threshold` | Minimum blocks per bounding-box volume for a cluster to count. Sparser clusters are split like oversized ones. | 0.002 |
| `notification-density-threshold` | Minimum density for a Discord message and waypoint. 0 reports every find. | 0.005 |
| `max-cluster-distance` | Maximum distance between neighbouring blocks of one cluster. | 30 |
| `max-cluster-size` | Maximum diameter of one cluster, so neighbouring stashes aren't merged. | 64 |
| `report-dedupe-radius` | A stash this close to a reported one or to a stash waypoint isn't reported again. | 48 |
| `save-to-xaero` | Also add each stash waypoint to Xaero's Minimap (current waypoint set of the current server and dimension). No effect without Xaero's Minimap. | false |
| `flight-altitude` | Y level to fly at (100-400, also `.sh altitude <y>`). Changing it during a flight applies to the remaining waypoints. | 160 |
| `scan-interval` | Longest wait between scans in ticks (20 ticks = 1 second, max 1200). | 40 |
| `scan-by-distance` | Also scan after flying half the scan radius since the last scan, so fast flight leaves no gaps. | true |
| `scan-only-while-flying` | Only scan while a flight (`.sh start`/`spiral`/`resume`) runs. Off: also works as a standing scanner. | false |
| `player-detection` | Report other players nearby. | true |
| `player-detection-radius` | Distance in blocks for `player-detection`. | 100 |
| `player-report-cooldown` | Minutes before the same player is reported again. | 5 |
| `notify-on-death` | Notify on death and respawn. | true |
| `notify-on-completion` | Notify when an area is fully scanned. | true |
| `notify-on-disconnect` | Notify when you leave or lose the server. | true |
| `mention-everyone` | Ping `@everyone` for stash finds, deaths, disconnects and other important messages. | false |
| `use-baritone-pathing` | Let Baritone steer when it is installed. No effect without Baritone. | true |
| `land-after-completion` | After the area is scanned, let Baritone's elytra pathing land on a safe spot near the last waypoint. Without Baritone you keep gliding (a chat hint says so). `.sh stop` cancels the landing. | false |
| `land-on-low-fireworks` | With Baritone flight: once the rockets in your inventory drop to `low-firework-threshold`, the scan is saved as a trip and Baritone lands on a safe spot about 64 blocks ahead. Restock and `.sh resume`. Without Baritone, ElytraFly needs no rockets, so nothing happens. | true |
| `low-firework-threshold` | Rocket count (whole inventory) that triggers the landing. | 8 |

### Auto Elytra Repair (`auto-elytra-repair`)

When the elytra's remaining durability drops to `repair-threshold`, the module first looks for a spare elytra in your inventory with more durability than that (`swap-to-spare`) and puts it on - no stop needed. Without a spare it runs a repair sequence:

1. It pauses the flight (Baritone included) and hovers with Meteor's **ElytraFly** until you have stopped.
2. It places a block below you with Meteor's **Scaffold** (up to 3 tries).
3. It hovers on that block and lets Meteor's **AutoEXP** throw XP bottles until the elytra is repaired.
4. It resumes the flight. With Baritone, Baritone climbs back to cruise altitude on its way to the next waypoint.

**What you need:**
- A **Mending** elytra
- **XP bottles** anywhere in your inventory
- **Blocks** for Scaffold

**When it gives up:**
- **No XP bottles or no Mending:** the repair does not start. With Baritone flight the scan is saved and Baritone lands on a safe spot nearby (`.sh resume` later); otherwise the bot disconnects.
- **Scaffold places no block in 3 tries:** the bot disconnects.
- **No progress for 5 seconds:** the repair stops. If the elytra is still below the threshold, the bot disconnects.
- **Elytra about to break (≤ 10 durability) while still gliding** (also during a Baritone landing): the bot disconnects rather than risk a fall.

| Setting | Description | Default |
| --- | --- | --- |
| `repair-threshold` | Remaining durability that starts a repair (minimum 30, so the sequence has headroom). | 50 |
| `swap-to-spare` | Put on a spare elytra from the inventory (above `repair-threshold`) instead of repairing mid-air. | true |
| `notify-repairs` | Send Discord messages about repairs. | true |
| `repair-timeout` | Maximum repair time in seconds. | 300 |
| `debug-mode` | Print detailed progress in chat. | false |

### Stuck Detector (`stuck-detector`)

Detects elytra rubber-banding: you are gliding but haven't moved for `detection-threshold` seconds. With `auto-fix` on, it tries these steps in order:

1. It toggles ElytraFly off and on.
2. If you still haven't moved after 2 seconds, it stops gliding and briefly holds jump.

Stopping the glide only happens within 4 blocks of the ground (or water), so a fix never drops you from cruise altitude. Higher up, the flight controller takes over. It also pauses during an elytra repair and treats a Baritone landing as normal, not as being stuck.

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
| `discord` | Send a message to the Stash-Hunter webhook when a drop is detected. | false |
| `discord-cooldown` | Minimum seconds between two Discord messages. | 60 |

### NewerNewChunks (`NewerNewChunks`)

Marks chunks as new (generated recently) or old (visited before), based on what the server sends. It renders them as coloured overlays and provides the data for the trail following. The module's settings page also has a **DELETE CHUNK DATA** button that deletes the saved data for the current dimension. You have to press it twice within 5 seconds.

| Setting | Description | Default |
| --- | --- | --- |
| `PaletteExploit` | Detect new chunks from the order of the chunk section palettes (freshly generated sections still list air first). Works in all dimensions; chunks without a usable section stay unmarked. Servers on 1.18+ only; servers that rewrite palettes (some anti-xray plugins) can confuse it. | true |
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

### Slime Chunks (`slime-chunks`)

Marks slime chunks around you in the Overworld as green squares. In Java Edition they depend on the world seed, which servers don't send: in singleplayer the module uses the real seed, in multiplayer you have to enter it.

| Setting | Description | Default |
| --- | --- | --- |
| `world-seed` | The world seed, as a number or as text (hashed like the Create World screen). Ignored in singleplayer. | empty |
| `radius` | How many chunks around you to mark. | 16 |
| `render-height` | Height of the squares. Slimes spawn below y=40. | 40 |
| `shape-mode`, `side-color`, `line-color` | Look of the squares. | Both, green |

### Old Chunk Notifier (`old-chunk-notifier`)

Tells you when you fly from new or unclassified terrain into chunks that NewerNewChunks marked as old, in chat and on Discord. Needs NewerNewChunks to be enabled. Only the step into an old area is reported, not every old chunk.

| Setting | Description | Default |
| --- | --- | --- |
| `include-old-generation` | Also count chunks generated in an old Minecraft version. | on |
| `include-being-updated` | Also count chunks not explored since ≤ 1.17. | on |
| `cooldown` | Minimum seconds between two messages. | 60 |
| `discord` | Send the message to the webhook from `discord-webhook-url`. | on |

### Activated Spawner Detector (`activated-spawner-detector`)

Finds spawners that a player has already been near, from the chunk data the server sends. The mob type tells you the structure: dungeon (zombie, skeleton, spider), woodland mansion (spider on birch planks), mineshaft (cave spider), stronghold (silverfish), fortress (blaze), bastion (magma cube). Trial spawners count once a player has triggered them. Players often hide loot near such spawners, so the module also checks for storage within 16 blocks (14 for trial spawners). Optionally it marks amethyst geodes too. Works on its own, without the other modules. Ported from [PathSeeker](https://github.com/FaxHack/PathSeeker) (Apache-2.0).

| Setting | Description | Default |
| --- | --- | --- |
| `chat-feedback`, `display-coords` | Chat message per detection, with coordinates. | on, on |
| `stash-message`, `less-stash-spam` | Warn about possible stashes, only if storage is nearby. | on, on |
| `check-air-disturbances` | Also count untouched spawners if air and cave air are mixed around them (blocks placed and removed). | off |
| `ignore-geodes` | Skip that air check near geode blocks. | on |
| `storage-blocks` | Blocks that count as storage near a spawner (chest minecarts always count). | chest, barrel, hopper, dispenser |
| `deactivated-spawner-detector`, `torch-scan-distance` | Mark spawners with light blocks next to them in their own colour. | on, 1 |
| `dungeon`, `mineshaft`, `stronghold`, `fortress`, `bastion`, `woodland-mansion`, `trial-chamber`, `other-spawners` | One toggle per structure type. | on |
| `amethyst-geode` | Mark amethyst geodes (budding amethyst). | off |
| `structure-waypoints` | Create a Meteor waypoint for every structure with an activated spawner, named `<Type> structure @ x, y, z`. One per structure: spawners of a type closer than 16 (dungeon) to 96 blocks (mineshaft, stronghold, fortress) count as one. | off |
| `render-distance` | How far (in chunks) detections are checked and drawn. | 32 |
| `remove-outside-render-distance` | Forget detections farther away than that. | on |
| `less-render-spam` | Only draw the range box if storage is nearby. | on |
| `tracers`, `nearest-tracer-only` | Tracers to all detections or only the nearest. | on, off |
| `spawner-range-rendering` | Draw the spawner's activation range. | on |
| `shape-mode` and colours | Look of the boxes: spawner (red), trial spawner (orange), deactivated (magenta), geode (purple), range (light blue). | Both |

### Auto Firework (`auto-firework`)

**Automation, off by default.** Keeps up elytra speed with firework rockets: while gliding, when your forward speed (along your look direction) drops below `min-speed`, it uses a plain rocket from the offhand or hotbar (switching the hotbar slot briefly and back). Rockets with firework stars are never used, since they explode on you. It pauses while a screen is open, during Baritone's elytra flight (Baritone boosts on its own) and during the post-scan landing. It uses up rockets, and anti-cheat on some servers may flag automated item use.

| Setting | Description | Default |
| --- | --- | --- |
| `min-speed` | Fire below this forward speed, in blocks per second. | 20 |
| `use-delay` | Minimum ticks between two rockets. | 40 |
| `check-cooldown` | Ticks between two speed checks. | 5 |
| `only-when-flying` | Only fire during the Stash-Hunter scan flight. Off: during any elytra flight. | false |

### Online Player Activity (`online-player-activity`)

Watches the server's tab list and reports when other players join or leave, anywhere on the server. Only players shown in the tab list count (plugin NPCs and vanished players are not seen).

| Setting | Description | Default |
| --- | --- | --- |
| `chat` | Report joins and leaves in chat. | true |
| `count-only` | Only report the player count, without names. | false |
| `show-count` | Show the online count next to the module name. | true |
| `join-grace` | Seconds after joining or changing dimension in which changes aren't reported (tab list filling up). | 5 |
| `discord` | Send changes to the Stash-Hunter webhook; changes during the cooldown are sent together. | false |
| `discord-cooldown` | Minimum seconds between two Discord messages. | 60 |

### Player Alarms (`player-alarms`)

Plays a repeating local alarm while another player is within `radius`, for unattended runs. Only players inside render distance can be seen. For Discord messages use `player-detection` of the Stash Hunter module.

| Setting | Description | Default |
| --- | --- | --- |
| `radius` | Ring while a player is closer than this many blocks. | 100 |
| `sound` | Bell, Pling, Anvil, LevelUp or Orb. | Bell |
| `volume` | Alarm volume (also scaled by the game's sound sliders). | 1.0 |
| `repeat-interval` | Seconds between two rings while a player stays in range. | 3 |
| `ignore-friends` | Don't ring for Meteor friends. | true |
| `chat` | List the players in range whenever that set changes. | true |
| `action-bar` | Show the players in range in the action bar with every ring. | false |

### Auto Logout (`auto-logout`)

**Risky, off by default.** Disconnects when danger is detected. Some servers count this as combat logging - check the rules first. A running flight is paused and saved as a trip before disconnecting, so `.sh resume` continues it. Meteor's fake players (Blink, FakePlayer) are ignored.

| Setting | Description | Default |
| --- | --- | --- |
| `player-nearby` | Disconnect when another player comes within `radius`. | true |
| `radius` | Distance in blocks. | 32 |
| `ignore-friends` | Ignore Meteor friends. | true |
| `low-health` | Disconnect when health drops below `min-health`. | false |
| `min-health` | Threshold in half-hearts (20 = full health). | 10 |
| `discord` | Notify the Stash-Hunter webhook before disconnecting. | true |
| `disable-after-logout` | Turn the module off after it triggers, so reconnecting doesn't log out again at once. | true |

### Stacked Minecarts (`stacked-minecarts`)

Marks minecarts stacked in one spot. Stacked chest or hopper minecarts are a common way to hide storage, and stacks are easy to miss because they look like a single cart. Only loaded entities can be seen, i.e. within the server's entity range. Each new stack is reported once per world. Ported from [PathSeeker](https://github.com/FaxHack/PathSeeker) (Apache-2.0).

| Setting | Description | Default |
| --- | --- | --- |
| `detection-radius` | Maximum distance in blocks between carts of one stack. | 0.5 |
| `minimum-stack-size` | Minimum number of carts in one spot. | 2 |
| `storage-only` | Only count chest and hopper minecarts. | false |
| `chat-feedback` | Report each new stack in chat. | true |
| `discord` | Send each new stack to the Stash-Hunter webhook. | false |
| `render`, `shape-mode` and colours | Box on each stack, growing with its size (up to 2 blocks). | on, Both, red |
| `tracers` | Tracers to all stacks. | on |

### Chest Index (`chest-index`)

Records what chests, trapped chests, barrels and shulker boxes hold, including the contents of shulker boxes inside them, and keeps it per server in a JSON file (see [Files it creates](#files-it-creates)). Search it with `.chest-index search <item>`.

- **Auto-open:** standing still or walking, the module opens the nearest container in reach, reads it once the server has sent its contents and closes it again, then moves on to the next one. It pauses while a screen is open, while you sneak and during elytra flight (including a stash-hunter flight). Chests under a solid block can't be opened and are skipped. Containers it has opened, or given up on because nothing opened within 2 seconds, are not opened again until the module is toggled or you change dimension.
- **Containers you open yourself** are indexed too (also with `auto-open` off). They stay open, and the entry is updated while you take things out and once more when you close them.
- A double chest is one entry for both halves.
- The index is saved every minute and when you leave the server or turn the module off.

| Setting | Description | Default |
| --- | --- | --- |
| `auto-open` | Open containers in reach automatically. Off: only index containers you open yourself. | true |
| `delay` | Ticks between two auto-opened containers. | 10 |
| `rotate` | Look at a container before opening it (some anti-cheats reject clicks on blocks you don't look at). | true |
| `skip-indexed` | Don't auto-open containers that are already in the index, also from earlier sessions. Turn off to refresh it. | true |
| `chat-feedback` | Report each indexed container in chat (item count, shulker boxes). | true |

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
| **Old Chunks Entered** | You enter old chunks (Old Chunk Notifier). Includes chunk, coordinates and dimension. |
| **Stacked Minecarts** | A new minecart stack is found (`stacked-minecarts`, `discord` on). Includes count, coordinates and dimension. |

Pings are opt-in: `@everyone` is only used when `mention-everyone` is on. Rate limits and temporary Discord errors are retried automatically. While Discord is unreachable, at most 50 messages are queued.

## HUD

Add the **stash-hunter-status** element in Meteor's HUD editor. It shows the bot's status:

- **White:** idle.
- **Green:** flying. It then also shows `Flying to waypoint n/m` or `Trail Following`, the current target with its distance, and the waypoint progress.
- **Yellow:** the area is completed.

## Files it creates

| File | Content |
| --- | --- |
| `meteor-client/stash-hunter.properties` | Addon configuration (also stored in Meteor's own settings). |
| `meteor-client/stashhunter_trips.json` | Saved trips for `.sh resume`. If it is ever unreadable, it is moved to `stashhunter_trips.json.corrupt` instead of being overwritten. |
| `meteor-client/StashHunter/NewChunks/<server>/<dimension>/*.txt` | Chunk data saved by NewerNewChunks, one `[x, z]` chunk position per line. Older versions saved it under `StashHunter/NewChunks/` relative to the launcher's working directory; that folder is moved here automatically the first time it's needed. |
| `meteor-client/StashHunter/chest-index/<server>.json` | Containers recorded by `chest-index`: dimension, position (and the other half of a double chest), block, slot count, time, the items summed per id, and each shulker box inside with its name and contents. A file that can't be parsed is moved to `<server>.json.corrupt`. `.chest-index clear` deletes it. |
| `meteor-client/StashHunter/reported-stashes/<server>.txt` | Stashes already reported on that server (`dimension x y z` per line), so they aren't reported again after a reconnect. `.clear-stashes` deletes it. |

All paths are relative to your Minecraft game directory. Log output goes to the normal game log (`logs/latest.log`).

## Troubleshooting

- **"Please equip an Elytra before starting!"**: The flight only starts with an elytra in the chest slot.
- **The bot does not move after `.sh start`**: Make sure the `stash-hunter` module is enabled. The start command enables it, but disabling the module stops the flight.
- **No stashes are reported**:
  - Check `notification-density-threshold`. Finds below it only appear in chat.
  - A stash that is already reported, or has a stash waypoint nearby, is not reported again.
  - `storage-only-mode` ignores everything that isn't a container.
  - A warning that chunks "never loaded" means parts of the area were not scanned. Lower `scan-radius` (and the strip width) below the server's view distance, or fly slower.
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
- **PathSeeker** (FaxHack / dev.journey, Apache-2.0): original `ActivatedSpawnerDetector` and `StackedMinecartsDetector`, ported and adapted.
