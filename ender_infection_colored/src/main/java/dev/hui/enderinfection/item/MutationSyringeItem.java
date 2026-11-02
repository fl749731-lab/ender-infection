package dev.hui.enderinfection.item;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.particle.FactionEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Injects a host and turns it into the matching brood unit. */
public final class MutationSyringeItem extends Item {
    public MutationSyringeItem(Properties properties){super(properties);}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(!(target.level() instanceof ServerLevel server))return InteractionResult.SUCCESS;
        if(!(target instanceof Cow||target instanceof Pig||target instanceof Sheep||target instanceof Zombie||target instanceof Skeleton||target instanceof Spider))return InteractionResult.PASS;
        InfectedKind kind=target instanceof Cow?InfectedKind.EARLY_COW:target instanceof Pig?InfectedKind.EARLY_PIG:target instanceof Sheep?InfectedKind.EARLY_SHEEP:target instanceof Zombie?InfectedKind.EARLY_ZOMBIE:target instanceof Skeleton?InfectedKind.EARLY_SKELETON:InfectedKind.EARLY_SPIDER;
        InfectedMob brood=InfectedContent.TYPES.get(kind).get().create(server);
        if(brood==null)return InteractionResult.FAIL;
        brood.moveTo(target.getX(),target.getY(),target.getZ(),target.getYRot(),0);
        if(!server.addFreshEntity(brood))return InteractionResult.FAIL;
        FactionEffects.shatter(server,brood.position(),18);
        server.playSound(null,target.blockPosition(),SoundEvents.ZOMBIE_INFECT,SoundSource.PLAYERS,1F,.8F);
        target.discard();
        if(!player.getAbilities().instabuild)stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}
