package com.grace.annihilatetnt.registry;

import com.grace.annihilatetnt.AnnihilateTNT;
import com.grace.annihilatetnt.entity.PrimedAnnihilateTNT;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, AnnihilateTNT.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<PrimedAnnihilateTNT>> PRIMED_ANNIHILATE_TNT =
            ENTITY_TYPES.register("primed_annihilate_tnt", () ->
                    EntityType.Builder.<PrimedAnnihilateTNT>of(PrimedAnnihilateTNT::new, MobCategory.MISC)
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build("primed_annihilate_tnt")
            );
}
