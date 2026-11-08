# PolyCraftingTable

A Fabric mod that adds a universal 3×3 crafting table for vanilla, modded, and Polymer-backed recipes. Polymer-backed results stay out of the vanilla crafting recipe book, but remain available through this table and Polydex. 
Was mainly made to reduce the chances of a player getting a recipe book packet being too larger error (io.netty.handler.codec.EncoderException: java.lang.IllegalArgumentException: Packet too big ). This error isn't exclusive to recipe book packets players receive as they unlock recipes but this is the most common for me. 

I am sorry this is vibecoded. use at your own risk 

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

Right-click the table to craft. Left-click a recent item to autofill the grid; right-click it to view its Polydex page. Opening Polydex or another inventory from the crafting screen returns any remaining ingredients to your inventory. Closing that screen returns to the table with an empty grid and refreshed recent items, provided the table is still loaded and nearby. Nested screens stay open until you finish using them. Closing the crafting screen itself exits normally, as do Polydex and inventories opened independently.

## Build

GitHub Actions builds the mod on pushes and pull requests. Download the JAR from the `polymer-crafting-table` artifact in a workflow run.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Polymer Core and Resource Pack
- Polydex
