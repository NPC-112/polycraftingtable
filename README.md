# polycraftingtable

Custom crafting table for Minecraft vanilla, modded, and Polymer recipes. Developed to reduce the vanilla recipe-book payload and the chance of recipe-book "packet too big" errors without removing recipes from the server registry.

## Current implementation

- Loads on dedicated servers and in singleplayer (install the mod and its dependencies in the singleplayer instance).
- Adds a server-driven Polymer crafting table that appears as a vanilla crafting table to unmodded remote clients.
- Opens a dedicated crafting screen with a Polydex-style 3×3 layout for all registered crafting recipes: vanilla, ordinary modded, and Polymer-backed recipes.
- Uses Polydex's textured crafting background and transparent slot model when the Polymer resource pack is accepted; otherwise it shows a readable vanilla fallback.
- Treats the result as a craft button rather than a takeable item, so items with right-click actions cannot open another menu before the crafted output is safely delivered.
- On each successful batch, records the crafted-item statistic, invokes the vanilla item crafting callback on a detached copy, triggers the recipe-crafted advancement, and unlocks non-special recipes through the outgoing recipe-book filter.
- A knowledge-book button opens Polydex's main index, and sneak-right-clicking the table opens the same menu. Up to seven recent Polydex item entries appear in the bottom row beside a blank spacer and the Polydex button. Left-clicking a recent item tries to fill the open table from inventory; right-clicking opens that item's Polydex recipe page.
- Excludes only Polymer-backed crafting displays from initial vanilla recipe-book sync and subsequent recipe unlock packets. Ordinary recipes remain available in the vanilla book; Polydex and the custom table retain access to the full server recipe registry.
- GitHub Actions builds the mod on Java 25 for Minecraft 26.3 and stores the JAR as a workflow artifact.

The custom table matches every recipe registered with the standard crafting recipe type, in server registry order. This includes shaped, shapeless, and special/dynamic crafting recipes that match its 3×3 grid. Recipes for furnaces, stonecutters, smithing tables, or custom machines are not supported by this table.

Polymer's server-item detection API selects which results are excluded from vanilla crafting and its recipe book, including adapter-backed items and server-only item components. This does not exclude every recipe owned by a mod that depends on Polymer: ordinary outputs remain available in vanilla crafting. Polydex provides the standard recipe lookup and search rather than a duplicate custom category.

Ordinary crafting result lookup is blocked only for Polymer-backed results, while the custom table matches all crafting recipes directly. The recipes and player unlocks are never deleted; only outgoing Polymer-backed vanilla recipe-book entries are filtered. This reduces that packet's payload, not every registry or network packet.

## Testing

Use `/give @s polycraftingtable:polymer_crafting_table` as an operator. Place it and right-click to craft, or sneak-right-click with an empty hand for Polydex. The custom screen's knowledge-book button opens the Polydex main index; opening it returns the table's remaining ingredients to the player's inventory. Recent Polydex item entries appear in the bottom row, with a blank non-clickable space before the Polydex button. Left-click an entry to fill the open crafting grid from inventory; supported shaped and shapeless recipes may be partially filled when ingredients are missing. If the grid already contains items, autofill refuses without changing the grid or inventory. Right-click an entry to open its Polydex recipe page.

Accept the Polymer resource pack to see Polydex's crafting background and transparent slot model. Check the recent bar with empty and populated history, including an item with more than one visible crafting recipe and a stale or unsupported entry. Verify left-click fills without consuming unrelated items, an occupied grid remains unchanged with a message, and right-click opens the matching Polydex item page. Confirm the spacer stays visually blank, the Polydex button remains clickable, and Polydex page navigation works without injected controls. To check output safety, try normal and shift-clicks on an item whose right-click action opens another menu. Crafting should put the item in the inventory without opening that menu; using the item from the inventory should open it and return any remaining table inputs. If the output or its remainders will not fit, crafting should leave the recipe untouched and ask for inventory space.

For recipe coverage, test vanilla shaped recipes (such as a chest), shapeless recipes (such as planks), a special recipe (such as repairing two damaged tools), an ordinary mod recipe, and a Polymer-backed recipe in the custom table. Test a cake recipe to check bucket remainders, and repeat with a full inventory to confirm that a rejected craft consumes nothing. Vanilla and ordinary mod recipes should still work in the vanilla table and appear in its book when unlocked; Polymer-backed recipes should not. Polydex should continue to show all of them. Repeat on a dedicated server and in singleplayer.

The Polydex main-index, recipe-page, recent-entry, and screen-texture integrations use implementation classes because the current Polydex release does not expose public helpers for these entry points. Recent items are kept per connection by Polydex and are not persistent across reconnects. These dependencies may need an update when changing Polydex versions. The mod no longer injects controls into Polydex's own screens.

Craft completion uses Minecraft's standard item callback, recipe-crafted advancement trigger, and recipe-book award APIs. It does not synthesize loader-specific crafting events or callbacks provided only by individual modded workstations; those hooks are not exposed through the shared Fabric crafting-recipe interface.

The build is verified through GitHub Actions. In-game testing of dedicated-server and singleplayer behavior is still required.

## Build

GitHub Actions runs `gradle build` on every push and pull request. The output JAR is available from the workflow run's artifacts.

## Dependencies

- Minecraft 26.3
- Fabric Loader 0.19.5+
- Fabric API
- Polymer Core
- Polydex
