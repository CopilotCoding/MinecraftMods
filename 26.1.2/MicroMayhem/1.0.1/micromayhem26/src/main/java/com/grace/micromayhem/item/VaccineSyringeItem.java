package com.grace.micromayhem.item;

import com.grace.micromayhem.util.NbtHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class VaccineSyringeItem extends SyringeItem {

    public VaccineSyringeItem(Properties props) { super(props); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isLoaded(stack) && NbtHelper.hasTag(stack) && NbtHelper.getTag(stack).contains("StrainIds")) {
            NbtHelper.updateTag(stack, tag -> {
                tag.putBoolean("Loaded", true);
                tag.putBoolean(NBT_IS_VACCINE, true);
            });
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("§a[VACCINE SYRINGE]").withStyle(ChatFormatting.BOLD));
        if (isLoaded(stack)) {
            long[] ids = getStrainIds(stack);
            lines.accept(Component.literal("§7Provides immunity to §f" + ids.length + " strain(s)."));
        }
        lines.accept(Component.literal("§7Right-click self or entity to administer."));
    }
}
