# Base Finder

A Meteor client addon for finding bases on anarchy servers. This mod is designed to help players discover new bases by tracking player-placed blocks.

## Features

- **Base Finding Module**: The core of the addon, which actively searches for and records potential base locations.
- **Stuck Detector**: A utility to detect if the player character is stuck, which can be useful during automated exploration.
- **Customizable Commands**:
    - `.basefinder`: The main command to configure the Base Finder module.
    - `.clearbases`: Clears the list of found bases.
    - `.clearplayers`: Clears the list of tracked players.
- **In-game HUD**: A Heads-Up Display to show real-time information about the base finding process.

## Installation

1.  Download the latest version of Base Finder from the [Releases](https://github.com/omtoi101/base-finder/releases) page.
2.  Make sure you have [Meteor Client](https://meteorclient.com/) installed.
3.  Place the downloaded `.jar` file into your `mods` folder.
4.  Launch Minecraft with Fabric.

## Usage

Once in-game, you can enable the `BaseFinder` module through the Meteor Client GUI. Use the following commands to manage the addon:

-   `.basefinder help`: Displays help for the basefinder command.
-   `.clearbases`: Deletes all saved base locations.
-   `.clearplayers`: Deletes all tracked player data.

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
    git clone https://github.com/omtoi101/base-finder.git
    cd base-finder
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
