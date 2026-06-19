# Schematic Auto Builder

Minecraft 26.2 Fabric client mod for one-button schematic building.

This companion mod controls Baritone's builder process and adds a grouped
settings terminal for Litematica/Schematica build workflows.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.x
- Baritone for 26.2
- Litematica is recommended for placed schematics

## Controls

- No default `J` key is used.
- Assign `Open Auto Builder Menu` and `Pause/Resume Auto Builder` in Minecraft's Controls menu.
- With the bundled Baritone build, open Baritone settings and choose `全自動建築モードを開く`.

## Features

- Auto Build mode: starts building from the placed Litematica/Schematica schematic.
- Clear Area mode: opens the Baritone terrain-clearing menu.
- Register material chests by looking at a chest and pressing the menu button.
- Automatically checks registered material chests when Baritone pauses from missing materials.
- Shows `資材が足りません` when registered chests do not contain usable schematic materials.
- Visits each registered material chest once per refill pass, so empty chests do not open/close in a loop.
- Uses Baritone's exact schematic builder settings for direction-sensitive placement and block repair.
- GitHub manifest updater from the in-game menu.

## Build

```powershell
.\gradlew.bat remapJar
```

Output:

```text
build/libs/schematic-autobuilder-0.2.7+26.2.jar
```
