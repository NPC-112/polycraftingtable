"""Check our interception contracts against the actual pinned Minecraft bytecode.

This is not a live Minecraft test and does not boot Fabric or apply mixins.
Run after compilation in CI; fails when a supported caller or descriptor changes.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "src/main/java/dev/dragongamer112/polycraftingtable"


def fetch_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def method(code, name):
    """Select all overloads, retaining their descriptors and instructions."""
    return "\n".join(
        block for block in re.split(r"\n(?=  (?:public|protected|private) )", code)
        if re.search(rf"\b{re.escape(name)}\(", block.split("Code:")[0])
    )


def require(condition, message):
    if not condition:
        raise AssertionError(message)
    print("PASS", message)


def verify(game_jar):
    def bytecode(name):
        return subprocess.check_output(
            ["javap", "-classpath", str(game_jar), "-p", "-c", "-s", name], text=True
        )

    manager = bytecode("net.minecraft.world.item.crafting.RecipeManager")
    manager_mixin = (PACKAGE / "mixin/RecipeManagerMixin.java").read_text()
    targets = re.findall(r'method = "(getRecipeFor[^"]+)"', manager_mixin)
    require(len(targets) == 3, "all three vanilla recipe-manager overloads intercepted")
    for target in targets:
        name, descriptor = target.split("(", 1)
        require("descriptor: (" + descriptor in method(manager, name),
                "pinned manager descriptor exists: " + target)
    require(manager_mixin.count('at = @At("HEAD")') == 3
            and manager_mixin.count("filterResult(type, input, level, cir);") == 3
            and "VanillaCraftingLookup.matchingRecipe(" in manager_mixin,
            "normal, resource-key and holder hints route to the ordered policy lookup")

    crafting = method(bytecode("net.minecraft.world.inventory.CraftingMenu"), "slotChangedCraftingGrid")
    inventory = method(bytecode("net.minecraft.world.inventory.InventoryMenu"), "slotsChanged")
    require("RecipeManager.getRecipeFor:" in crafting,
            "manual 3x3 crafting calls intercepted recipe-manager lookup")
    require("CraftingMenu.slotChangedCraftingGrid:" in inventory,
            "manual 2x2 crafting shares the intercepted result computation")

    crafter = bytecode("net.minecraft.world.level.block.CrafterBlock")
    crafter_mixin = (PACKAGE / "mixin/CrafterBlockMixin.java").read_text()
    crafter_target = re.search(r'method = "(getPotentialResults[^"]+)"', crafter_mixin).group(1)
    name, descriptor = crafter_target.split("(", 1)
    require("descriptor: (" + descriptor in method(crafter, name),
            "pinned Crafter interception descriptor exists")
    dispense = method(crafter, "dispenseFrom")
    lookup_position = dispense.index("getPotentialResults:")
    assembly_position = dispense.index("CraftingRecipe.assemble:")
    require(lookup_position < assembly_position and "Optional.isEmpty:" in dispense[lookup_position:assembly_position],
            "Crafter rejects a missing candidate before assembling or consuming ingredients")
    require("VanillaCraftingLookup.matchingRecipe(level.recipeAccess(), input, level)" in crafter_mixin,
            "Crafter bypasses stale input-only cache and re-evaluates ordered matching")
    require('at = @At("HEAD")' in crafter_mixin and "cancellable = true" in crafter_mixin,
            "Crafter interception replaces cached results before use")

    book = bytecode("net.minecraft.stats.ServerRecipeBook")
    book_mixin = (PACKAGE / "mixin/ServerRecipeBookMixin.java").read_text()
    require('method = {"addRecipes", "sendInitialRecipeBook"}' in book_mixin,
            "initial book synchronization and later unlocks share the filter")
    for name in ("addRecipes", "sendInitialRecipeBook"):
        body = method(book, name)
        require("ClientboundRecipeBookAddPacket" in body
                and "ServerGamePacketListenerImpl.send:" in body,
                "pinned book packet interception exists: " + name)
    require("info.parent().id().identifier()" in book_mixin
            and "RecipeBookFilter.shouldIncludeCraftingDisplay(" in book_mixin,
            "book filter receives the real parent recipe ID")

    full_lookup = (PACKAGE / "CraftingRecipeLookup.java").read_text()
    require("CraftingRestrictions" not in full_lookup and "byType(RecipeType.CRAFTING)" in full_lookup,
            "custom table retains unfiltered registry access")
    lookup = (PACKAGE / "VanillaCraftingLookup.java").read_text()
    require("RecipeSelection.firstAllowed(" in lookup
            and lookup.index(".matches(input, level)") < lookup.index(".assemble(input)"),
            "restricted lookup uses match-before-output ordered selector")
    book_filter = (PACKAGE / "RecipeBookFilter.java").read_text()
    require("resolveForStacks" in book_filter and ".assemble(" not in book_filter,
            "book discovery uses declared displays, never fabricated crafting inputs")
    mixins = json.loads((ROOT / "src/main/resources/polycraftingtable.mixins.json").read_text())["mixins"]
    require(all(name in mixins for name in ("RecipeManagerAccessor", "RecipeManagerMixin",
                                          "CrafterBlockMixin", "ServerRecipeBookMixin")),
            "all recipe-policy mixins registered")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--game-jar", type=Path, help="Already extracted pinned server jar for local inspection")
    args = parser.parse_args()
    if args.game_jar:
        verify(args.game_jar)
        return
    version = re.search(r"^minecraft_version=(.+)$",
                        (ROOT / "gradle.properties").read_text(), re.MULTILINE).group(1)
    manifest = fetch_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
    metadata = fetch_json(next(v["url"] for v in manifest["versions"] if v["id"] == version))
    download = metadata["downloads"]["server"]
    with urllib.request.urlopen(download["url"], timeout=120) as response:
        bundle = response.read()
    require(hashlib.sha1(bundle).hexdigest() == download["sha1"], "official pinned server bundle checksum")
    with tempfile.TemporaryDirectory(prefix="crafting-hook-contracts-") as directory:
        with zipfile.ZipFile(io.BytesIO(bundle)) as archive:
            jar = Path(directory) / "server.jar"
            jar.write_bytes(archive.read(f"META-INF/versions/{version}/server-{version}.jar"))
        verify(jar)


if __name__ == "__main__":
    main()
