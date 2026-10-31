package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.combat.CrystalAttacks;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** A warned, temporary spell hazard. It never replaces terrain or damages the faction. */
public final class CrystalSpike extends Entity {
    private static final EntityDataAccessor<Integer> WARMUP=SynchedEntityData.defineId(CrystalSpike.class,EntityDataSerializers.INT);
    private UUID caster,cast;
    private float damage=7;
    private int slow=0;
    private boolean restored;
    public CrystalSpike(EntityType<? extends CrystalSpike> type,Level level){super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(){entityData.define(WARMUP,16);}
    public void configure(Mob owner,UUID castId,int warmup,float power,int slowing){caster=owner.getUUID();cast=castId;entityData.set(WARMUP,Math.max(6,Math.min(50,warmup)));damage=power;slow=slowing;}
    public int warmup(){return entityData.get(WARMUP);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel level))return;
        if(restored||caster==null||cast==null||!CrystalAttacks.active(level,cast)){discard();return;}
        Entity source=level.getEntity(caster);
        if(!(source instanceof Mob owner)||!owner.isAlive()||!CrystalAttacks.validCaster(owner,cast)){discard();return;}
        if(tickCount==warmup()){FactionEffects.shatter(level,position(),6);level.playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,net.minecraft.sounds.SoundSource.HOSTILE,.7F,.8F);}
        if(tickCount==warmup()+3){
            for(LivingEntity victim:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.25,.25,.25),e->FactionAI.enemy(owner,e))){
                if(!CrystalAttacks.claimHit(level,cast,victim))continue;
                if(victim.hurt(level.damageSources().indirectMagic(this,owner),damage)){
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0,.23,0));
                    if(slow>0)victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,60,slow-1));
                }
            }
        }
        if(tickCount>=warmup()+20)discard();
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){restored=true;}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putBoolean("TransientSpell",true);}
}
