package dev.hui.enderinfection.combat;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class CrystalAttacks {
    private CrystalAttacks(){}
    private record Cast(UUID owner,int sequence,long until,Set<UUID> hits){}
    private static final Map<ServerLevel,Map<UUID,Cast>> CASTS=new HashMap<>();
    public static boolean active(ServerLevel level,UUID id){var map=CASTS.get(level);return map!=null&&map.containsKey(id)&&map.get(id).until()>level.getGameTime();}
    public static boolean validCaster(Mob owner,UUID id){
        if(!(owner.level() instanceof ServerLevel level))return false;var map=CASTS.get(level);Cast cast=map==null?null:map.get(id);if(cast==null)return false;
        return owner.getUUID().equals(cast.owner())&&owner.getPersistentData().getInt("CrystalCastSequence")==cast.sequence();
    }
    public static boolean claimHit(ServerLevel level,UUID id,LivingEntity target){var map=CASTS.get(level);Cast cast=map==null?null:map.get(id);return cast!=null&&cast.hits().add(target.getUUID());}
    public static int cast(Mob owner,InfectedKind kind,LivingEntity target,int warmup){
        if(!(owner.level() instanceof ServerLevel level)||target==null||!owner.hasLineOfSight(target)||kind.early()&&!(owner instanceof MutantZombie)||kind==InfectedKind.CORE||kind==InfectedKind.SIEGE_BEAST)return 0;
        var map=CASTS.computeIfAbsent(level,key->new HashMap<>());map.values().removeIf(c->c.until()<=level.getGameTime());if(map.size()>=128)return 0;
        int sequence=owner.getPersistentData().getInt("CrystalCastSequence")+1;owner.getPersistentData().putInt("CrystalCastSequence",sequence);
        UUID id=UUID.randomUUID();map.put(id,new Cast(owner.getUUID(),sequence,level.getGameTime()+80,new HashSet<>()));
        Vec3 front=target.position().subtract(owner.position()).multiply(1,0,1).normalize();if(front.lengthSqr()<.01)front=new Vec3(0,0,1);
        Vec3 center=target.position();int spawned=0;
        switch(kind){
            case COW->{for(int i=0;i<6;i++)spawned+=spike(level,owner,id,owner.position().add(front.scale(2+i*1.6)),warmup+i*2,9,0);}
            case PIG->{for(int side=-1;side<=1;side++)for(int i=0;i<2;i++)spawned+=spike(level,owner,id,owner.position().add(front.scale(2.2+i*1.7)).add(-front.z*side*1.3,0,front.x*side*1.3),warmup+i*2,7,0);}
            case SHEEP->{center=owner.position();for(int i=0;i<8;i++){double angle=i*Math.PI/4;spawned+=spike(level,owner,id,center.add(Math.cos(angle)*3.5,0,Math.sin(angle)*3.5),warmup,6,2);}}
            case SPIDER->{for(int i=0;i<5;i++){double angle=i*Math.PI/2;spawned+=spike(level,owner,id,i==4?center:center.add(Math.cos(angle)*1.3,0,Math.sin(angle)*1.3),warmup,6,3);}}
            case ENDERMAN->{for(int i=-2;i<=2;i++)spawned+=spike(level,owner,id,center.add(front.scale(i*1.25)),warmup,10,1);for(int i:new int[]{-1,1})spawned+=spike(level,owner,id,center.add(-front.z*i*1.4,0,front.x*i*1.4),warmup,10,1);}
            case SKELETON->{spawned+=spike(level,owner,id,center,warmup,8,1);for(int i=0;i<6;i++){double angle=i*Math.PI/3;spawned+=spike(level,owner,id,center.add(Math.cos(angle)*1.7,0,Math.sin(angle)*1.7),warmup,8,1);}}
            default->{for(int i=0;i<5;i++)spawned+=spike(level,owner,id,owner.position().add(front.scale(1.8+i*1.25)),warmup+i,8,1);}
        }
        if(spawned>0){FactionEffects.rune(level,center,kind==InfectedKind.SHEEP?4:2.4);FactionEffects.gather(level,owner.getEyePosition(),5);}else map.remove(id);
        return spawned;
    }
    private static int spike(ServerLevel level,Mob owner,UUID cast,Vec3 desired,int warmup,float damage,int slow){
        BlockPos column=BlockPos.containing(desired);if(!level.hasChunkAt(column))return 0;
        if(level.getEntitiesOfClass(CrystalSpike.class,owner.getBoundingBox().inflate(24)).size()>=96)return 0;
        for(int y=2;y>=-4;y--){
            BlockPos ground=column.offset(0,y-1,0);var state=level.getBlockState(ground);
            if(state.hasBlockEntity()||!(state.isFaceSturdy(level,ground,Direction.UP)||state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock||dev.hui.enderinfection.block.ErodedGroundBlock.farmland(state))||!level.getFluidState(ground.above()).isEmpty())continue;
            Vec3 at=new Vec3(desired.x,ground.getY()+state.getCollisionShape(level,ground).max(Direction.Axis.Y),desired.z);
            if(level.clip(new ClipContext(owner.getEyePosition(),at.add(0,.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS)continue;
            CrystalSpike spike=EnderInfection.CRYSTAL_SPIKE.get().create(level);if(spike==null)return 0;spike.setPos(at);spike.configure(owner,cast,warmup,damage,slow);
            if(level.getBlockCollisions(spike,spike.getBoundingBox()).iterator().hasNext())continue;
            if(level.addFreshEntity(spike)){FactionEffects.rune(level,at,.8);return 1;}
        }
        return 0;
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level&&level.getGameTime()%20==0){var map=CASTS.get(level);if(map!=null){map.values().removeIf(c->c.until()<=level.getGameTime());if(map.isEmpty())CASTS.remove(level);}}}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)CASTS.remove(level);}
}
