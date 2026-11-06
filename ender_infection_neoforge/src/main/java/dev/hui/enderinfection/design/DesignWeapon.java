package dev.hui.enderinfection.design;

import dev.hui.enderinfection.item.InfectionItems;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.Level;

/** Server-authoritative abilities: ammunition, charge, cooldown and durability are consumed once. */
public class DesignWeapon extends SwordItem {
    public final String key;
    public DesignWeapon(String key){super(Tiers.DIAMOND,new Item.Properties().durability(650).rarity(Rarity.RARE).attributes(SwordItem.createAttributes(Tiers.DIAMOND,key.equals("earthsplitter_hammer")?7:key.equals("prism_spear")?4:1,key.equals("earthsplitter_hammer")?-3.3F:-2.6F)));this.key=key;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity user){return key.equals("crystal_spike_crossbow")?72000:0;}
    @Override public UseAnim getUseAnimation(ItemStack stack){return key.equals("crystal_spike_crossbow")?UseAnim.CROSSBOW:UseAnim.NONE;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        if(key.equals("crystal_spike_crossbow")){if(!player.getAbilities().instabuild&&!player.getInventory().contains(new ItemStack(InfectionItems.SHARD.get())))return InteractionResultHolder.fail(stack);player.startUsingItem(hand);return InteractionResultHolder.consume(stack);}
        if(level instanceof ServerLevel server){
            if(key.equals("resonance_staff")){
                DesignProjectile.fire(server,player,player.getEyePosition().add(player.getLookAngle().scale(24)),10,false);FactionEffects.rune(server,player.position(),1.8);
            }else{
                double reach=key.equals("prism_spear")?5:4;Vec3 look=player.getLookAngle();
                for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(reach),e->e!=player&&e.isAlive()&&!(e instanceof Player q&&!player.canHarmPlayer(q)))){
                    Vec3 d=target.getBoundingBox().getCenter().subtract(player.getEyePosition());
                    if(d.length()>reach||!player.hasLineOfSight(target)||key.equals("prism_spear")&&d.normalize().dot(look)<.88)continue;
                    if(target.hurt(player.damageSources().playerAttack(player),key.equals("prism_spear")?9:12)){target.knockback(.8,-d.x,-d.z);FactionEffects.shatter(server,target.position(),10);}
                }
                FactionEffects.rune(server,player.position(),key.equals("prism_spear")?1:4);FactionEffects.shatter(server,player.position().add(look.scale(2)),24);
            }
            player.getCooldowns().addCooldown(this,key.equals("earthsplitter_hammer")?80:key.equals("prism_spear")?35:25);stack.hurtAndBreak(2,player,net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity user,int remaining){
        if(!(level instanceof ServerLevel server)||!(user instanceof Player player)||player.getCooldowns().isOnCooldown(this)||getUseDuration(stack,user)-remaining<15)return;
        if(!player.getAbilities().instabuild){boolean paid=false;for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack ammo=player.getInventory().getItem(i);if(ammo.is(InfectionItems.SHARD.get())){ammo.shrink(1);paid=true;break;}}if(!paid)return;}
        Vec3 aim=player.getEyePosition().add(player.getLookAngle().scale(24));
        for(int i=-1;i<=1;i++)DesignProjectile.fire(server,player,aim.add(i*.5,0,i*.5),8,false);
        player.getCooldowns().addCooldown(this,35);stack.hurtAndBreak(1,player,net.minecraft.world.entity.LivingEntity.getSlotForHand(player.getUsedItemHand()));
    }
}
