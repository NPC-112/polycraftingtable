package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.PolydexMenu;
import eu.pb4.polydex.api.v1.recipe.PolydexPage;
import eu.pb4.polydex.impl.book.ui.GuiUtils;
import eu.pb4.polydex.impl.book.ui.PageAware;
import eu.pb4.polydex.impl.book.ui.PageViewerGui;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PageViewerGui.class)
abstract class PolydexPageViewerMixin {
    @Shadow
    @Final
    protected List<PolydexPage> pages;

    @Shadow
    protected int page;

    @Shadow
    public abstract ServerPlayer getPlayer();

    @Shadow
    public abstract int getPage();

    @Shadow
    public abstract int getPageAmount();

    @Shadow
    protected abstract void setupNavigator();

    @Shadow
    public abstract GuiElement filler();

    @Shadow
    public abstract void setSlot(int index, GuiElement element);

    @Unique
    private boolean polycraftingtable$autofillButtonVisible;

    @Inject(method = "updateDisplay", at = @At("TAIL"))
    private void polycraftingtable$updateAutofillControl(CallbackInfo callbackInfo) {
        ServerPlayer player = this.getPlayer();
        if (!PolydexMenu.hasValidTableOrigin(player) || this.page < 0 || this.page >= this.pages.size()) {
            this.polycraftingtable$restoreNavigator();
            return;
        }

        PolydexPage selectedPage = this.pages.get(this.page);
        CraftingRecipe recipe = PolydexMenu.recipeForPage(selectedPage);
        if (recipe == null) {
            this.polycraftingtable$restoreNavigator();
            return;
        }

        if (this.getPageAmount() > 1) {
            PageAware pageAware = (PageAware) this;
            this.setSlot(47, GuiUtils.previousPage(player, pageAware));
            this.setSlot(48, GuiUtils.page(player, this.getPage() + 1, this.getPageAmount()).build());
            this.setSlot(49, GuiUtils.nextPage(player, pageAware));
        } else {
            this.setSlot(47, this.filler());
            this.setSlot(48, this.filler());
            this.setSlot(49, this.filler());
        }

        this.setSlot(50, new GuiElementBuilder(Items.EMERALD)
                .setName(Component.literal("+ Fill crafting grid"))
                .setCallback((index, clickType, action, gui) ->
                        PolydexMenu.returnToTable(player, recipe))
                .build());
        this.polycraftingtable$autofillButtonVisible = true;
    }

    @Unique
    private void polycraftingtable$restoreNavigator() {
        if (this.polycraftingtable$autofillButtonVisible) {
            this.setupNavigator();
            this.polycraftingtable$autofillButtonVisible = false;
        }
    }
}
