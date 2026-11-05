# polycraftingtable

Custom crafting table for Minecraft Polymer mods. Developed specifically to reduce number of registered recipes and reduce chances of "packet too big" errors related to number of recipes.

## Current implementation

- Adds a server-side Polymer crafting table that appears as a vanilla crafting table to unmodded clients.
- Opens a dedicated 3×3 crafting screen and only accepts crafting results whose item implements Polymer's `PolymerItem`.
- Sneak-right-click opens the Polydex crafting recipe browser.
- GitHub Actions builds the mod on Java 25 for Minecraft 26.3 and stores the JAR as a workflow artifact.

The current prototype uses the server's normal crafting recipes and filters them at this table. It does not yet remove those recipes from the vanilla recipe book or reduce the initial recipe-sync payload. That requires moving the recipe data into a server-side table recipe format or adding compatibility hooks to the source mods.

## Build

GitHub Actions runs `gradle build` on every push and pull request. The output JAR is available from the workflow run's artifacts.

## Dependencies

- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API
- Polymer Core
- Polydex
