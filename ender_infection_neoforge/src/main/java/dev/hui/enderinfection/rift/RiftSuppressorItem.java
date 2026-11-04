package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.List;
import javax.annotation.Nullable;

public final class RiftSuppressorItem extends Item {
    public static final int DURATION=200,COOLDOWN=240;
    public RiftSuppressorItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide)return InteractionResultHolder.success(player.getItemInHand(hand));
        return activate((ServerLevel)level,player,hand,player.position().add(0,1,0))?InteractionResultHolder.consume(player.getItemInHand(hand)):InteractionResultHolder.fail(player.getItemInHand(hand));
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(!context.getLevel().getBlockState(context.getClickedPos()).is(RiftContent.ANCHOR.get()))return super.useOn(context);
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!context.getLevel().mayInteract(player,context.getClickedPos()))return InteractionResult.FAIL;
        return activate((ServerLevel)context.getLevel(),player,context.getHand(),context.getClickLocation().add(context.getClickedFace().getStepX()*.05,context.getClickedFace().getStepY()*.05,context.getClickedFace().getStepZ()*.05))?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    private boolean activate(ServerLevel level,Player player,InteractionHand hand,Vec3 center){
        if(player.isSpectator()||player.getCooldowns().isOnCooldown(this))return false;
        int count=pulse(level,center,player);if(count==0){player.displayClientMessage(Component.translatable("message.ender_infection.no_rift_target"),true);return false;}
        var stack=player.getItemInHand(hand);stack.hurtAndBreak(1,player,net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));player.getCooldowns().addCooldown(this,COOLDOWN);
        level.playSound(null,player.blockPosition(),SoundEvents.BEACON_DEACTIVATE,SoundSource.PLAYERS,1,.8F);
        level.sendParticles(ParticleTypes.END_ROD,center.x,center.y,center.z,45,2,.6,2,.03);
        player.displayClientMessage(Component.translatable("message.ender_infection.rift_suppressed",count),true);return true;
    }
    public static int pulse(ServerLevel level,Vec3 center,Player player){
        int count=0;
        for(var anchor:RiftAnchorEntity.nearby(level,net.minecraft.core.BlockPos.containing(center),6)){
            if(!level.mayInteract(player,anchor.getBlockPos()))continue;
            var end=Vec3.atCenterOf(anchor.getBlockPos());var hit=level.clip(new ClipContext(center,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(anchor.getBlockPos())){anchor.suppress(DURATION);count++;}
        }
        for(var mob:level.getEntitiesOfClass(InfectedMob.class,new AABB(center,center).inflate(6),m->RiftCombat.unit(m)&&m.isAlive()&&m.position().distanceToSqr(center)<=36)){
            if(level.clip(new ClipContext(center,mob.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)continue;
            RiftCombat.suppress(mob,DURATION);count++;
        }
        return count;
    }
    @Override public void appendHoverText(ItemStack s,net.minecraft.world.item.Item.TooltipContext l,List<Component> lines,TooltipFlag f){lines.add(Component.translatable("tooltip.ender_infection.phase_suppressor"));}
}
