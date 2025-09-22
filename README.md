# Stash-Hunter

A Meteor client addon for finding stashes on anarchy servers. This mod is designed to help players discover new stashes by tracking player-placed blocks.

## Features

- **Stash Finding Module**: The core of the addon, which actively searches for and records potential stash locations.
- **Dynamic Chunk Trail Searching**: Automatically follows trails of newly generated chunks to find player activity.
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

Once in-game, you can enable the `StashHunter` module through the Meteor Client GUI. Use the following commands to manage the addon:

-   `.stashhunter help`: Displays help for the stashhunter command.

The HUD can be enabled and configured from the Meteor Client HUD settings.

## Documentation

For more detailed information, please see the following documents:

-   [**Features**](./docs/FEATURES.md): A detailed overview of all features.
-   [**Usage**](./docs/USAGE.md): In-depth instructions for all commands.
-   [**Configuration**](./docs/CONFIGURATION.md): A guide to all available settings.

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
