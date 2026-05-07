package com.grace.micromayhem.menu;

import com.grace.micromayhem.blockentity.CentrifugeBlockEntity;
import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CentrifugeMenu extends AbstractContainerMenu {

    public static final int SLOT_INPUT        = 0;  // (17,17) ready dish
    public static final int SLOT_EMPTY_START  = 1;  // (17,44) and (17,62)
    public static final int SLOT_EMPTY_COUNT  = 2;
    public static final int SLOT_OUTPUT_START = 3;  // (107,17/35/53)
    public static final int SLOT_OUTPUT_COUNT = 3;
    public static final int CONTAINER_SLOTS   = 6;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX      = 1;
    public static final int DATA_COUNT    = 2;

    private final Container container;
    private final ContainerData data;
    public final CentrifugeBlockEntity blockEntity;

    public CentrifugeMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        this(windowId, playerInv, new SimpleContainer(CONTAINER_SLOTS), new SimpleContainerData(DATA_COUNT), null);
    }

    public CentrifugeMenu(int windowId, Inventory playerInv, Container container,
                          ContainerData data, CentrifugeBlockEntity be) {
        super(ModMenuTypes.CENTRIFUGE.get(), windowId);
        this.container = container; this.data = data; this.blockEntity = be;
        checkContainerSize(container, CONTAINER_SLOTS);
        checkContainerDataCount(data, DATA_COUNT);

        addSlot(new Slot(container, SLOT_INPUT, 16, 17) {
            @Override public boolean mayPlace(ItemStack s) {
                return s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.READY;
            }
        });
        for (int i = 0; i < SLOT_EMPTY_COUNT; i++) {
            final int fi = i;
            addSlot(new Slot(container, SLOT_EMPTY_START + i, 16, 44 + i * 18) {
                @Override public boolean mayPlace(ItemStack s) {
                    return s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY;
                }
            });
        }
        for (int i = 0; i < SLOT_OUTPUT_COUNT; i++) {
            addSlot(new Slot(container, SLOT_OUTPUT_START + i, 107, 17 + i * 18) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return true; }
            });
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 156));

        addDataSlots(data);
    }

    public int getProgress()    { return data.get(DATA_PROGRESS); }
    public int getMaxProgress() { return data.get(DATA_MAX); }
    public boolean isRunning()  { return getMaxProgress() > 0 && getProgress() < getMaxProgress(); }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(); ItemStack copy = stack.copy();
        if (index < CONTAINER_SLOTS) { if (!moveItemStackTo(stack, CONTAINER_SLOTS, slots.size(), true)) return ItemStack.EMPTY; }
        else { if (!moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)) return ItemStack.EMPTY; }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
