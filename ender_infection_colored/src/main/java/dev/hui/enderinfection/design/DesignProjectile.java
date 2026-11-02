package dev.hui.enderinfection.design;

import dev.hui.enderinfection.entity.FactionAI;
import dev.hui.enderinfection.item.InfectionItems;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.effect.*;

public final class DesignProjectile extends ThrowableItemProjectile {
    private float damage=7;private boolean poison;
    public DesignProjectile(EntityType<? extends DesignProjectile> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected Item getDefaultItem(){return InfectionItems.SHARD.get();}
    public static void fire(ServerLevel level,LivingEntity owner,Vec3 aim,float damage,boolean poison){
        DesignProjectile p=DesignContent.PROJECTILE.get().create(level);if(p==null)return;p.setOwner(owner);p.setPos(owner.getX(),owner.getEyeY()-.15,owner.getZ());p.damage=damage;p.poison=poison;
        Vec3 d=aim.subtract(p.position()).normalize();p.shoot(d.x,d.y,d.z,1.25F,1);level.addFreshEntity(p);FactionEffects.rune(level,owner.position(),.8);
    }
    @Override protected boolean canHitEntity(Entity e){return super.canHitEntity(e)&&e!=getOwner()&&!(getOwner() instanceof Mob m&&e instanceof LivingEntity l&&!FactionAI.enemy(m,l))&&!(getOwner() instanceof Player p&&e instanceof Player q&&!p.canHarmPlayer(q));}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level() instanceof ServerLevel level&&hit.getEntity() instanceof LivingEntity victim){
            if(victim.hurt(damageSources().indirectMagic(this,getOwner()),damage)){victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));if(poison)victim.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));}
            FactionEffects.shatter(level,position(),10);discard();
        }
    }
    @Override protected void onHitBlock(BlockHitResult hit){super.onHitBlock(hit);if(level() instanceof ServerLevel level){FactionEffects.shatter(level,position(),8);discard();}}
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>80)discard();}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putFloat("Power",damage);tag.putBoolean("Poison",poison);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);damage=Math.min(40,Math.max(0,tag.getFloat("Power")));poison=tag.getBoolean("Poison");}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
