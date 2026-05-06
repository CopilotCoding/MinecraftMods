package com.grace.micromayhem.item;

import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sample Bag — portable 54-slot storage that only accepts Swabs and Petri Dishes.
 *
 * Inventory is stored as NBT inside the item's CustomData component.
 * Opens a standard double-chest GUI (6×9) when right-clicked.
 * Saves inventory back to the item when closed.
 *
 * Cannot be placed inside itself.
 */
public class SampleBagItem extends Item {

    public static final int ROWS = 6;
    public static final int COLS = 9;
    public static final int SIZE = ROWS * COLS; // 54

    private static final String NBT_INVENTORY = "SampleBagInventory";

    public SampleBagItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Build container backed by bag NBT
            SampleBagContainer container = new SampleBagContainer(bag);

            serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Sample Bag");
                }

                @Override
                public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                        int windowId, Inventory playerInv, Player p) {
                    return new SampleBagMenu(windowId, playerInv, container, bag, hand);
                }
            });
        }

        return InteractionResult.SUCCESS;
    }

    // ---- Tooltip ----

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        CompoundTag tag = NbtHelper.getTag(stack);
        if (!tag.contains(NBT_INVENTORY)) {
            lines.accept(Component.literal("§7Empty. Right-click to open."));
            lines.accept(Component.literal("§7Holds swabs and petri dishes only."));
            return;
        }

        ListTag inv = tag.getListOrEmpty(NBT_INVENTORY);
        int contamSwabs = 0, readyDishes = 0, emptySwabs = 0, emptyDishes = 0, culturingDishes = 0;
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag slot = inv.getCompoundOrEmpty(i);
            if (!slot.contains("id")) continue;
            String id = slot.getStringOr("id", "");
            CompoundTag slotData = slot.contains("components") ? slot.getCompoundOrEmpty("components") : new CompoundTag();
            if (id.contains("swab")) {
                if (slot.contains("tag") && slot.getCompoundOrEmpty("tag").getBooleanOr("Contaminated", false)) contamSwabs++;
                else emptySwabs++;
            } else if (id.contains("petri_dish")) {
                String state = slot.contains("tag") ? slot.getCompoundOrEmpty("tag").getStringOr("DishState", "") : "";
                if ("READY".equals(state)) readyDishes++;
                else if ("CULTURING".equals(state)) culturingDishes++;
                else emptyDishes++;
            }
        }

        int total = contamSwabs + readyDishes + emptySwabs + emptyDishes + culturingDishes;
        lines.accept(Component.literal("§7Contents: §f" + total + "/54 slots used"));
        if (contamSwabs  > 0) lines.accept(Component.literal("§c  Contaminated swabs: §f" + contamSwabs));
        if (emptySwabs   > 0) lines.accept(Component.literal("§7  Sterile swabs: §f" + emptySwabs));
        if (readyDishes  > 0) lines.accept(Component.literal("§a  Ready cultures: §f" + readyDishes));
        if (culturingDishes > 0) lines.accept(Component.literal("§e  Culturing: §f" + culturingDishes));
        if (emptyDishes  > 0) lines.accept(Component.literal("§7  Empty dishes: §f" + emptyDishes));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = NbtHelper.getTag(stack);
        if (!tag.contains(NBT_INVENTORY)) return false;
        // Glow if any ready cultures inside
        ListTag inv = tag.getListOrEmpty(NBT_INVENTORY);
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag slot = inv.getCompoundOrEmpty(i);
            if (slot.contains("tag") && "READY".equals(slot.getCompoundOrEmpty("tag").getStringOr("DishState", "")))
                return true;
        }
        return false;
    }

    // ---- Inner container class ----

    /**
     * A SimpleContainer that:
     * - Only accepts Swabs and Petri Dishes
     * - Saves its contents back to the bag ItemStack on change
     */
    public static class SampleBagContainer extends SimpleContainer {

        private final ItemStack bagStack;

        public SampleBagContainer(ItemStack bagStack) {
            super(SIZE);
            this.bagStack = bagStack;
            load();
        }

        private void load() {
            CompoundTag tag = NbtHelper.getTag(bagStack);
            if (!tag.contains(NBT_INVENTORY)) return;
            ListTag list = tag.getListOrEmpty(NBT_INVENTORY);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompoundOrEmpty(i);
                int slot = entry.getIntOr("Slot", 0);
                if (slot < 0 || slot >= SIZE) continue;
                // Reconstruct ItemStack from stored id + count + data tag
                String itemId = entry.getStringOr("id", "");
                int count = entry.getIntOr("count", 0);
                net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(itemId)).orElse(net.minecraft.world.item.Items.AIR);
                if (item == null) continue;
                ItemStack loaded = new ItemStack(item, count);
                // Restore CustomData NBT if present
                if (entry.contains("data")) {
                    NbtHelper.setTag(loaded, entry.getCompoundOrEmpty("data"));
                }
                setItem(slot, loaded);
            }
        }

        public void save() {
            ListTag list = new ListTag();
            for (int i = 0; i < SIZE; i++) {
                ItemStack stack = getItem(i);
                if (stack.isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", i);
                entry.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString());
                entry.putInt("count", stack.getCount());
                CompoundTag data = NbtHelper.getTag(stack);
                if (!data.isEmpty()) entry.put("data", data);
                list.add(entry);
            }
            NbtHelper.updateTag(bagStack, tag -> tag.put(NBT_INVENTORY, list));
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isValidItem(stack);
        }

        public static boolean isValidItem(ItemStack stack) {
            return stack.getItem() instanceof SwabItem || stack.getItem() instanceof PetriDishItem;
        }
    }

    // ---- Menu class ----

    /**
     * Double chest menu (6×9) backed by SampleBagContainer.
     * Uses custom FilteredSlot objects to actually block non-swab/dish items.
     * Saves the container back to the bag on close.
     */
    public static class SampleBagMenu extends net.minecraft.world.inventory.AbstractContainerMenu {

        private final SampleBagContainer bagContainer;
        private final ItemStack bagStack;
        private final InteractionHand hand;
        private final Inventory playerInventory;

        public SampleBagMenu(int windowId, Inventory playerInv,
                             SampleBagContainer container, ItemStack bagStack, InteractionHand hand) {
            super(MenuType.GENERIC_9x6, windowId);
            this.bagContainer = container;
            this.bagStack = bagStack;
            this.hand = hand;
            this.playerInventory = playerInv;

            // Add 54 filtered bag slots (6 rows × 9 cols)
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = col + row * 9;
                    this.addSlot(new FilteredSlot(container, slotIndex, 8 + col * 18, 18 + row * 18));
                }
            }

            // Add player inventory slots (3 rows)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    this.addSlot(new net.minecraft.world.inventory.Slot(
                        playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
                }
            }

            // Add player hotbar slots
            for (int col = 0; col < 9; col++) {
                this.addSlot(new net.minecraft.world.inventory.Slot(
                    playerInv, col, 8 + col * 18, 198));
            }
        }

        @Override
        public boolean stillValid(Player player) {
            // Check player still holds a sample bag in either hand — don't use == (reference breaks on item move)
            return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof SampleBagItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof SampleBagItem;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            ItemStack result = ItemStack.EMPTY;
            net.minecraft.world.inventory.Slot slot = this.slots.get(index);

            if (slot.hasItem()) {
                ItemStack slotStack = slot.getItem();
                result = slotStack.copy();

                if (index < SIZE) {
                    // Moving from bag to player inventory
                    if (!this.moveItemStackTo(slotStack, SIZE, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Moving from player inventory to bag — only if valid, never the bag itself
                    if (slotStack.getItem() instanceof SampleBagItem) return ItemStack.EMPTY;
                    if (!SampleBagContainer.isValidItem(slotStack)) return ItemStack.EMPTY;
                    if (!this.moveItemStackTo(slotStack, 0, SIZE, false)) {
                        return ItemStack.EMPTY;
                    }
                }

                if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
                else slot.setChanged();
            }

            return result;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            bagContainer.save();
        }

        // Filtered slot — only accepts swabs and petri dishes, never the bag itself
        private static class FilteredSlot extends net.minecraft.world.inventory.Slot {
            public FilteredSlot(net.minecraft.world.Container container, int index, int x, int y) {
                super(container, index, x, y);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                // Explicitly block sample bags from going inside themselves
                if (stack.getItem() instanceof SampleBagItem) return false;
                return SampleBagContainer.isValidItem(stack);
            }
        }
    }
}
