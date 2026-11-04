package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Local energy buffer; recharge is a paid transfer from exactly one assigned mother. */
public final class RiftAnchorEntity extends BlockEntity {
    public static final int CAPACITY=40;
    private int energy;
    private long suppressedUntil;
    private UUID mother;
    public RiftAnchorEntity(BlockPos p,BlockState s){super(RiftContent.ANCHOR_ENTITY.get(),p,s);}
    public int energy(){return energy;}
    public boolean suppressed(){return level!=null&&level.getGameTime()<suppressedUntil;}
    public boolean active(){return energy>=2&&!suppressed()&&!isRemoved();}
    public net.minecraft.network.chat.Component statusName(){return net.minecraft.network.chat.Component.translatable("message.ender_infection.anchor_"+(suppressed()?"suppressed":active()?"powered":"empty"));}
    public void assignMother(InfectedMob core){mother=core.getUUID();setChanged();}
    public void addEnergy(int n){energy=Math.min(CAPACITY,energy+Math.max(0,n));setChanged();syncState();}
    public boolean spend(int n){if(n<=0||!active()||energy<n)return false;energy-=n;setChanged();syncState();return true;}
    public void suppress(int ticks){if(level==null)return;suppressedUntil=Math.max(suppressedUntil,level.getGameTime()+ticks);setChanged();syncState();}
    private void syncState(){
        if(level==null||level.isClientSide||isRemoved())return;
        var s=getBlockState();var next=s.setValue(RiftAnchorBlock.ACTIVE,active()).setValue(RiftAnchorBlock.SUPPRESSED,suppressed());
        if(s!=next)level.setBlock(worldPosition,next,3);
    }
    public static void tick(Level level,BlockPos p,BlockState state,RiftAnchorEntity a){
        if(level.getGameTime()%20==0&&!a.suppressed()&&a.energy<CAPACITY&&a.mother!=null&&level instanceof ServerLevel server
            &&server.getEntity(a.mother) instanceof InfectedMob core&&core.kind==InfectedKind.CORE&&core.isAlive()&&!core.isNoAi()
            &&core.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<=32*32&&core.spendBiomass(1))a.addEnergy(1);
        a.syncState();
    }
    public static List<RiftAnchorEntity> nearby(ServerLevel level,BlockPos center,int radius){
        var list=new ArrayList<RiftAnchorEntity>();
        for(int x=(center.getX()-radius)>>4;x<=(center.getX()+radius)>>4;x++)for(int z=(center.getZ()-radius)>>4;z<=(center.getZ()+radius)>>4;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);if(chunk==null)continue;
            for(var be:chunk.getBlockEntities().values())if(be instanceof RiftAnchorEntity a&&!a.isRemoved()&&a.getBlockPos().distSqr(center)<=radius*radius)list.add(a);
        }
        list.sort(Comparator.comparingDouble(a->a.getBlockPos().distSqr(center)));return list;
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.putInt("Energy",energy);tag.putLong("SuppressedUntil",suppressedUntil);if(mother!=null)tag.putUUID("Mother",mother);}
    @Override public void load(CompoundTag tag){super.load(tag);energy=Math.max(0,Math.min(CAPACITY,tag.getInt("Energy")));suppressedUntil=tag.getLong("SuppressedUntil");mother=tag.hasUUID("Mother")?tag.getUUID("Mother"):null;}
}
