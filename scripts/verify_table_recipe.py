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
check(recipe.get("ingredients") == ["polycraftingtable:polydex"],
      "requires exactly one Polydex item")
check(recipe.get("result") == {"id": RECIPE_ID, "count": 1},
      "produces one custom table item")

advancement = json.loads(ADVANCEMENT.read_text())
criteria = advancement.get("criteria", {})
check(advancement.get("parent") == "minecraft:recipes/root", "recipe advancement joins the recipe book tree")
check(criteria.get("has_polydex", {}).get("trigger") == "minecraft:inventory_changed"
      and criteria["has_polydex"].get("conditions", {}).get("items")
      == [{"items": "polycraftingtable:polydex"}],
      "recipe-book unlock checks for a Polydex item")
check(criteria.get("has_the_recipe", {}).get("conditions", {}).get("recipes") == RECIPE_ID,
      "recipe-unlocked criterion uses the exact recipe ID")
check(advancement.get("rewards", {}).get("recipes") == [RECIPE_ID],
      "advancement unlocks the recipe")
check(advancement.get("requirements") == [["has_the_recipe", "has_polydex"]],
      "either valid recipe unlock or Polydex inventory awards the recipe")

version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
               if line.startswith("mod_version="))
check(version == "0.17.2", "mod version is 0.17.2")
print("PASS table recipe and advancement JSON parsed successfully")
