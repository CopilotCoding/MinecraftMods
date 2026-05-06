package com.grace.micromayhem.client;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class SicknessParticles {

    private static final int ACTIVE_INTERVAL = 100;
    private static final int SEVERE_INTERVAL = 40;

    // SpellParticleOption.create requires the ParticleType as first arg
    private static SpellParticleOption spell() {
        return SpellParticleOption.create(ParticleTypes.EFFECT, 0xFFFFFF, 1.0f);
    }

    public static void tickMob(LivingEntity mob, ServerLevel level,
                                int activeSeverity, int severeSeverity,
                                long gameTime) {
        if (activeSeverity == 0 && severeSeverity == 0) return;
        double x = mob.getX(), y = mob.getEyeY() - 0.2, z = mob.getZ();

        if (severeSeverity > 0 && gameTime % SEVERE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 5, 0.3, 0.2, 0.3, 0.08);
            level.sendParticles(ParticleTypes.SPLASH, x, mob.getY() + 0.3, z, 4, 0.2, 0.1, 0.2, 0.05);
            level.sendParticles(spell(), x, y + 0.3, z, 6, 0.4, 0.3, 0.4, 0.02);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.5, z, 8, 0.5, 0.4, 0.5, 0.0);
            if (severeSeverity >= 2) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.3, 0.2, 0.3, 0.02);
                level.sendParticles(ParticleTypes.SNEEZE, x, y + 0.4, z, 8, 0.5, 0.3, 0.5, 0.12);
            }
        } else if (activeSeverity > 0 && gameTime % ACTIVE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 2, 0.2, 0.15, 0.2, 0.05);
            level.sendParticles(spell(), x, y + 0.2, z, 3, 0.3, 0.2, 0.3, 0.01);
        }
    }

    public static void tickPlayer(LivingEntity player, ServerLevel level,
                                   int activeSeverity, int severeSeverity,
                                   long gameTime) {
        if (activeSeverity == 0 && severeSeverity == 0) return;
        double x = player.getX(), y = player.getEyeY(), z = player.getZ();

        if (severeSeverity > 0 && gameTime % SEVERE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 4, 0.25, 0.15, 0.25, 0.07);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.3, z, 6, 0.4, 0.3, 0.4, 0.0);
            level.sendParticles(spell(), x, y + 0.1, z, 5, 0.3, 0.2, 0.3, 0.02);
            if (severeSeverity >= 2) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y - 0.5, z, 2, 0.2, 0.1, 0.2, 0.01);
            }
        } else if (activeSeverity > 0 && gameTime % ACTIVE_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 1, 0.15, 0.1, 0.15, 0.04);
            level.sendParticles(spell(), x, y + 0.1, z, 2, 0.2, 0.15, 0.2, 0.01);
        }
    }

    public static void sneezeTransmission(LivingEntity mob, ServerLevel level) {
        double x = mob.getX(), y = mob.getEyeY(), z = mob.getZ();
        level.sendParticles(ParticleTypes.SNEEZE, x, y, z, 6, 0.4, 0.2, 0.4, 0.1);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.2, z, 10, 0.6, 0.4, 0.6, 0.0);
    }
}
