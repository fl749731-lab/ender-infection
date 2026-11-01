package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, decaying memory of observed in-game combat. No inventory inspection or unseen-player tracking. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerTactics {
    private PlayerTactics(){}
    public enum Observation { MELEE,RANGED,HIGH_GROUND,RAID,RETREAT }
    public enum Tactic {
        BALANCED("均衡"),ANTI_RANGED("侧翼追击远程"),ANTI_MELEE("重装与远程交叉支援"),ANTI_TOWER("对高处远程压制"),DEFEND_CORE("保卫母体与节点"),PURSUIT("快速追击");
        public final String label;Tactic(String label){this.label=label;}
    }
    private static final class Profile {
        double melee,ranged,tower,raids,retreat;long updated,lastSeen,lastMovement;
        boolean decay(long now){if(now-updated<200)return false;double factor=Math.pow(.5,(now-updated)/2400.0);melee*=factor;ranged*=factor;tower*=factor;raids*=factor;retreat*=factor;updated=now;return true;}
        Tactic tactic(){
            if(raids>=3&&raids>=Math.max(melee,ranged)*.6)return Tactic.DEFEND_CORE;
            if(tower>=3)return Tactic.ANTI_TOWER;
            if(ranged>=3&&ranged>melee*1.4)return Tactic.ANTI_RANGED;
            if(melee>=3&&melee>ranged*1.4)return Tactic.ANTI_MELEE;
            if(retreat>=3)return Tactic.PURSUIT;
            return Tactic.BALANCED;
        }
    }
    public static final class Data extends SavedData {
        private final Map<UUID,Profile> profiles=new HashMap<>();
        public static Data load(CompoundTag tag){
            Data data=new Data();ListTag list=tag.getList("Players",10);
            for(int i=0;i<Math.min(128,list.size());i++){
                CompoundTag t=list.getCompound(i);if(!t.hasUUID("Id"))continue;Profile p=new Profile();p.melee=t.getDouble("Melee");p.ranged=t.getDouble("Ranged");p.tower=t.getDouble("Tower");p.raids=t.getDouble("Raids");p.retreat=t.getDouble("Retreat");p.updated=t.getLong("Updated");p.lastSeen=t.getLong("LastSeen");p.lastMovement=t.getLong("LastMovement");data.profiles.put(t.getUUID("Id"),p);
            }
            return data;
        }
        public void observe(UUID id,Observation action,long now){
            if(!profiles.containsKey(id)&&profiles.size()>=128)profiles.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().lastSeen)).ifPresent(e->profiles.remove(e.getKey()));
            Profile p=profiles.computeIfAbsent(id,key->{Profile value=new Profile();value.updated=now;return value;});p.decay(now);p.lastSeen=now;
            switch(action){case MELEE->p.melee=Math.min(40,p.melee+1);case RANGED->p.ranged=Math.min(40,p.ranged+1);case HIGH_GROUND->p.tower=Math.min(40,p.tower+1);case RAID->p.raids=Math.min(40,p.raids+1);case RETREAT->p.retreat=Math.min(40,p.retreat+1);}
            setDirty();
        }
        public Tactic tactic(UUID id,long now){Profile p=profiles.get(id);if(p==null)return Tactic.BALANCED;if(p.decay(now))setDirty();return p.tactic();}
        public void observeRetreat(Player player,InfectedMob mother,long now){
            Profile p=profiles.get(player.getUUID());if(p==null||now-p.lastMovement<100)return;p.lastMovement=now;
            var away=player.position().subtract(mother.position()).multiply(1,0,1).normalize();
            if(player.distanceToSqr(mother)>64&&player.getDeltaMovement().dot(away)>.08)observe(player.getUUID(),Observation.RETREAT,now);
        }
        @Override public CompoundTag save(CompoundTag tag){
            ListTag list=new ListTag();profiles.forEach((id,p)->{CompoundTag t=new CompoundTag();t.putUUID("Id",id);t.putDouble("Melee",p.melee);t.putDouble("Ranged",p.ranged);t.putDouble("Tower",p.tower);t.putDouble("Raids",p.raids);t.putDouble("Retreat",p.retreat);t.putLong("Updated",p.updated);t.putLong("LastSeen",p.lastSeen);t.putLong("LastMovement",p.lastMovement);list.add(t);});tag.put("Players",list);return tag;
        }
    }
    public static Data get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(Data::load,Data::new,"ender_infection_player_tactics");}
    @SubscribeEvent public static void hurt(LivingHurtEvent event){
        if(event.getAmount()<=0||!FactionAI.infected(event.getEntity())||!(event.getEntity().level() instanceof ServerLevel level)||!(event.getSource().getEntity() instanceof Player player)||player.isSpectator())return;
        boolean witnessed=false;
        for(InfectedMob core:level.getEntitiesOfClass(InfectedMob.class,event.getEntity().getBoundingBox().inflate(32,12,32),e->e.kind==InfectedKind.CORE&&e.isAlive())){
            if(core.hasLineOfSight(player)){witnessed=true;core.motherBrain().rememberPlayer(player.getUUID(),level.getGameTime());if(FactionAI.enemy(core,player))core.setTarget(player);}
        }
        if(!witnessed)return;
        Data data=get(level);long now=level.getGameTime();data.observe(player.getUUID(),event.getSource().getDirectEntity() instanceof Projectile?Observation.RANGED:Observation.MELEE,now);
        if(player.getY()-event.getEntity().getY()>3)data.observe(player.getUUID(),Observation.HIGH_GROUND,now);
        if(event.getEntity() instanceof InfectedMob core&&core.kind==InfectedKind.CORE)data.observe(player.getUUID(),Observation.RAID,now);
    }
    @SubscribeEvent public static void nodeBroken(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!event.getState().is(InfectedContent.NEST.get()))return;
        Player player=event.getPlayer();InfectedMob owner=null;
        if(level.getBlockEntity(event.getPos()) instanceof dev.hui.enderinfection.block.ExpansionNodeEntity node&&node.ownerId()!=null&&level.getEntity(node.ownerId()) instanceof InfectedMob core&&core.isAlive())owner=core;
        if(owner==null)owner=level.getEntitiesOfClass(InfectedMob.class,new net.minecraft.world.phys.AABB(event.getPos()).inflate(48,12,48),e->e.kind==InfectedKind.CORE&&e.isAlive()).stream().min(Comparator.comparingDouble(e->e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(event.getPos())))).orElse(null);
        if(owner==null||!FactionAI.enemy(owner,player))return;
        if(owner.hasLineOfSight(player)){get(level).observe(player.getUUID(),Observation.RAID,level.getGameTime());owner.motherBrain().rememberPlayer(player.getUUID(),level.getGameTime());owner.setTarget(player);}
        FactionAI.defendColony(owner,player,event.getPos());
    }
    @SubscribeEvent public static void command(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("enderstrategy").requires(s->s.hasPermission(2)).executes(context->{
            var player=context.getSource().getPlayerOrException();var level=player.serverLevel();
            context.getSource().sendSuccess(()->Component.literal("阵营对你的近期战术："+get(level).tactic(player.getUUID(),level.getGameTime()).label),false);
            level.getEntitiesOfClass(InfectedMob.class,player.getBoundingBox().inflate(64),e->e.kind==InfectedKind.CORE&&e.isAlive()).stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(core->context.getSource().sendSuccess(()->Component.literal("最近母体：阶段 "+core.motherStage()+"，生物质 "+core.colonyBiomass()+"，状态 "+core.motherState().label),false));
            level.getEntitiesOfClass(InfectedMob.class,player.getBoundingBox().inflate(64),e->e.kind==InfectedKind.CORE&&e.isAlive()).stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(core->context.getSource().sendSuccess(()->Component.literal("母体教导：攻击性 "+core.motherBrain().learning().aggression()+"，围攻上限 "+core.motherBrain().learning().packLimit()+"，狩猎嗅觉半径 "+Math.round(FactionAI.huntScent(core))+" 格"),false));
            level.getEntitiesOfClass(InfectedMob.class,player.getBoundingBox().inflate(64),e->e.kind==InfectedKind.CORE&&e.isAlive()).stream().min(Comparator.comparingDouble(player::distanceToSqr)).ifPresent(core->context.getSource().sendSuccess(()->Component.literal("经济策略：倾向 "+core.motherBrain().policy().arm().name()+"，已观察 "+core.motherBrain().policy().windows()+" 个窗口，孵化/扩张收益 "+String.format("%.1f",core.motherBrain().policy().hatchValue())+" / "+String.format("%.1f",core.motherBrain().policy().expandValue())+"，节点效率 "+String.format("%.2f",core.nodeEfficiency())+" 格/生物质"),false));
            int shown=0;
            for(BlockPos p:BlockPos.betweenClosed(player.blockPosition().offset(-24,-8,-24),player.blockPosition().offset(24,8,24))){
                if(shown>=4)break;
                if(!(level.getBlockEntity(p) instanceof dev.hui.enderinfection.block.ExpansionNodeEntity node))continue;
                shown++;
                final BlockPos at=p.immutable();
                context.getSource().sendSuccess(()->Component.literal("节点 "+at.toShortString()+"：半径 "+node.radius()+"，活跃 "+node.activeTicks()+" tick，改格 "+node.purchasedChanges()+"，下沉 "+node.deepenedBlocks()+"，成熟 "+node.maturedBlocks()+"，单位 "+node.soilBorn()),false);
            }
            level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,player.getBoundingBox().inflate(48),e->FactionAI.infected(e)).stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(8).forEach(unit->context.getSource().sendSuccess(()->Component.literal(unit.getName().getString()+"："+unit.getPersistentData().getString("ColonyTask")+" / "+unit.getPersistentData().getString("TaskReason")),false));
            return 1;
        }));
    }
}
