# Usage

This document provides detailed instructions on how to use the commands available in the Base Finder addon.

## Main Command: `.basefinder`

The `.basefinder` command (which can be shortened to `.bf`) is the main command for controlling the automated flight and scanning system.

### Sub-commands

-   `.basefinder start <x1> <z1> <x2> <z2> [stripWidth]`
    -   **Description**: Starts the automated scanning process in a defined rectangular area.
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
        .bf start ~-10000 ~-10000 ~10000 ~10000 200
        ```
        This command will scan a 20,000 x 20,000 area around you, with a strip width of 200 blocks.

-   `.basefinder stop`
    -   **Description**: Stops the current scanning operation.

-   `.basefinder status`
    -   **Description**: Shows the current status of the Base Finder, including whether it's active, the current progress (waypoints), and the current target coordinates.

-   `.basefinder help`
    -   **Description**: Displays a help message with a summary of all commands and examples.

## Utility Commands

-   `.clear-bases`
    -   **Description**: Clears the internal list of bases that have already been found and reported. This is useful if you want the mod to notify you about a previously found base again.

-   `.clear-players`
    -   **Description**: Clears the list of players that have been recently detected. This will allow the mod to send a new notification for a player that is still in the area.
