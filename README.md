# PolyCraftingTable

A Fabric mod that adds a universal 3×3 crafting table for vanilla, modded, and Polymer-backed recipes. Polymer-backed results stay out of the vanilla crafting recipe book, but remain available through this table and Polydex.

## Features

- Craft standard crafting recipes, including shaped, shapeless, and supported special recipes.
- Keep ordinary recipes in vanilla crafting and its recipe book; exclude only Polymer-backed results.
- Open Polydex from the table or sneak-right-click the table with an empty hand.
- Use recent Polydex items to autofill ingredients from inventory or open their recipe pages.
- Play on dedicated servers or in singleplayer.

## Use

Install the mod and its dependencies on the server and client. In singleplayer, install them in your game instance.

Operators can get the table with:

```mcfunction
/give @s polycraftingtable:polymer_crafting_table
```

Right-click the table to craft. Left-click a recent item to autofill the grid; right-click it to view its Polydex page. Opening Polydex returns any remaining ingredients to your inventory.

The blank GUI spacer requires the Polymer server resource pack. Regenerate and redistribute that pack after updating this mod, then have players accept/reload it. Without the pack, the separator remains an inert empty inventory slot; the table still works.

## Build

GitHub Actions builds the mod on pushes and pull requests. Download the JAR from the `polymer-crafting-table` artifact in a workflow run.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Polymer Core and Resource Pack
- Polydex
