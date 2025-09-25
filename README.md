# Stash-Hunter

A Meteor client addon for finding stashes on anarchy servers. This mod is designed to help players discover new stashes by tracking player-placed blocks.

## Features

- **Stash Finding Module**: The core of the addon, which actively searches for and records potential stash locations.
- **Auto Elytra**: A fully automated feature that manages elytra flight, ensuring continuous and efficient exploration.
- **Chunk Trail Following Algorithm**: A sophisticated algorithm that automatically follows trails of newly generated chunks to locate player activity and bases.
- **Stuck Detector**: A utility to detect if the player character is stuck, which can be useful during automated exploration.
- **Customizable Commands**:
    - `.stashhunter`: The main command to configure the Stash-Hunter module.
    - `.clearstashes`: Clears the list of found stashes.
    - `.clearplayers`: Clears the list of tracked players.
- **In-game HUD**: A Heads-Up Display to show real-time information about the stash finding process.

## Installation

1.  Download the latest version of Stash-Hunter from the [Releases](https://github.com/omtoi101/stash-hunter/releases) page.
2.  Make sure you have [Meteor Client](https://meteorclient.com/) installed.
3.  Place the downloaded `.jar` file into your `mods` folder.
4.  Launch Minecraft with Fabric.

## Usage

This document provides detailed instructions on how to use the commands available in the Stash-Hunter addon.

### Main Command: `.stashhunter`

The `.stashhunter` command (which can be shortened to `.sh`) is the main command for controlling the automated flight and scanning system.

#### Sub-commands

-   `.stashhunter start <x1> <z1> <x2> <z2> [stripWidth]`
    -   **Description**: Starts the automated scanning process in a defined rectangular area. This mode is ideal for systematically searching a specific region.
    -   **Arguments**:
        -   `<x1> <z1>`: The coordinates of the starting corner of the area.
        -   `<x2> <z2>`: The coordinates of the opposite corner of the area.
        -   `[stripWidth]` (optional): The width of the strips the bot will fly in. Defaults to `128`. Must be between 50 and 500.
    -   **Coordinate Formats**:
        -   **Absolute**: Use standard coordinates (e.g., `10000 5000`).
        -   **Relative**: Use `~` to represent your current position (e.g., `~ ~` for your current X and Z).
        -   **Relative with Offset**: Use `~` followed by a number to specify an offset from your current position (e.g., `~-5000` for 5000 blocks in the negative direction from your current location).
    -   **Example**:
        ```
        .sh start ~-10000 ~-10000 ~10000 ~10000 200
        ```
        This command will scan a 20,000 x 20,000 area around you, with a strip width of 200 blocks.

-   `.stashhunter trail`
    -   **Description**: Activates the fully automated chunk trail following mode. In this mode, the addon will automatically detect and follow trails of newly generated chunks, allowing for autonomous exploration to find player activity.
    -   **Note**: This command does not require coordinates, as it dynamically follows chunk trails.

-   `.stashhunter stop`
    -   **Description**: Stops the current scanning or trail-following operation.

-   `.stashhunter status`
    -   **Description**: Shows the current status of the Stash-Hunter, including whether it's active, the current progress (waypoints), and the current target coordinates.

-   `.stashhunter help`
    -   **Description**: Displays a help message with a summary of all commands and examples.

### Utility Commands

-   `.clear-stashes`
    -   **Description**: Clears the internal list of stashes that have already been found and reported. This is useful if you want the mod to notify you about a previously found stash again.

-   `.clear-players`
    -   **Description**: Clears the list of players that have been recently detected. This will allow the mod to send a new notification for a player that is still in the area.

## Configuration

This document provides a detailed overview of all the settings available for customization in the Stash-Hunter addon.

### Stash-Hunter Module

These settings control the core functionality of the stash finding process.

| Setting                        | Description                                                                                             | Default Value |
| ------------------------------ | ------------------------------------------------------------------------------------------------------- | ------------- |
| `discord-webhook-url`          | The Discord webhook URL to send notifications to.                                                       | (empty)       |
| `block-detection-threshold`    | The number of valuable blocks to find before a stash is detected.                                       | 10            |
| `scan-radius`                  | The radius (in blocks) to scan for valuable blocks around the player.                                   | 64            |
| `storage-only-mode`            | If enabled, only searches for storage containers (chests, shulkers). Faster than a full block scan.      | true          |
| `max-volume-threshold`         | The maximum volume of a block cluster to be considered a stash. Helps filter out large natural structures. | 5000          |
| `filter-natural-structures`    | If enabled, attempts to filter out likely natural structures like dungeons and mineshafts.                | true          |
| `min-density-threshold`        | The minimum density (blocks/volume) required for a cluster to be considered a potential stash.          | 0.002         |
| `notification-density-threshold` | The minimum density required to send a Discord notification. Set to 0 to be notified for all finds.       | 0.005         |
| `max-cluster-distance`         | The maximum distance between two blocks for them to be considered part of the same cluster.               | 30            |
| `flight-altitude`              | The altitude (Y-level) the bot will fly at during scanning.                                             | 320           |
| `scan-interval`                | The interval in ticks between each scan for blocks (20 ticks = 1 second).                               | 40            |
| `player-detection`             | Whether to notify when another player is detected nearby.                                               | true          |
| `notify-on-death`              | Whether to send a Discord notification if you die.                                                      | true          |
| `notify-on-completion`         | Whether to send a Discord notification when the scanning of a defined area is complete.                 | true          |

### Stuck Detector Module

These settings control the behavior of the elytra rubber-band detection system.

| Setting               | Description                                                                          | Default Value |
| --------------------- | ------------------------------------------------------------------------------------ | ------------- |
| `discord-webhook-url` | The Discord webhook URL to send stuck notifications to. Can be the same or different from the main one. | (empty)       |
| `detection-threshold` | The time in seconds the player needs to be motionless before being considered stuck.   | 3             |
| `auto-fix`            | If enabled, automatically tries to fix the rubber-banding by toggling `ElytraFly`.     | true          |

### NewerNewChunks Module

These settings control the behavior of the new chunk detection and trail-following system.

| Setting                        | Description                                                                                             | Default Value |
| ------------------------------ | ------------------------------------------------------------------------------------------------------- | ------------- |
| `PaletteExploit`               | (1.18+ only) Detects new chunks by scanning chunk palettes.                                             | true          |
| `beingUpdatedDetector`         | Marks chunks that are being updated from an older version. Requires `PaletteExploit`.                   | true          |
| `overworldOldChunksDetector`   | Detects old chunks in the Overworld based on block types.                                               | true          |
| `netherOldChunksDetector`      | Detects old chunks in the Nether based on missing new blocks.                                           | true          |
| `endOldChunksDetector`         | Detects old chunks in the End based on biome types.                                                     | true          |
| `Chunk Detection Mode`         | The mode for detecting new chunks. `BlockExploitMode` is recommended for some servers.                  | Normal        |
| `chunk-trail-following`        | When enabled, the Auto Elytra feature will automatically follow trails of new chunks, creating a fully automated exploration system. | true          |
| `LiquidExploit`                | Estimates new chunks based on flowing liquids.                                                          | false         |
| `BlockUpdateExploit`           | Estimates new chunks based on block updates.                                                            | false         |
| `RemoveOnModuleDisabled`       | Clears cached chunk data when the module is disabled.                                                   | true          |
| `RemoveOnLeaveWorldOrChangeDimensions` | Clears cached chunk data when changing worlds or dimensions.                                     | true          |
| `RemoveOutsideRenderDistance`  | Clears cached chunk data for chunks outside your render distance.                                       | false         |
| `SaveChunkData`                | Saves detected chunk data to a file.                                                                    | true          |
| `LoadChunkData`                | Loads chunk data from a file when the module is enabled.                                                | true          |
| `AutoReloadChunks`             | Automatically reloads chunk data from files periodically.                                               | false         |
| `AutoReloadDelayInSeconds`     | The delay in seconds for auto-reloading chunk data.                                                     | 60            |
| `Render-Distance(Chunks)`      | The render distance for chunk overlays.                                                                 | 64            |
| `render-height`                | The Y-level at which to render the chunk overlays.                                                      | 0             |
| `shape-mode`                   | The rendering mode for chunk overlays (Sides, Lines, or Both).                                          | Both          |
| `new-chunks-side-color`        | The side color for newly generated chunks.                                                              | Red (95)      |
| `BlockExploitChunks-side-color`| The side color for chunks detected via block updates.                                                   | Blue (75)     |
| `old-chunks-side-color`        | The side color for old chunks.                                                                          | Green (40)    |
| `being-updated-chunks-side-color` | The side color for chunks being updated from an older version.                                           | Yellow (60)   |
| `old-version-chunks-side-color`| The side color for chunks from old Minecraft versions.                                                  | Yellow (40)   |
| `new-chunks-line-color`        | The line color for newly generated chunks.                                                              | Red (205)     |
| `BlockExploitChunks-line-color`| The line color for chunks detected via block updates.                                                   | Blue (170)    |
| `old-chunks-line-color`        | The line color for old chunks.                                                                          | Green (80)    |
| `being-updated-chunks-line-color` | The line color for chunks being updated from an older version.                                           | Yellow (100)  |
| `old-version-chunks-line-color`| The line color for chunks from old Minecraft versions.                                                  | Yellow (80)   |

## Detailed Features

This document provides a detailed overview of the features available in the Stash-Hunter addon.

### Stash-Hunter Module

The `StashHunterModule` is the core of this addon. It is a highly configurable module that automates the process of finding stashes.

#### Key Features:

-   **Auto Elytra**: A fully automated feature that manages elytra flight for continuous and efficient exploration. It handles takeoff, landing, and maintaining altitude, allowing for seamless travel across vast distances without manual intervention.
-   **Configurable Scanning**: You can configure various parameters for scanning, such as:
    -   `scan-radius`: The radius around the player to scan for blocks.
    -   `block-detection-threshold`: The minimum number of valuable blocks to trigger a stash detection.
    -   `storage-only-mode`: A mode to only search for storage containers like chests and shulker boxes, which is faster than scanning for all valuable blocks.
-   **Advanced Filtering**: The module includes advanced filtering to avoid false positives from natural structures:
    -   `max-volume-threshold`: Sets a maximum volume for a cluster of blocks to be considered a stash.
    -   `min-density-threshold`: Sets a minimum density (blocks/volume) for a cluster to be considered a stash.
    -   `filter-natural-structures`: An option to enable or disable the filtering of natural structures.
-   **Player Detection**: The module can detect other players within a certain range and send a notification.
-   **Discord Integration**: The module can send detailed notifications to a Discord webhook for:
    -   Found stashes, with coordinates, density, and a list of found containers.
    -   Detected players.
    -   Player death events.
    -   Completion of a scanning mission.
-   **Meteor Waypoints**: When a stash is found, a waypoint is automatically created in Meteor Client.

### NewerNewChunks Module

The `NewerNewChunks` module is a powerful tool for identifying newly generated chunks, which can indicate recent player activity. This module is essential for the "chunktrail following algorithm" feature.

#### Key Features:

-   **Multiple Detection Methods**: The module uses several techniques to detect new chunks:
    -   **PaletteExploit**: (1.18+ only) Detects new chunks by analyzing the structure of chunk data. This is the most reliable method on modern servers.
    -   **LiquidExploit**: Identifies new chunks by looking for flowing liquids, which often indicates recent world generation.
    -   **BlockUpdateExploit**: Detects block updates that can signify new chunk generation.
-   **Old Chunk Detection**: The module can also identify chunks generated in older versions of Minecraft, helping to distinguish between old and new areas.
-   **Chunk Trail Following Algorithm**: A sophisticated algorithm that, when enabled, directs the `Auto Elytra` feature to automatically follow trails of new chunks. This creates a fully automated system for discovering player activity, bases, and other points of interest by tracking the paths of recent world generation.
-   **Configurable Rendering**: You can customize the color and rendering style of different types of chunks (new, old, etc.) to make them easily visible.

### Stuck Detector Module

The `StuckDetector` is a utility module designed to handle a common issue with elytra flight on anarchy servers: rubber-banding.

#### Key Features:

-   **Stuck Detection**: The module monitors the player's movement and detects when they are stuck in an elytra rubber-band loop.
-   **Auto-Fix**: When enabled, the module will automatically attempt to fix the rubber-banding by:
    1.  Toggling the `ElytraFly` module.
    2.  If that fails, it will stop the player's gliding.
-   **Discord Notifications**: It can send a notification to a Discord webhook when the player gets stuck, and whether it is attempting an automatic fix.

### Stash-Hunter HUD

The `StashHunterHud` provides real-time information about the status of the Stash-Hunter on your screen.

#### HUD Elements:

-   **Status**: Displays the current status of the `ElytraController` (e.g., "Active", "Idle", "Completed"). The color of the text changes based on the status.
-   **Target Information**: When active, it shows the coordinates of the current target and the distance to it.
-   **Progress**: Displays the progress of the current scanning mission in the format of `current waypoint / total waypoints`.

## Building

To build this project from source, you will need:

-   Java 21 or later
-   Git

Follow these steps:

1.  **Clone the repository:**
    ```sh
    git clone https://github.com/omtoi101/stash-hunter.git
    cd stash-hunter
    ```

2.  **Build the project:**
    -   On Windows:
        ```sh
        gradlew.bat build
        ```
    -   On macOS/Linux:
        ```sh
        ./gradlew build
        ```

3.  The compiled `.jar` file will be located in the `build/libs/` directory.

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for more details.

## Credits

-   **omtoi**: Original author.