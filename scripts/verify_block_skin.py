"""Verify the textured Polymer table model and safe no-pack fallback."""
import json
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/polycraftingtable"


def check(condition, message):
    if not condition:
        raise SystemExit(f"FAIL {message}")
    print("PASS", message)


block_model = json.loads(
    (ASSETS / "models/block/polymer_crafting_table.json").read_text()
)
check(block_model.get("parent") == "minecraft:block/cube",
      "custom table keeps a full-cube block shape")

textures = block_model.get("textures", {})
expected_faces = {"particle", "down", "up", "north", "south", "east", "west"}
check(expected_faces.issubset(textures), "all block faces and the particle texture are defined")

for texture_id in set(textures.values()):
    namespace, path = texture_id.split(":", 1)
    texture_path = ROOT / f"src/main/resources/assets/{namespace}/textures/{path}.png"
    image = texture_path.read_bytes()
    check(image.startswith(b"\x89PNG\r\n\x1a\n"), f"{texture_path.name} is a PNG texture")
    width, height = struct.unpack(">II", image[16:24])
    check(width == 16 and height == 16, f"{texture_path.name} is a 16x16 pixel texture")

item_model = json.loads(
    (ASSETS / "items/polymer_crafting_table.json").read_text()
)
item_model_data = item_model.get("model", {})
check(item_model_data.get("type") == "minecraft:model"
      and item_model_data.get("model") == "polycraftingtable:block/polymer_crafting_table",
      "the item icon reuses the custom block model")

block = (ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolymerCraftingTableBlock.java").read_text()
mod = (ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolyCraftingTableMod.java").read_text()
item = (ROOT / "src/main/java/dev/dragongamer112/polycraftingtable/PolymerCraftingTableItem.java").read_text()
metadata = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text())
build = (ROOT / "build.gradle").read_text()
readme = (ROOT / "README.md").read_text()

check("implements PolymerTexturedBlock" in block,
      "the Polymer textured-block interface is enabled")
check("PolymerBlockResourceUtils.requestBlock" in mod
      and "BlockModelType.FULL_BLOCK" in mod
      and "block/polymer_crafting_table" in mod,
      "the custom full-block model is registered")
check("PolymerResourcePackUtils.hasMainPack(context)" in mod
      and "Blocks.CRAFTING_TABLE.defaultBlockState()" in mod,
      "players without the server pack see the vanilla crafting table")
check("getPolymerBlockState" in block
      and "getPolymerBreakEventBlockState" in block
      and block.count("clientTableBlockState(context)") == 2,
      "the pack-aware appearance is used for block and break visuals")
check("hasTexturedTableAppearance(context)" in item
      and "? PolyCraftingTableMod.TABLE_ITEM_MODEL_ID" in item
      and ": null" in item,
      "the item icon also falls back when the pack is not accepted")
check("polymer-blocks" in metadata.get("depends", {})
      and 'modImplementation include("eu.pb4:polymer-blocks:${project.polymer_version}")' in build,
      "the matching Polymer Blocks module is bundled and declared")
check("must accept the pack" in readme
      and "vanilla crafting-table appearance" in readme,
      "the resource-pack requirement and fallback are documented")

mod_jars = [
    path for path in (ROOT / "build/libs").glob("*.jar")
    if not path.name.endswith("-sources.jar")
]
if mod_jars:
    with zipfile.ZipFile(mod_jars[0]) as archive:
        packaged = set(archive.namelist())
        required_assets = {
            "assets/polycraftingtable/models/block/polymer_crafting_table.json",
            "assets/polycraftingtable/items/polymer_crafting_table.json",
            "assets/polycraftingtable/textures/block/polymer_crafting_table_top.png",
            "assets/polycraftingtable/textures/block/polymer_crafting_table_side.png",
            "assets/polycraftingtable/textures/block/polymer_crafting_table_bottom.png",
        }
        check(required_assets.issubset(packaged),
              "the mod jar contains every custom block and item asset")
        bundled_block_module = any(
            path.startswith("META-INF/jars/") and "polymer-blocks-" in path and path.endswith(".jar")
            for path in packaged
        )
        check(bundled_block_module, "Polymer Blocks is bundled in the mod jar")
else:
    print("SKIP built-jar checks; no Gradle build output is present")

print("PASS custom block-skin assets and fallbacks verified")
