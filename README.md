# Schematic Auto Builder

Minecraft 26.2 Fabric client mod for one-button schematic building.

This is a companion mod. It does not replace Baritone; it controls the Baritone
builder process and adds a small settings terminal for Litematica/Schematica
build workflows.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.x
- Baritone for 26.2
- Litematica is recommended for placed schematics

## Controls

- Press `J` in game to open the Schematic Auto Builder menu.

## Features

- One-button start for placed Litematica/Schematica schematics.
- Register material chests by looking at a chest and pressing the menu button.
- Fetch building materials from registered chests.
- Optional auto-start after material fetching.
- GitHub manifest updater from the in-game menu.

## Current Material Fetching Behavior

The first version fetches likely building materials from registered chests:
block items, sticks, iron ingots, diamonds, and cobblestone.

It is intentionally separate from the Baritone clearing project. The existing
Baritone build engine still handles placement, direction-sensitive blocks, and
pathing.

## Build

```powershell
.\gradlew.bat remapJar
```

Output:

```text
build/libs/schematic-autobuilder-0.1.0+26.2.jar
```

