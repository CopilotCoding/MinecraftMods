package com.grace.micromayhem.menu;

import com.grace.micromayhem.blockentity.IrradiatorBlockEntity;
import com.grace.micromayhem.item.PetriDishItem;
import com.grace.micromayhem.microbe.EvolutionPressure;
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
import net.minecraft.world.item.Items;

public class IrradiatorMenu extends AbstractContainerMenu {

    // Unique inputs (top-left group)
    public static final int SLOT_DISH       = 0;  // (8,17)
    public static final int SLOT_PRESSURE   = 1;  // (26,17)
    public static final int SLOT_FUEL       = 2;  // (8,35)
    // Empty dish inputs (bottom-left group, separated)
    public static final int SLOT_EMPTY_START = 3; // 2x2 at (8,62),(26,62),(8,80),(26,80)
    public static final int SLOT_EMPTY_COUNT = 4;
    // Output slots (right)
    public static final int SLOT_OUT_START  = 7;  // 2x2 at (116,17),(134,17),(116,35),(134,35)
    public static final int SLOT_OUT_COUNT  = 4;
    public static final int CONTAINER_SLOTS = 11;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX      = 1;
    public static final int DATA_COUNT    = 2;

    private final Container container;
    private final ContainerData data;
    public final IrradiatorBlockEntity blockEntity;

    public IrradiatorMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        this(windowId, playerInv, new SimpleContainer(CONTAINER_SLOTS), new SimpleContainerData(DATA_COUNT), null);
    }

    public IrradiatorMenu(int windowId, Inventory playerInv, Container container,
                          ContainerData data, IrradiatorBlockEntity be) {
        super(ModMenuTypes.IRRADIATOR.get(), windowId);
        this.container = container; this.data = data; this.blockEntity = be;
        checkContainerSize(container, CONTAINER_SLOTS);
        checkContainerDataCount(data, DATA_COUNT);

        // Unique input group
        addSlot(new Slot(container, SLOT_DISH, 8, 17) {
            @Override public boolean mayPlace(ItemStack s) {
                return s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.READY;
            }
        });
        addSlot(new Slot(container, SLOT_PRESSURE, 26, 17) {
            @Override public boolean mayPlace(ItemStack s) { return EvolutionPressure.fromItem(s) != null; }
        });
        addSlot(new Slot(container, SLOT_FUEL, 8, 35) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(Items.GLOWSTONE_DUST); }
        });

        // Empty dish input group (separated below)
        for (int i = 0; i < SLOT_EMPTY_COUNT; i++) {
            addSlot(new Slot(container, SLOT_EMPTY_START + i, 8 + (i % 2) * 18, 62 + (i / 2) * 18) {
                @Override public boolean mayPlace(ItemStack s) {
                    return s.getItem() instanceof PetriDishItem && PetriDishItem.getState(s) == PetriDishItem.DishState.EMPTY;
                }
            });
        }

        // Output slots
        for (int i = 0; i < SLOT_OUT_COUNT; i++) {
            addSlot(new Slot(container, SLOT_OUT_START + i, 116 + (i % 2) * 18, 17 + (i / 2) * 18) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
                @Override public boolean mayPickup(Player p) { return true; }
            });
        }

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 116 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 8 + col * 18, 174));

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
