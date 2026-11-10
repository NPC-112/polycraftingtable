"""Validate portable Polydex recipes and the inventory-return wiring."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/polycraftingtable"


def check(condition, message):
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print(f"PASS {message}")


def recipe(name):
    return json.loads((DATA / "recipe" / f"{name}.json").read_text())


def advancement(name):
    return json.loads(
        (DATA / "advancement/recipes/misc" / f"{name}.json").read_text()
    )


def verify_recipe(name, ingredients):
    value = recipe(name)
    check(value.get("type") == "minecraft:crafting_shapeless",
          f"{name} is shapeless")
    check(value.get("ingredients") == ingredients,
          f"{name} has the expected ingredients")
    check(value.get("result") == {"id": "polycraftingtable:polydex", "count": 1},
          f"{name} produces one Polydex item")

    unlock = advancement(name)
    criteria = unlock.get("criteria", {})
    ingredient_criterion = next(
        (criterion for key, criterion in criteria.items() if key != "has_the_recipe"),
        {},
    )
    check(unlock.get("parent") == "minecraft:recipes/root",
          f"{name} advancement joins the recipe book tree")
    check(criteria.get("has_the_recipe", {}).get("conditions", {}).get("recipes")
          == f"polycraftingtable:{name}",
          f"{name} advancement unlocks the correct recipe ID")
    check(ingredient_criterion.get("trigger") == "minecraft:inventory_changed"
          and ingredient_criterion.get("conditions", {}).get("items")
          == [{"items": item} for item in ingredients],
          f"{name} advancement checks its ingredients")
    check(unlock.get("rewards", {}).get("recipes") == [f"polycraftingtable:{name}"],
          f"{name} advancement grants the recipe")


table_recipe = json.loads(
    (DATA / "recipe/polymer_crafting_table.json").read_text()
)
check(table_recipe.get("ingredients") == ["polycraftingtable:polydex"],
      "the Polymer Crafting Table requires one Polydex item")
check(table_recipe.get("result") == {
    "id": "polycraftingtable:polymer_crafting_table",
    "count": 1,
}, "the Polydex item recipe produces one Polymer Crafting Table")

verify_recipe(
    "polydex_from_vanilla_table",
    ["minecraft:book", "minecraft:crafting_table"],
)
verify_recipe(
    "polydex_from_polymer_table",
    ["polycraftingtable:polymer_crafting_table"],
)

mod_json = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
check(not mod_json.get("entrypoints", {}).get("client"),
      "the mod does not require a client entrypoint")
check(not (
    ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolyCraftingTableClient.java"
).exists() and not (
    ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/OpenPlayerInventoryPayload.java"
).exists(), "client-only inventory-return code is removed")

mixins = json.loads((ROOT / "src/main/resources/polycraftingtable.mixins.json").read_text())
check("InventoryMenuMixin" in mixins.get("mixins", []),
      "inventory click interception is registered")

item_source = (
    ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolydexItem.java"
).read_text()
check("super(properties, Items.KNOWLEDGE_BOOK)" in item_source,
      "Polymer disguises the item as a knowledge book")
check("PolyCraftingTableMod.openPolydexFromHand(serverPlayer)" in item_source,
      "hand use opens Polydex without consuming the item")

click_source = (
    ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/mixin/InventoryMenuMixin.java"
).read_text()
check("input != ContainerInput.PICKUP" in click_source
      and "button != 1" in click_source
      and "slotId >= ((AbstractContainerMenu) (Object) this).slots.size()" in click_source
      and "PolydexReturnSession.openFromMenu(serverPlayer)" in click_source
      and "ci.cancel()" in click_source,
      "right-clicking the item in any menu opens Polydex without moving it")

session_source = (
    ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolydexReturnSession.java"
).read_text()
check("openFromMenu(ServerPlayer player)" in session_source
      and "open(player, null, () -> PolydexMenu.openMainMenu(player), true)" in session_source,
      "inventory-item opens do not require client code or an inventory-return session")
check("ServerPlayNetworking" not in session_source
      and "PLAYER_INVENTORY" not in session_source,
      "Polydex navigation no longer sends client-only return packets")
language = json.loads(
    (ROOT / "src/main/resources/assets/polycraftingtable/lang/en_us.json").read_text()
)
check("message.polycraftingtable.client_required" not in language,
      "the client-install message is removed")
print("PASS portable Polydex recipe and interaction contracts")
