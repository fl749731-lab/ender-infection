package dev.hui.enderinfection.item;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class InfectionItems {
    private InfectionItems(){}
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,EnderInfection.MOD_ID);
    public static final RegistryObject<Item> TISSUE=ITEMS.register("infected_tissue",()->new Item(new Item.Properties()){
        @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
            if(!(target instanceof InfectedMob core)||core.kind!=InfectedKind.CORE||core.colonyBiomass()>=10000)return InteractionResult.PASS;
            if(target.level() instanceof ServerLevel level){core.receiveColonyBiomass(4);if(!player.getAbilities().instabuild)stack.shrink(1);FactionEffects.harvest(level,player.position(),core.position().add(0,1,0));}
            return InteractionResult.sidedSuccess(target.level().isClientSide);
        }
    });
    public static final RegistryObject<Item> SHARD=ITEMS.register("resonant_shard",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> HEART=ITEMS.register("mother_heart",()->new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> DUST=ITEMS.register("suppressing_dust",()->new Item(new Item.Properties()){
        @Override public InteractionResult useOn(UseOnContext context){
            if(!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ExpansionNodeEntity node))return InteractionResult.PASS;
            Player player=context.getPlayer();if(player!=null&&player.getCooldowns().isOnCooldown(this))return InteractionResult.FAIL;
            if(context.getLevel() instanceof ServerLevel level){
                node.inhibit(level,300);FactionEffects.suppress(level,node.effectPosition());
                if(player==null||!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
                if(player!=null)player.getCooldowns().addCooldown(this,40);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
    });
    public static final RegistryObject<Item> BLADE=ITEMS.register("resonance_blade",()->new SwordItem(Tiers.IRON,4,-2.6F,new Item.Properties().durability(420).rarity(Rarity.UNCOMMON)){
        @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
            if(target.level() instanceof ServerLevel level){target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0));FactionEffects.shatter(level,target.position().add(0,.8,0),8);}
            return super.hurtEnemy(stack,target,attacker);
        }
    });
    public static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(InfectionItems::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey()==CreativeModeTabs.INGREDIENTS){event.accept(TISSUE);event.accept(SHARD);event.accept(HEART);event.accept(DUST);}
        if(event.getTabKey()==CreativeModeTabs.COMBAT)event.accept(BLADE);
    }
}
