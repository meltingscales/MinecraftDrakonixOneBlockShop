package com.drakonix.oneblockshop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.border.WorldBorder;

// ponytail: plain fill instead of a custom background texture, swap for real panel art later.
public class ShopScreen extends AbstractContainerScreen<ShopMenu>
{
    private static final int TAB_Y = 18;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_WIDTH = 44;
    private static final int SELL_INFO_Y = 56;
    private static final int INFO_Y = 36;
    private static final int ACTION_Y = 50;
    private static final int BUY_COLS = 2;
    private static final int BUY_ROW_HEIGHT = 16;
    private static final int BUY_COL_WIDTH = 92;
    // Border tab needs an extra warning line the buy grid doesn't, so its button sits lower
    // than ACTION_Y (which the buy grid still uses unchanged).
    private static final int BORDER_WARNING_Y = 46;
    private static final int BORDER_ACTION_Y = 58;
    // Packs tab also has a second description line (see PACKS_SUBINFO_Y), same reason.
    private static final int PACKS_SUBINFO_Y = 46;
    private static final int PACKS_ACTION_Y = 58;
    private static final int PACKS_ROW_HEIGHT = 22;
    // Explore tab's second (cave-only) button sits right below the normal one.
    private static final int EXPEDITION_CAVE_ACTION_Y = ACTION_Y + 22;

    // The buy grid's row count depends on however many offers are in pricing/buy_offers.json,
    // so the player inventory (and the whole GUI) is sized to fit it rather than a fixed guess -
    // ShopMenu.addSlots reads these same two Y values so the slots and the drawn grid never
    // drift apart.
    private static final int BUY_GRID_ROWS = (ShopMenu.BUY_OFFERS.size() + BUY_COLS - 1) / BUY_COLS;
    static final int PLAYER_INVENTORY_Y = ACTION_Y + BUY_GRID_ROWS * BUY_ROW_HEIGHT + 4;
    static final int HOTBAR_Y = PLAYER_INVENTORY_Y + 3 * 18 + 4;

    // Tab row now has 5 tabs (Sell/Border/Buy/Explore/Packs) - the buy grid alone no longer
    // guarantees a wide enough background, so the image width is whichever of the two is wider.
    private static final int TAB_ROW_WIDTH = ShopMenu.Tab.values().length * TAB_WIDTH + (ShopMenu.Tab.values().length - 1) * 2;

    private Button sellTabButton;
    private Button borderTabButton;
    private Button buyTabButton;
    private Button expeditionTabButton;
    private Button packsTabButton;
    private Button expandButton;
    private Button teleportButton;
    private Button teleportCaveButton;
    private final List<Button> buyButtons = new ArrayList<>();
    private final List<Button> packButtons = new ArrayList<>();

    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth = 8 + Math.max(BUY_COLS * BUY_COL_WIDTH, TAB_ROW_WIDTH) + 8;
        this.imageHeight = HOTBAR_Y + 18 + 6;
        this.inventoryLabelY = PLAYER_INVENTORY_Y - 12;
    }

    @Override
    protected void init()
    {
        super.init();

        this.sellTabButton = addRenderableWidget(Button.builder(Component.literal("Sell"), b -> pressTab(ShopMenu.TAB_SELL_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());
        this.borderTabButton = addRenderableWidget(Button.builder(Component.literal("Border"), b -> pressTab(ShopMenu.TAB_BORDER_BUTTON))
                .bounds(this.leftPos + 8 + TAB_WIDTH + 2, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());
        this.buyTabButton = addRenderableWidget(Button.builder(Component.literal("Buy"), b -> pressTab(ShopMenu.TAB_BUY_BUTTON))
                .bounds(this.leftPos + 8 + (TAB_WIDTH + 2) * 2, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());
        this.expeditionTabButton = addRenderableWidget(Button.builder(Component.literal("Explore"), b -> pressTab(ShopMenu.TAB_EXPEDITION_BUTTON))
                .bounds(this.leftPos + 8 + (TAB_WIDTH + 2) * 3, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());
        this.packsTabButton = addRenderableWidget(Button.builder(Component.literal("Packs"), b -> pressTab(ShopMenu.TAB_PACKS_BUTTON))
                .bounds(this.leftPos + 8 + (TAB_WIDTH + 2) * 4, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());

        this.expandButton = addRenderableWidget(Button.builder(Component.literal("Buy expansion"), b -> pressButton(ShopMenu.EXPAND_BORDER_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + BORDER_ACTION_Y, 160, 20).build());
        this.teleportButton = addRenderableWidget(Button.builder(Component.literal("Open Portal"), b -> pressButton(ShopMenu.TELEPORT_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + ACTION_Y, 160, 20).build());
        this.teleportCaveButton = addRenderableWidget(Button.builder(Component.literal("Open Portal (Cave Only)"), b -> pressButton(ShopMenu.TELEPORT_CAVE_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + EXPEDITION_CAVE_ACTION_Y, 160, 20).build());

        this.buyButtons.clear();
        List<ShopMenu.BuyOffer> offers = ShopMenu.BUY_OFFERS;
        for (int i = 0; i < offers.size(); i++)
        {
            ShopMenu.BuyOffer offer = offers.get(i);
            int buttonId = ShopMenu.BUY_ITEM_BUTTON_BASE + i;
            int col = i % BUY_COLS;
            int row = i / BUY_COLS;
            Button button = addRenderableWidget(Button.builder(buyLabel(offer), b -> pressButton(buttonId))
                    .bounds(this.leftPos + 8 + col * BUY_COL_WIDTH, this.topPos + ACTION_Y + row * BUY_ROW_HEIGHT, BUY_COL_WIDTH - 4, BUY_ROW_HEIGHT - 2).build());
            this.buyButtons.add(button);
        }

        this.packButtons.clear();
        List<StarterPacks.Pack> packs = StarterPacks.PACKS;
        for (int i = 0; i < packs.size(); i++)
        {
            int buttonId = ShopMenu.PACK_CLAIM_BUTTON_BASE + i;
            Button button = addRenderableWidget(Button.builder(Component.literal(packs.get(i).label()), b -> pressButton(buttonId))
                    .bounds(this.leftPos + 8, this.topPos + PACKS_ACTION_Y + i * PACKS_ROW_HEIGHT, TAB_ROW_WIDTH, 20).build());
            this.packButtons.add(button);
        }

        syncWidgets();
    }

    // AbstractContainerScreen.render() no longer calls renderTooltip itself (every vanilla
    // screen - ContainerScreen, InventoryScreen, etc - calls it explicitly after super.render());
    // without this override, no tooltip (vanilla or ours) ever showed.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void pressTab(int buttonId)
    {
        this.menu.clickMenuButton(this.minecraft.player, buttonId);
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        syncWidgets();
    }

    private void pressButton(int buttonId)
    {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
    }

    // No "Buy " prefix - the tab it's on already says that, and several offer names (Mangrove
    // Propagule, Dark Oak Sapling) are long enough that every character counts in a ~88px button.
    private static Component buyLabel(ShopMenu.BuyOffer offer)
    {
        return Component.literal(offer.item().getDescription().getString() + " (" + offer.price() + ")");
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        syncWidgets();
    }

    // Real Button widgets stay put; this just toggles visibility/labels/afford-state off the
    // menu's authoritative tab + balance every client tick, same idiom vanilla multi-state
    // screens use (AbstractContainerScreen.containerTick()).
    private void syncWidgets()
    {
        ShopMenu.Tab tab = this.menu.getActiveTab();
        this.expandButton.visible = tab == ShopMenu.Tab.BORDER;
        this.teleportButton.visible = tab == ShopMenu.Tab.EXPEDITION;
        this.teleportCaveButton.visible = tab == ShopMenu.Tab.EXPEDITION;
        for (Button button : this.buyButtons)
            button.visible = tab == ShopMenu.Tab.BUY;
        for (Button button : this.packButtons)
            button.visible = tab == ShopMenu.Tab.PACKS;

        if (tab == ShopMenu.Tab.BORDER && this.minecraft.level != null)
        {
            WorldBorder border = this.minecraft.level.getWorldBorder();
            long cost = Border.costForNextExpansion(border);
            int cooldown = this.menu.getExpandCooldownSeconds();
            if (cooldown > 0)
            {
                this.expandButton.setMessage(Component.literal("Wait " + cooldown + "s..."));
                this.expandButton.active = false;
            }
            else
            {
                this.expandButton.setMessage(Component.literal("Buy expansion: " + cost));
                this.expandButton.active = this.menu.getBalance() >= cost;
            }
        }

        if (tab == ShopMenu.Tab.BUY)
        {
            List<ShopMenu.BuyOffer> offers = ShopMenu.BUY_OFFERS;
            for (int i = 0; i < this.buyButtons.size() && i < offers.size(); i++)
                this.buyButtons.get(i).active = this.menu.getBalance() >= offers.get(i).price();
        }

        if (tab == ShopMenu.Tab.EXPEDITION)
        {
            int remaining = this.menu.getPortalRemainingSeconds();
            if (remaining > 0)
            {
                Component status = Component.literal("Portal open (" + formatDuration(remaining) + ") - walk in!");
                this.teleportButton.setMessage(status);
                this.teleportButton.active = false;
                this.teleportCaveButton.setMessage(status);
                this.teleportCaveButton.active = false;
            }
            else
            {
                this.teleportButton.setMessage(Component.literal("Open Portal"));
                this.teleportButton.active = true;
                this.teleportCaveButton.setMessage(Component.literal("Open Portal (Cave Only)"));
                this.teleportCaveButton.active = true;
            }
        }

        if (tab == ShopMenu.Tab.PACKS)
        {
            List<StarterPacks.Pack> packs = StarterPacks.PACKS;
            for (int i = 0; i < this.packButtons.size() && i < packs.size(); i++)
            {
                Button button = this.packButtons.get(i);
                int cooldown = this.menu.getPackCooldownSeconds(i);
                if (cooldown > 0)
                {
                    button.setMessage(Component.literal(packs.get(i).label() + " - wait " + formatDuration(cooldown)));
                    button.active = false;
                }
                else
                {
                    button.setMessage(Component.literal("Claim: " + packs.get(i).label()));
                    button.active = true;
                }
            }
        }
    }

    private static String formatDuration(int seconds)
    {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // Sell tab: show what an item would fetch when hovered, same tooltip mechanism vanilla
    // uses for enchantment/durability info lines.
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack)
    {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.menu.getActiveTab() == ShopMenu.Tab.SELL && this.minecraft.level != null)
        {
            if (stack.getEnchantmentLevel(this.minecraft.level.registryAccess().holderOrThrow(OneBlockShopMod.UNSELLABLE)) > 0)
                tooltip.add(Component.literal("Not sellable").withStyle(ChatFormatting.RED));
            else
            {
                long price = Pricing.priceOf(stack.getItem(), this.minecraft.level.getRecipeManager(), this.minecraft.level.registryAccess());
                tooltip.add(Component.literal("Sell price: " + price + " each").withStyle(ChatFormatting.GREEN));
            }
        }
        return tooltip;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xC0101010);
        if (this.menu.getActiveTab() == ShopMenu.Tab.SELL)
            graphics.fill(x + 79, y + 34, x + 79 + 18, y + 34 + 18, 0xFF404040);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(this.font, "Balance: " + this.menu.getBalance(), 8, 16, 0x40FF40, false);

        if (this.menu.getActiveTab() == ShopMenu.Tab.SELL)
            graphics.drawString(this.font, "Drop items to sell", 8, SELL_INFO_Y, 0xA0A0A0, false);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.BORDER && this.minecraft.level != null)
        {
            graphics.drawString(this.font, "Border size: " + (int) this.minecraft.level.getWorldBorder().getSize(), 8, INFO_Y, 0xFFFFFF, false);
            graphics.drawString(this.font, "Warning: expanding summons a monster wave!", 8, BORDER_WARNING_Y, 0xFF5555, false);
        }
        else if (this.menu.getActiveTab() == ShopMenu.Tab.BUY)
            graphics.drawString(this.font, "Buy what you can't produce yet", 8, INFO_Y, 0xA0A0A0, false);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.EXPEDITION)
            graphics.drawString(this.font, "Opens a portal above the shop - walk in to go", 8, INFO_Y, 0xA0A0A0, false);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.PACKS)
        {
            graphics.drawString(this.font, "Free tech mod starter kits - an easier way to get going", 8, INFO_Y, 0xA0A0A0, false);
            graphics.drawString(this.font, "One claim per pack every hour, no cost", 8, PACKS_SUBINFO_Y, 0xA0A0A0, false);
        }
    }
}
