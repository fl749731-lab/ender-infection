package dev.hui.enderinfection.design;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class DesignArmorEffects {
    private static boolean full(Player p,String set){int n=0;for(var s:p.getArmorSlots()){if(!(s.getItem() instanceof DesignArmor a)||!a.set.equals(set))return false;n++;}return n==4;}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&!e.player.level().isClientSide&&e.player.tickCount%20==0&&full(e.player,"crystal_carapace"))e.player.removeEffect(InfectedContent.ENDER_EROSION.get());}
    @SubscribeEvent public static void hurt(LivingHurtEvent e){
        if(!(e.getEntity() instanceof Player p)||p.level().isClientSide||!full(p,"brood_heavy")||p.getHealth()-e.getAmount()>p.getMaxHealth()*.35F)return;
        long now=p.level().getGameTime();if(now<p.getPersistentData().getLong("CarapaceReady"))return;
        p.getPersistentData().putLong("CarapaceReady",now+400);p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,120,1));e.setAmount(e.getAmount()*.65F);
    }
}
