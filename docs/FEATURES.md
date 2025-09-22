# Features

This document provides a detailed overview of the features available in the Stash-Hunter addon.

## Stash-Hunter Module

The `StashHunterModule` is the core of this addon. It is a highly configurable module that automates the process of finding stashes.

### Key Features:

-   **Automated Elytra Flight**: The module uses `ElytraController` to fly automatically over a specified area, scanning for valuable blocks.
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

## NewerNewChunks Module

The `NewerNewChunks` module is a powerful tool for identifying newly generated chunks, which can indicate recent player activity. This module is essential for the "dynamic chunk trail searching" feature.

### Key Features:

-   **Multiple Detection Methods**: The module uses several techniques to detect new chunks:
    -   **PaletteExploit**: (1.18+ only) Detects new chunks by analyzing the structure of chunk data. This is the most reliable method on modern servers.
    -   **LiquidExploit**: Identifies new chunks by looking for flowing liquids, which often indicates recent world generation.
    -   **BlockUpdateExploit**: Detects block updates that can signify new chunk generation.
-   **Old Chunk Detection**: The module can also identify chunks generated in older versions of Minecraft, helping to distinguish between old and new areas.
-   **Dynamic Trail Following**: When enabled, the `ElytraController` will automatically follow trails of new chunks. This is a powerful way to find bases and other points of interest.
-   **Configurable Rendering**: You can customize the color and rendering style of different types of chunks (new, old, etc.) to make them easily visible.

## Stuck Detector Module

The `StuckDetector` is a utility module designed to handle a common issue with elytra flight on anarchy servers: rubber-banding.

### Key Features:

-   **Stuck Detection**: The module monitors the player's movement and detects when they are stuck in an elytra rubber-band loop.
-   **Auto-Fix**: When enabled, the module will automatically attempt to fix the rubber-banding by:
    1.  Toggling the `ElytraFly` module.
    2.  If that fails, it will stop the player's gliding.
-   **Discord Notifications**: It can send a notification to a Discord webhook when the player gets stuck, and whether it is attempting an automatic fix.

## Stash-Hunter HUD

The `StashHunterHud` provides real-time information about the status of the Stash-Hunter on your screen.

### HUD Elements:

-   **Status**: Displays the current status of the `ElytraController` (e.g., "Active", "Idle", "Completed"). The color of the text changes based on the status.
-   **Target Information**: When active, it shows the coordinates of the current target and the distance to it.
-   **Progress**: Displays the progress of the current scanning mission in the format of `current waypoint / total waypoints`.
