package dev.hui.enderinfection.client;

import dev.hui.enderinfection.entity.MutantZombie;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MutantZombieRenderer extends GeoEntityRenderer<MutantZombie> {
    public MutantZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new MutantZombieModel());
        this.shadowRadius = 0.75F;
    }
}
