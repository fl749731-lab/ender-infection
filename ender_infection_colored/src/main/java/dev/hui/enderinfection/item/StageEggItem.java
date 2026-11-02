package dev.hui.enderinfection.item;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Spawn egg that drops a mother already at the requested stage, so every stage can be tested directly. */
public final class StageEggItem extends Item {
    private final int stage;
    public StageEggItem(int stage,Properties properties){super(properties);this.stage=stage;}
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        if(!(level instanceof ServerLevel server))return InteractionResult.SUCCESS;
        BlockPos pos=context.getClickedPos().relative(context.getClickedFace());
        InfectedMob core=InfectedContent.TYPES.get(InfectedKind.CORE).get().create(server);
        if(core==null)return InteractionResult.FAIL;
        CompoundTag tag=new CompoundTag();
        core.addAdditionalSaveData(tag);
        tag.putInt("MotherStage",stage);
        core.readAdditionalSaveData(tag);
        core.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,context.getHorizontalDirection().toYRot(),0);
        if(!server.noCollision(core,core.getBoundingBox())||server.containsAnyLiquid(core.getBoundingBox()))return InteractionResult.FAIL;
        if(!server.addFreshEntity(core))return InteractionResult.FAIL;
        Player player=context.getPlayer();
        if(player==null||!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
        level.playSound(null,pos,SoundEvents.ENDERMAN_TELEPORT,SoundSource.NEUTRAL,.8F,1.1F);
        return InteractionResult.CONSUME;
    }
}
