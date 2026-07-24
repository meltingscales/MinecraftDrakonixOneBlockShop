package com.drakonix.oneblockshop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.border.WorldBorder;

// ponytail: plain fills and text-button hitboxes instead of a custom background texture and
// widgets, swap for real art/Button widgets later.
public class ShopScreen extends AbstractContainerScreen<ShopMenu>
{
    private static final int TAB_Y = 26;
    private static final int SELL_TAB_X = 8;
    private static final int BORDER_TAB_X = 40;
    private static final int INFO_Y = 36;
    private static final int ACTION_Y = 46;

    private boolean borderTabActive;

    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xC0101010);
        if (!this.borderTabActive)
            graphics.fill(x + 79, y + 34, x + 79 + 18, y + 34 + 18, 0xFF404040);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "Balance: " + this.menu.getBalance(), 8, 16, 0x40FF40, false);

        graphics.drawString(this.font, "Sell", SELL_TAB_X, TAB_Y, this.borderTabActive ? 0x808080 : 0xFFFFFF, false);
        graphics.drawString(this.font, "Border", BORDER_TAB_X, TAB_Y, this.borderTabActive ? 0xFFFFFF : 0x808080, false);

        if (this.borderTabActive)
        {
            WorldBorder border = this.minecraft.level.getWorldBorder();
            long cost = Border.costForNextExpansion(border);
            boolean canAfford = this.menu.getBalance() >= cost;
            graphics.drawString(this.font, "Border size: " + (int) border.getSize(), 8, INFO_Y, 0xFFFFFF, false);
            graphics.drawString(this.font, "[Buy expansion: " + cost + "]", 8, ACTION_Y, canAfford ? 0x40FF40 : 0xFF4040, false);
        }
        else
        {
            graphics.drawString(this.font, "Drop items to sell", 8, INFO_Y, 0xA0A0A0, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (button == 0 && withinLabel(mouseX, mouseY, SELL_TAB_X, TAB_Y, "Sell"))
        {
            this.borderTabActive = false;
            return true;
        }
        if (button == 0 && withinLabel(mouseX, mouseY, BORDER_TAB_X, TAB_Y, "Border"))
        {
            this.borderTabActive = true;
            return true;
        }
        if (button == 0 && this.borderTabActive)
        {
            long cost = Border.costForNextExpansion(this.minecraft.level.getWorldBorder());
            if (withinLabel(mouseX, mouseY, 8, ACTION_Y, "[Buy expansion: " + cost + "]"))
            {
                this.menu.clickMenuButton(this.minecraft.player, ShopMenu.EXPAND_BORDER_BUTTON);
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ShopMenu.EXPAND_BORDER_BUTTON);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean withinLabel(double mouseX, double mouseY, int relX, int relY, String text)
    {
        int x0 = this.leftPos + relX;
        int y0 = this.topPos + relY;
        return mouseX >= x0 && mouseX < x0 + this.font.width(text) && mouseY >= y0 && mouseY < y0 + this.font.lineHeight;
    }
}
