package dev.hui.enderinfection.design;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Animated appearance of the existing staff; DesignWeapon still owns the ability. */
public final class ResonanceStaff extends DesignWeapon implements GeoItem {
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("animation.resonance_staff.idle");
    private static final RawAnimation CAST=RawAnimation.begin().thenPlay("animation.resonance_staff.cast");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    public ResonanceStaff(){super("resonance_staff");GeoItem.registerSyncedAnimatable(this);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var result=super.use(level,player,hand);
        if(level instanceof ServerLevel server&&result.getResult().consumesAction()&&!result.getObject().isEmpty())
            triggerAnim(player,GeoItem.getOrAssignId(result.getObject(),server),"staff","cast");
        return result;
    }
    // Durability and the first synced animation ID must not lower the item during its cast.
    @Override public boolean shouldCauseReequipAnimation(ItemStack before,ItemStack after,boolean slotChanged){
        return slotChanged||before.getItem()!=after.getItem()||
            (GeoItem.getId(before)>=0&&GeoItem.getId(after)>=0&&GeoItem.getId(before)!=GeoItem.getId(after));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"staff",0,state->state.setAndContinue(IDLE)).triggerableAnim("cast",CAST));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer){
        dev.hui.enderinfection.client.ResonanceStaffRenderer.extensions(consumer);
    }
}
