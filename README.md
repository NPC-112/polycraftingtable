# PolyCraftingTable

This mod restricts configured crafting recipes from vanilla crafting and its recipe book and adds a new crafting table with extended features.
The custom crafting table and Polydex retain access to every registered crafting recipe.

Was mainly made to reduce the chances of a player getting a recipe book packet being too larger error (io.netty.handler.codec.EncoderException: java.lang.IllegalArgumentException: Packet too big ). This error isn't exclusive to recipe book packets players may receive as they unlock recipes, but this is the most common for me.

I am sorry this is vibecoded. Use at your own risk
- Multiple chances for lost items at the moment. Has stopped happening for me but not every mod does things the same way.

Operators can get the new items with commands:

```Command
/give @s polycraftingtable:polymer_crafting_table
/give @s polycraftingtable:polydex
```

Craft a Polydex with a minecraft:crafting_table and minecraft:book.
Once you have a Polydex, you can convert it to and from a Polymer Crafting Table by placing it into a crafting grid. They are each others recipes.


The recipe is also unlocked in the vanilla recipe book once you have the required ingredients.

## Features

- Craft standard crafting recipes, including shaped, shapeless, and supported special recipes.
- Configure vanilla crafting and recipe-book restrictions for any mod, not just Polymer.
- Open Polydex from within the table or sneak-right-click the table with an empty hand.
- Autofill recently viewed polydex recipes as long as the player has all the components.
- If there are multiple possible recipes the player can currently craft for one item, selecting the recipe will cycle through filling each different recipe.
- Runs on dedicated servers and/or singleplayer.

## Use

Install the mod and its dependencies on the server, or in single player. The custom table skin is delivered through Polymer's generated server resource pack; clients do not need to install this mod, but must accept the pack to see the skin. If they decline it or the custom model is unavailable, the table and its item use the vanilla crafting-table appearance. Polymer Blocks is bundled with this mod.

Right-click the table to craft. Left-click a recent item to fill a recipe; repeated clicks cycle through the fully fillable recipe variants.
The table remembers the last successfully filled recipe for each recent item across visits and tries it first when you return.
The current grid and your inventory are both considered, so unused grid items are returned to your inventory when the recipe changes.
If no complete recipe fits, or unused items cannot safely fit in your inventory, the grid and inventory remain unchanged.
Right-click a recent item to view its Polydex page.
Opening Polydex or another inventory from the crafting screen returns any remaining ingredients to your inventory.
When closing the table, inventory overflow is dropped near the player, as with vanilla crafting screens.
Closing that screen returns to the table with an empty grid and refreshed recent items.

## Crafting restrictions

On first startup, `config/polycraftingtable.json` is created with:

```json
{
  "vanillaCraftingExemptions": [
    "minecraft:",
    "polycraftingtable:"
  ],
  "restrictedRecipeIds": []
}
```

Rule priority:

1. An exact recipe ID in `restrictedRecipeIds` is blocked in vanilla, even if its output is exempt.
2. Otherwise, a matching output item or namespace in `vanillaCraftingExemptions` allows vanilla crafting.
3. Everything else is restricted to the custom table.

Examples:

- `some_mod:` exempts all registered output items in that namespace.
- `some_mod:special_item` exempts just that item, not other items in the mod.
- `polycraftingtable:polymer_crafting_table` can replace `polycraftingtable:` if only the table should be exempt.
- Putting `minecraft:diamond_from_coal` in `restrictedRecipeIds` blocks that specific recipe, if installed, even though `minecraft:` outputs are exempt. Other diamond recipes are unaffected.

Exemptions match the **real output item ID**, not the recipe's namespace or Polymer's client-side disguise.
Custom component-based items that genuinely use an ID such as `minecraft:paper` also match `minecraft:`;
use exact recipe restrictions to block those individually. Recipe restrictions require full IDs: no namespace shortcuts, wildcards, tags, or component matching.

Restart the server or game to apply edits; `/reload` does not reload this configuration.
Existing files are never rewritten, and empty lists are respected. Both fields are required.
Invalid JSON, unknown fields, wrong types, or malformed rules stop initialization with an error naming the file.
Unknown but valid IDs/namespaces are retained and warned about once registries and datapacks are available.
Remove an exemption to restrict it, rather than expecting removed defaults to be added back automatically.

Restrictions affect standard crafting recipes, not smelting, stonecutting, or smithing.
The custom table and Polydex remain unfiltered, and saved recipe unlocks are preserved.
Allowed book entries still follow normal unlock rules. Unresolved or mixed allowed/restricted output displays
are hidden conservatively from the vanilla book; valid-input crafting is evaluated independently.
Other mods' independent machines that bypass vanilla lookups are not guaranteed to follow these rules.

## Build

GitHub Actions builds the mod on pushes and pull requests. Download the JAR from the `polymer-crafting-table` artifact in a workflow run.

The build also runs configuration/policy unit checks and verifies hook contracts against the pinned Minecraft
bytecode. Reports are in `crafting-policy-test-reports`. These checks do not boot Minecraft or apply Fabric
mixins in a running game, and no Fabric GameTests are included.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Polymer Core and Resource Pack
- Polymer Blocks (bundled with this mod)
- Polydex
