package com.drakonix.oneblockshop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
    // Packs tab also has a second description line (drawn dynamically below the first, since it
    // can wrap - see renderLabels), same reason its button sits lower than ACTION_Y.
    private static final int PACKS_ACTION_Y = 58;
    private static final int PACKS_ROW_HEIGHT = 22;
    // Explore tab's second (cave-only) button sits right below the normal one.
    private static final int EXPEDITION_CAVE_ACTION_Y = ACTION_Y + 22;
    // Settings tab (PlayerSettings): randomize-prices toggle, then the expedition-minutes
    // +/- stepper, then the permanent hard mode lock, one row each.
    private static final int SETTINGS_ACTION_Y = 58;
    private static final int SETTINGS_ROW_HEIGHT = 22;
    private static final int SETTINGS_STEPPER_BUTTON_WIDTH = 20;

    // The buy grid's row count depends on however many offers are in pricing/buy_offers.json,
    // so the player inventory (and the whole GUI) is sized to fit it rather than a fixed guess -
    // ShopMenu.addSlots reads these same two Y values so the slots and the drawn grid never
    // drift apart.
    private static final int BUY_GRID_ROWS = (ShopMenu.BUY_OFFERS.size() + BUY_COLS - 1) / BUY_COLS;
    static final int PLAYER_INVENTORY_Y = ACTION_Y + BUY_GRID_ROWS * BUY_ROW_HEIGHT + 4;
    static final int HOTBAR_Y = PLAYER_INVENTORY_Y + 3 * 18 + 4;

    // Tab row now has 6 tabs (Sell/Border/Buy/Explore/Packs/Settings) - the buy grid alone no
    // longer guarantees a wide enough background, so the image width is whichever of the two is
    // wider.
    private static final int TAB_ROW_WIDTH = ShopMenu.Tab.values().length * TAB_WIDTH + (ShopMenu.Tab.values().length - 1) * 2;

    private Button sellTabButton;
    private Button borderTabButton;
    private Button buyTabButton;
    private Button expeditionTabButton;
    private Button packsTabButton;
    private Button settingsTabButton;
    private Button expandButton;
    private Button teleportButton;
    private Button teleportCaveButton;
    private Button randomizeToggleButton;
    private Button minutesDownButton;
    private Button minutesUpButton;
    private Button hardModeButton;
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
        this.settingsTabButton = addRenderableWidget(Button.builder(Component.literal("Settings"), b -> pressTab(ShopMenu.TAB_SETTINGS_BUTTON))
                .bounds(this.leftPos + 8 + (TAB_WIDTH + 2) * 5, this.topPos + TAB_Y, TAB_WIDTH, TAB_HEIGHT).build());

        this.expandButton = addRenderableWidget(Button.builder(Component.literal("Buy expansion"), b -> pressButton(ShopMenu.EXPAND_BORDER_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + BORDER_ACTION_Y, 160, 20).build());
        this.teleportButton = addRenderableWidget(Button.builder(Component.literal("Open Portal"), b -> pressButton(ShopMenu.TELEPORT_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + ACTION_Y, 160, 20).build());
        this.teleportCaveButton = addRenderableWidget(Button.builder(Component.literal("Open Portal (Cave Only)"), b -> pressButton(ShopMenu.TELEPORT_CAVE_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + EXPEDITION_CAVE_ACTION_Y, 160, 20).build());

        this.randomizeToggleButton = addRenderableWidget(Button.builder(Component.literal("Randomize Prices"), b -> pressButton(ShopMenu.RANDOMIZE_TOGGLE_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + SETTINGS_ACTION_Y, TAB_ROW_WIDTH, 20).build());
        this.minutesDownButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> pressButton(ShopMenu.EXPEDITION_MINUTES_DOWN_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + SETTINGS_ACTION_Y + SETTINGS_ROW_HEIGHT, SETTINGS_STEPPER_BUTTON_WIDTH, 20).build());
        this.minutesUpButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> pressButton(ShopMenu.EXPEDITION_MINUTES_UP_BUTTON))
                .bounds(this.leftPos + 8 + SETTINGS_STEPPER_BUTTON_WIDTH + 4, this.topPos + SETTINGS_ACTION_Y + SETTINGS_ROW_HEIGHT, SETTINGS_STEPPER_BUTTON_WIDTH, 20).build());
        this.hardModeButton = addRenderableWidget(Button.builder(Component.literal("Enable Permanent Hard Mode"), b -> pressButton(ShopMenu.HARD_MODE_BUTTON))
                .bounds(this.leftPos + 8, this.topPos + SETTINGS_ACTION_Y + SETTINGS_ROW_HEIGHT * 2, TAB_ROW_WIDTH, 20).build());

        this.buyButtons.clear();
        List<ShopMenu.BuyOffer> offers = ShopMenu.BUY_OFFERS;
        for (int i = 0; i < offers.size(); i++)
        {
            ShopMenu.BuyOffer offer = offers.get(i);
            int buttonId = ShopMenu.BUY_ITEM_BUTTON_BASE + i;
            int col = i % BUY_COLS;
            int row = i / BUY_COLS;
            Button button = addRenderableWidget(Button.builder(buyLabel(offer, offer.price()), b -> pressButton(buttonId))
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
    // Takes the price separately (rather than reading offer.price() itself) since syncWidgets
    // re-labels every tick with the live, possibly-randomized price from the menu.
    private static Component buyLabel(ShopMenu.BuyOffer offer, long price)
    {
        return Component.literal(offer.item().getDescription().getString() + " (" + price + ")");
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
        this.randomizeToggleButton.visible = tab == ShopMenu.Tab.SETTINGS;
        this.minutesDownButton.visible = tab == ShopMenu.Tab.SETTINGS;
        this.minutesUpButton.visible = tab == ShopMenu.Tab.SETTINGS;
        this.hardModeButton.visible = tab == ShopMenu.Tab.SETTINGS;

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
            {
                long price = this.menu.getBuyPrice(i);
                Button button = this.buyButtons.get(i);
                button.setMessage(buyLabel(offers.get(i), price));
                button.active = this.menu.getBalance() >= price;
            }
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

        if (tab == ShopMenu.Tab.SETTINGS)
        {
            boolean locked = this.menu.isHardModeLocked();
            this.randomizeToggleButton.setMessage(Component.literal(
                    "Randomize Prices: " + (this.menu.isPriceRandomizationEnabled() ? "ON" : "OFF")));
            this.randomizeToggleButton.active = !locked;

            int minutes = this.menu.getExpeditionMinutes();
            this.minutesDownButton.active = !locked && minutes > PlayerSettings.MIN_EXPEDITION_MINUTES;
            this.minutesUpButton.active = !locked && minutes < PlayerSettings.MAX_EXPEDITION_MINUTES;

            if (locked)
            {
                this.hardModeButton.setMessage(Component.literal("Permanent Hard Mode: ON (locked)"));
                this.hardModeButton.active = false;
            }
            else
            {
                this.hardModeButton.setMessage(Component.literal("Enable Permanent Hard Mode"));
                this.hardModeButton.active = true;
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
                price = Pricing.applyRandomization(price, this.menu.getSeedHash(), stack.getItem(), this.menu.isPriceRandomizationEnabled());
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

    // AbstractContainerScreen/GuiGraphics has no built-in multi-line label drawing - several of
    // this screen's info/warning lines are long enough to overflow the GUI's width otherwise.
    // Returns the pixel height consumed so a caller can stack a following line right after it.
    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int color)
    {
        int maxWidth = this.imageWidth - x - 8;
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), maxWidth);
        for (int i = 0; i < lines.size(); i++)
            graphics.drawString(this.font, lines.get(i), x, y + i * this.font.lineHeight, color, false);
        return lines.size() * this.font.lineHeight;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        // Above TAB_Y (18) rather than overlapping it - this used to sit at y=16, clipping into
        // the tab row right below it.
        graphics.drawString(this.font, "Balance: " + this.menu.getBalance(), 8, 6, 0x40FF40, false);

        if (this.menu.getActiveTab() == ShopMenu.Tab.SELL)
            graphics.drawString(this.font, "Drop items to sell", 8, SELL_INFO_Y, 0xA0A0A0, false);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.BORDER && this.minecraft.level != null)
        {
            graphics.drawString(this.font, "Border size: " + (int) this.minecraft.level.getWorldBorder().getSize(), 8, INFO_Y, 0xFFFFFF, false);
            drawWrapped(graphics, "Warning: expanding summons a monster wave!", 8, BORDER_WARNING_Y, 0xFF5555);
        }
        else if (this.menu.getActiveTab() == ShopMenu.Tab.BUY)
            drawWrapped(graphics, "Buy what you can't produce yet", 8, INFO_Y, 0xA0A0A0);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.EXPEDITION)
            drawWrapped(graphics, "Opens a portal above the shop - walk in to go", 8, INFO_Y, 0xA0A0A0);
        else if (this.menu.getActiveTab() == ShopMenu.Tab.PACKS)
        {
            int consumed = drawWrapped(graphics, "Free tech mod starter kits - an easier way to get going", 8, INFO_Y, 0xA0A0A0);
            drawWrapped(graphics, "One claim per pack every hour, no cost", 8, INFO_Y + consumed + 2, 0xA0A0A0);
        }
        else if (this.menu.getActiveTab() == ShopMenu.Tab.SETTINGS)
        {
            drawWrapped(graphics, "Configure your own difficulty options", 8, INFO_Y, 0xA0A0A0);
            graphics.drawString(this.font, "Expedition Minutes: " + this.menu.getExpeditionMinutes(),
                    8 + (SETTINGS_STEPPER_BUTTON_WIDTH + 4) * 2 + 4, SETTINGS_ACTION_Y + SETTINGS_ROW_HEIGHT + 6, 0xFFFFFF, false);
            // Spells out exactly what gets locked - "hard mode is permanent" alone left it
            // unclear what a player was actually committing to before clicking.
            drawWrapped(graphics, "Warning: locks in Randomize Prices and Expedition Minutes for"
                    + " good - only an admin can undo it!", 8, SETTINGS_ACTION_Y + SETTINGS_ROW_HEIGHT * 2 + 24, 0xFF5555);
        }
    }
}
