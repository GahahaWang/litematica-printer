# Litematica Printer

![GitHub issues](https://img.shields.io/github/issues-raw/aleksilassila/litematica-printer)
![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/aleksilassila/litematica-printer)
![GitHub all releases](https://img.shields.io/github/downloads/aleksilassila/litematica-printer/total)
![GitHub Repo stars](https://img.shields.io/github/stars/aleksilassila/litematica-printer)

This extension adds printing functionality for [Litematica fabric](https://github.com/maruohon/litematica). Printer
allows players to build big structures more quickly by automatically placing the correct blocks around you.

![Demo](printer_demo.gif)

## Installation

1. Download and install [Fabric](https://fabricmc.net/use/installer/) if you haven't already.
2. Download the latest Litematica Printer release for your Minecraft version from the
   [releases page](https://github.com/aleksilassila/litematica-printer/releases) (The files can be found under
   "assets").
3. Download [Litematica + MaLiLib](https://www.curseforge.com/minecraft/mc-mods/litematica)
   and [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api/) (≠ Fabric).
4. Place the downloaded .jar files in your `mods/` folder.

[If this is the first fabric mod you are installing, here's an informative video on how to install Fabric mods.](https://www.youtube.com/watch?v=x7gmfib4gHg)

## How To Use

Using the printer is straightforward: You can toggle the feature by pressing `CAPS_LOCK` by default. To configure
variables such as
printing speed and range, open Litematica's settings by pressing `M + C` and navigate to "Generic" tab. Printer's
configuration can be
found at the bottom of the page. You can also rebind the printing toggle under "Hotkeys" tab. Holding down `V` by
default will also
print regardless if the printer is toggled on or off.

## Issues

If you have issues with the printer, **do not** bother the original creator of
Litematica (maruohon) with them. Contact me instead. Feature requests or bugs can
be reported via [GitHub issues](https://github.com/aleksilassila/litematica-printer/issues),
or in [Discord](https://discord.gg/enypPQh6pz). I'll try to keep a todo list of things
I'm planning to implement and fix, so please look for duplicates there first.

Before creating an issue, make sure you are using the latest version of the mod.
To make fixing bugs easier, include the following information in your issue:

- Minecraft version
- Litematica version
- Printer version
- Detailed description of how to reproduce the issue
- If you can, any additional information, such as error logs, screenshots or **the incorrectly printed schematics**.

### List of know issues

Currently, the following features are still broken or missing:

- Placing liquids (printing **in** liquids works though)
- Printing without support directly in air (printInAir)
- Current algorithm for placing rails isn't perfect,
  sometimes it can't place all the rails (to avoid placing anything incorrectly).
- Legit mode? (for anticheats)

Also, I have decided that features that fix existing builds,
such as automatic excavation or correcting incorrectly placed blocks are out of the scope of this mod.

## Building and Contributing

One source tree, `src/main`, builds the mod for every supported Minecraft version (26.1.2, 26.2 and 26.3)
with [FastMultiversion](https://github.com/GahahaWang/FastMultiversion). Each version is a Gradle project,
such as `:26.2` in `versions/26.2`, set up by `gradle/version/version.gradle`. The dependencies of each
version are in `gradle.properties` (`mc.<version>.<key>`).

Code that differs between versions is ordinary Java: `Mc.is("[26.3,)")` for statements and expressions,
`@Mc.Is("[26.3,)")` for declarations. Most of it is in `multiversion/Multiversion.java`. A build fails when code uses
Minecraft, malilib or Litematica API in a version that does not have it, and says where it exists.

Contributions are welcome and appreciated!

Useful gradle tasks:

- `buildAllVersions`
    - Build every version; the jars are in `build/libs/` (`litematica-printer-<Minecraft version>-<version>.jar`).
- `:26.3:runClient`
    - Start the given Minecraft version (in `versions/26.3/run`).
- `generateApiDatabase`
    - Update the list of each version's API that the version check uses (any build also does).
