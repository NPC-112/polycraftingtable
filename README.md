# polycraftingtable

Custom crafting table for Minecraft Polymer mods. Developed specifically to reduce number of registered recipes and reduce chances of "packet too big" errors related to number of recipes.

## Current implementation

- Loads on dedicated servers and in singleplayer (install the mod and its dependencies in the singleplayer instance).
- Adds a server-driven Polymer crafting table that appears as a vanilla crafting table to unmodded remote clients.
- Opens a dedicated 3×3 crafting screen for Polymer-backed crafting results, including items registered through Polymer adapters.
- A knowledge-book button opens Polydex's main index. Sneak-right-clicking the table opens the same menu.
- Excludes moved crafting displays from initial vanilla recipe-book sync and subsequent recipe unlock packets. Polydex and the custom table retain access to the full server recipe registry.
- GitHub Actions builds the mod on Java 25 for Minecraft 26.3 and stores the JAR as a workflow artifact.

Recipes are selected by Polymer's server-item detection API, including adapter-backed items and server-only item components. This does not mean every recipe owned by every mod that depends on Polymer: recipes producing ordinary vanilla outputs are not automatically moved. Polydex provides the standard recipe lookup and search rather than a duplicate custom category.

Ordinary crafting result lookup is blocked for moved recipes while the custom table matches them directly. The recipes and player unlocks are never deleted; only outgoing vanilla recipe-book entries are filtered. This reduces that packet's payload, not every registry or network packet.

## Testing

Use `/give @s polycraftingtable:polymer_crafting_table` as an operator. Place it and right-click to craft, or sneak-right-click with an empty hand for Polydex. The custom screen's knowledge-book button opens the same Polydex main index. Opening Polydex closes the crafting screen and returns its remaining ingredients to the player's inventory.

The Polydex main-index integration uses an implementation class because the current Polydex release does not expose a public main-menu opener. That dependency is isolated in `PolydexMenu` and may need an update when changing Polydex versions.

The build is verified through GitHub Actions. In-game testing of dedicated-server and singleplayer behavior is still required.

## Build

GitHub Actions runs `gradle build` on every push and pull request. The output JAR is available from the workflow run's artifacts.

## Dependencies

- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API
- Polymer Core
- Polydex
