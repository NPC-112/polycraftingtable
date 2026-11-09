"""Validate the custom table's datapack recipe, unlock advancement and version."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECIPE_ID = "polycraftingtable:polymer_crafting_table"
RECIPE = ROOT / "src/main/resources/data/polycraftingtable/recipe/polymer_crafting_table.json"
ADVANCEMENT = ROOT / "src/main/resources/data/polycraftingtable/advancement/recipes/misc/polymer_crafting_table.json"


def check(condition, message):
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print(f"PASS {message}")


recipe = json.loads(RECIPE.read_text())
check(recipe.get("type") == "minecraft:crafting_shapeless", "recipe is shapeless")
check(recipe.get("ingredients") == ["minecraft:crafting_table", "minecraft:book"],
      "requires exactly one crafting table and one book")
check(recipe.get("result") == {"id": RECIPE_ID, "count": 1},
      "produces one custom table item")

advancement = json.loads(ADVANCEMENT.read_text())
criteria = advancement.get("criteria", {})
check(advancement.get("parent") == "minecraft:recipes/root", "recipe advancement joins the recipe book tree")
check(criteria.get("has_both_ingredients", {}).get("trigger") == "minecraft:inventory_changed"
      and criteria["has_both_ingredients"].get("conditions", {}).get("items")
      == [{"items": "minecraft:crafting_table"}, {"items": "minecraft:book"}],
      "recipe-book unlock checks both ingredients")
check(criteria.get("has_the_recipe", {}).get("conditions", {}).get("recipes") == RECIPE_ID,
      "recipe-unlocked criterion uses the exact recipe ID")
check(advancement.get("rewards", {}).get("recipes") == [RECIPE_ID],
      "advancement unlocks the recipe")
check(advancement.get("requirements") == [["has_the_recipe", "has_both_ingredients"]],
      "either valid recipe unlock or both ingredients awards the recipe")

version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
               if line.startswith("mod_version="))
check(version == "0.17.1", "mod version is 0.17.1")
print("PASS table recipe and advancement JSON parsed successfully")
