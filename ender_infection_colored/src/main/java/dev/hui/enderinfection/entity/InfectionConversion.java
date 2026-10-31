package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Confirm death on a later level tick before replacing a host; never convert players or our faction. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class InfectionConversion {
    private record Pending(Mob victim,Mob killer,long due) {}
    private static final Map<ServerLevel,List<Pending>> PENDING=new HashMap<>();
    private InfectionConversion() {}
    public static InfectedKind replacementFor(LivingEntity host){
        if(FactionAI.infected(host))return null;
        EntityType<?> type=host.getType();
        if(type==EntityType.ZOMBIE)return InfectedKind.EARLY_ZOMBIE;
        if(type==EntityType.SKELETON)return InfectedKind.EARLY_SKELETON;
        if(type==EntityType.COW)return InfectedKind.EARLY_COW;
        if(type==EntityType.PIG)return InfectedKind.EARLY_PIG;
        if(type==EntityType.SHEEP)return InfectedKind.EARLY_SHEEP;
        if(type==EntityType.SPIDER)return InfectedKind.EARLY_SPIDER;
        if(type==EntityType.ENDERMAN)return InfectedKind.EARLY_ENDERMAN;
        return null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void killed(LivingDeathEvent event){
        if(!(event.getEntity() instanceof Mob victim)||!(victim.level() instanceof ServerLevel level))return;
        if(!(event.getSource().getEntity() instanceof Mob killer)||!FactionAI.infected(killer)||replacementFor(victim)==null)return;
        List<Pending> deaths=PENDING.computeIfAbsent(level,key->new ArrayList<>());
        if(deaths.stream().noneMatch(p->p.victim()==victim))deaths.add(new Pending(victim,killer,level.getGameTime()+12));
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        List<Pending> deaths=PENDING.get(level);if(deaths==null)return;
        var iterator=deaths.iterator();
        while(iterator.hasNext()){
            Pending death=iterator.next();if(level.getGameTime()<death.due())continue;iterator.remove();
            Mob victim=death.victim();if(victim.isRemoved()||!victim.isDeadOrDying())continue;
            InfectedKind kind=replacementFor(victim);if(kind==null)continue;
            InfectedMob next=InfectedContent.TYPES.get(kind).get().create(level);if(next==null)continue;
            next.moveTo(victim.getX(),victim.getY(),victim.getZ(),victim.getYRot(),0);
            if(level.getBlockCollisions(next,next.getBoundingBox()).iterator().hasNext())continue;
            if(victim.hasCustomName()){next.setCustomName(victim.getCustomName());next.setCustomNameVisible(victim.isCustomNameVisible());}
            if(victim.isPersistenceRequired())next.setPersistenceRequired();
            InfectedMob mother=FactionAI.home(death.killer());if(mother!=null)next.getPersistentData().putUUID("ColonyMother",mother.getUUID());next.getPersistentData().putString("ColonyDuty","hunter");
            LivingEntity enemy=death.killer().getTarget();if(enemy!=null&&FactionAI.enemy(next,enemy))next.setTarget(enemy);
            next.getPersistentData().putString("InfectedHost",net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
            if(level.addFreshEntity(next))victim.discard();
        }
        if(deaths.isEmpty())PENDING.remove(level);
    }
    @SubscribeEvent public static void unloaded(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)PENDING.remove(level);}
}
