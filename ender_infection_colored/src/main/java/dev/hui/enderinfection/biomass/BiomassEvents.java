package dev.hui.enderinfection.biomass;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.particle.FactionEffects;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.FactionAI;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.MutantZombie;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BiomassEvents {
    private BiomassEvents(){}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void onDeath(LivingDeathEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        var killer=event.getSource().getEntity();
        if(!(killer instanceof InfectedMob)&&!(killer instanceof MutantZombie))return;
        if(!(event.getEntity() instanceof Mob)||FactionAI.infected(event.getEntity()))return;
        int value=Math.max(5,Math.min(25,Math.round(event.getEntity().getMaxHealth()*.5F)));
        if(killer instanceof InfectedMob infected)infected.feedBiomass(value);
        InfectedMob owner=FactionAI.home((Mob)killer);
        if(owner!=null)owner.receiveColonyBiomass(value);
        else if(killer.getPersistentData().hasUUID("ColonyMother"))killer.getPersistentData().putInt("PendingColonyBiomass",Math.min(1000,killer.getPersistentData().getInt("PendingColonyBiomass")+value));
        else level.getEntitiesOfClass(InfectedMob.class,event.getEntity().getBoundingBox().inflate(32,12,32),core->core.kind==InfectedKind.CORE&&core.isAlive()).stream().min(java.util.Comparator.comparingDouble(core->core.distanceToSqr(event.getEntity()))).ifPresentOrElse(core->core.receiveColonyBiomass(value),()->BiomassData.get(level).add(value));
        FactionEffects.harvest(level,event.getEntity().position().add(0,.7,0),killer.position().add(0,1,0));
    }
    private static InfectedMob nearest(net.minecraft.commands.CommandSourceStack source){
        return source.getLevel().getEntitiesOfClass(InfectedMob.class,new net.minecraft.world.phys.AABB(net.minecraft.core.BlockPos.containing(source.getPosition())).inflate(32,12,32),core->core.kind==InfectedKind.CORE&&core.isAlive()).stream().min(java.util.Comparator.comparingDouble(core->core.distanceToSqr(source.getPosition()))).orElse(null);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("enderbiomass").requires(source->source.hasPermission(2))
            .executes(context->{
                var source=context.getSource();int reserve=BiomassData.get(source.getLevel()).amount();var core=nearest(source);
                source.sendSuccess(()->Component.literal("附近母体储备："+(core==null?"无母体":core.colonyBiomass()+"；已加载领土："+core.territoryColumns()+"格；上轮土地收益："+core.lastTerritoryIncome()+"/10秒；土地养分："+core.territoryFertility()+"%")+"；未分配/旧版储备："+reserve),false);return core==null?reserve:core.colonyBiomass();
            }).then(Commands.literal("deposit").then(Commands.argument("amount",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10000)).executes(context->{
                var source=context.getSource();var core=nearest(source);int amount=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"amount");var reserve=BiomassData.get(source.getLevel());
                if(core==null||core.colonyBiomass()+amount>10000||!reserve.spend(amount)){source.sendFailure(Component.literal("附近没有母体、储备不足或超过母体容量。"));return 0;}
                core.receiveColonyBiomass(amount);source.sendSuccess(()->Component.literal("已向最近母体转入 "+amount+" 生物质。"),false);return amount;
            }))));
    }
}
