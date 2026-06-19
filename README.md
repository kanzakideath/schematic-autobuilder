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
- Press `K` in game to pause or resume automation.
- Both keybinds are configurable in Minecraft's Controls menu.

## Features

- One-button start for placed Litematica/Schematica schematics.
- Register material chests by looking at a chest and pressing the menu button.
- Automatically switches to registered material chests when Baritone pauses from missing materials.
- Returns to the placed schematic and restarts the builder after a successful refill.
- Visits each registered material chest once per refill pass, so empty chests do not open/close in a loop.
- Uses Baritone's exact schematic builder settings: direction-sensitive blocks are not ignored, and incorrect existing blocks are repaired by Baritone.
- Optional auto-resume after material fetching.
- GitHub manifest updater from the in-game menu.

## Current Material Fetching Behavior

When Baritone exposes the active build's incorrect positions, the refill pass
targets item types that match the schematic's missing desired block states. If
that snapshot is unavailable, it falls back to likely building materials such as
block items, sticks, iron ingots, diamonds, cobblestone, redstone, and string.

It is intentionally separate from the Baritone clearing project. The existing
Baritone build engine still handles placement, direction-sensitive blocks, and
pathing.

## Build

```powershell
.\gradlew.bat remapJar
```

Output:

```text
build/libs/schematic-autobuilder-0.2.0+26.2.jar
```
