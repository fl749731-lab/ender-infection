package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.MutantZombie;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class MutantZombieModel extends GeoModel<MutantZombie> {
    @Override public ResourceLocation getModelResource(MutantZombie entity) {
        return new ResourceLocation(EnderInfection.MOD_ID, "geo/ender_mutant_zombie.geo.json");
    }
    @Override public ResourceLocation getTextureResource(MutantZombie entity) {
        return new ResourceLocation(EnderInfection.MOD_ID, "textures/entity/ender_mutant_zombie.png");
    }
    @Override public ResourceLocation getAnimationResource(MutantZombie entity) {
        return new ResourceLocation(EnderInfection.MOD_ID, "animations/ender_mutant_zombie.animation.json");
    }
}
