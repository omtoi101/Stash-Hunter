# Configuration

This document provides a detailed overview of all the settings available for customization in the Stash-Hunter addon.

## Stash-Hunter Module

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

## Stuck Detector Module

These settings control the behavior of the elytra rubber-band detection system.

| Setting               | Description                                                                          | Default Value |
| --------------------- | ------------------------------------------------------------------------------------ | ------------- |
| `discord-webhook-url` | The Discord webhook URL to send stuck notifications to. Can be the same or different from the main one. | (empty)       |
| `detection-threshold` | The time in seconds the player needs to be motionless before being considered stuck.   | 3             |
| `auto-fix`            | If enabled, automatically tries to fix the rubber-banding by toggling `ElytraFly`.     | true          |
