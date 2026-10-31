package dev.hui.enderinfection.item;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionLoot {
    private FactionLoot(){}
    @SubscribeEvent public static void drops(LivingDropsEvent event){
        var victim=event.getEntity();if(victim.level().isClientSide||!victim.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)||!FactionAI.infected(victim))return;
        boolean core=victim instanceof InfectedMob unit&&unit.kind==InfectedKind.CORE;
        boolean boss=victim instanceof InfectedMob unit&&unit.kind==InfectedKind.SIEGE_BEAST;
        boolean early=victim instanceof InfectedMob unit&&unit.kind.early();
        var random=victim.getRandom();int loot=Math.min(3,Math.max(0,event.getLootingLevel()));
        add(event,new ItemStack(InfectionItems.TISSUE.get(),core?4+((InfectedMob)victim).motherStage()*2:boss?8+random.nextInt(5):early?1+random.nextInt(2):2+random.nextInt(3)));
        if(core||boss||!early||random.nextInt(4)==0)add(event,new ItemStack(InfectionItems.SHARD.get(),boss?6+random.nextInt(5):core?2*((InfectedMob)victim).motherStage():1+random.nextInt(2+loot)));
        if(core||boss)add(event,new ItemStack(InfectionItems.HEART.get(),core&&((InfectedMob)victim).motherStage()>=4?2:1));
    }
    private static void add(LivingDropsEvent event,ItemStack stack){
        var victim=event.getEntity();event.getDrops().add(new ItemEntity(victim.level(),victim.getX(),victim.getY()+.5,victim.getZ(),stack));
    }
}
