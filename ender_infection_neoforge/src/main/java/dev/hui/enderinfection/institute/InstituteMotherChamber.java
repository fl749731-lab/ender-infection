package dev.hui.enderinfection.institute;

import dev.hui.enderinfection.entity.*;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.rift.RiftAnchorEntity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;

/** Deferred F7 mother creation. Worldgen only writes the anchor's marker NBT. */
public final class InstituteMotherChamber {
    private final BlockPos motherOffset, shellMin, shellMax;
    private final int stage;
    private boolean initialized, breached;
    private InstituteMotherChamber(BlockPos motherOffset,BlockPos shellMin,BlockPos shellMax,int stage,boolean initialized,boolean breached){
        this.motherOffset=motherOffset;this.shellMin=shellMin;this.shellMax=shellMax;this.stage=stage;this.initialized=initialized;this.breached=breached;
    }
    private static BlockPos offset(CompoundTag tag,String key){
        int[] a=tag.getIntArray(key);if(a.length!=3)throw new IllegalArgumentException("Invalid institute chamber "+key);
        for(int n:a)if(Math.abs(n)>16)throw new IllegalArgumentException("Chamber offset exceeds local bounds");
        return new BlockPos(a[0],a[1],a[2]);
    }
    public static InstituteMotherChamber load(CompoundTag tag){
        var mother=offset(tag,"MotherOffset");var min=offset(tag,"ShellMin");var max=offset(tag,"ShellMax");
        int width=max.getX()-min.getX()+1,height=max.getY()-min.getY()+1,depth=max.getZ()-min.getZ()+1;
        if(width<5||height<4||depth<5||width>13||height>11||depth>13)throw new IllegalArgumentException("Invalid chamber shell size");
        if(mother.getX()<=min.getX()||mother.getX()>=max.getX()||mother.getY()<=min.getY()||mother.getY()>=max.getY()||mother.getZ()<=min.getZ()||mother.getZ()>=max.getZ())
            throw new IllegalArgumentException("Chamber mother must be inside the shell");
        return new InstituteMotherChamber(mother,min,max,Math.max(1,Math.min(2,tag.getInt("MotherStage"))),tag.getBoolean("Initialized"),tag.getBoolean("Breached"));
    }
    private static int[] xyz(BlockPos p){return new int[]{p.getX(),p.getY(),p.getZ()};}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putIntArray("MotherOffset",xyz(motherOffset));tag.putIntArray("ShellMin",xyz(shellMin));tag.putIntArray("ShellMax",xyz(shellMax));
        tag.putInt("MotherStage",stage);tag.putBoolean("Initialized",initialized);tag.putBoolean("Breached",breached);return tag;
    }
    private boolean loaded(ServerLevel level,BlockPos anchor){
        var min=anchor.offset(shellMin);var max=anchor.offset(shellMax);
        if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight())return false;
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private boolean intact(ServerLevel level,BlockPos anchor){
        var min=anchor.offset(shellMin);var max=anchor.offset(shellMax);
        for(var at:BlockPos.betweenClosed(min,max)){
            if(at.getX()!=min.getX()&&at.getX()!=max.getX()&&at.getY()!=min.getY()&&at.getY()!=max.getY()&&at.getZ()!=min.getZ()&&at.getZ()!=max.getZ())continue;
            if(!level.getBlockState(at).isCollisionShapeFullBlock(level,at))return false;
        }
        return true;
    }
    private UUID identity(ServerLevel level,BlockPos anchor){
        return UUID.nameUUIDFromBytes(("ender_infection:F7/"+level.dimension().location()+"/"+anchor.asLong()).getBytes(StandardCharsets.UTF_8));
    }
    public void tick(ServerLevel level,RiftAnchorEntity anchor){
        var at=anchor.getBlockPos();if(level.getGameTime()%20!=0||!loaded(level,at))return;
        UUID uuid=identity(level,at);
        InfectedMob mother=level.getEntity(uuid) instanceof InfectedMob m&&m.kind==InfectedKind.CORE?m:null;
        if(!initialized){
            if(level.getDifficulty()==Difficulty.PEACEFUL||!intact(level,at))return;
            if(mother==null){
                mother=InfectedContent.TYPES.get(InfectedKind.CORE).get().create(level);if(mother==null)return;
                var tag=new CompoundTag();mother.addAdditionalSaveData(tag);tag.putInt("MotherStage",stage);tag.putInt("ColonyBiomass",64);
                tag.putBoolean("ExpansionNodeCreated",true);tag.putInt("EmergenceTicks",0);mother.readAdditionalSaveData(tag);
                var point=at.offset(motherOffset);mother.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,90,0);
                if(!level.noCollision(mother,mother.getBoundingBox())||level.containsAnyLiquid(mother.getBoundingBox()))return;
                mother.setUUID(uuid);mother.setPersistenceRequired();mother.getPersistentData().putBoolean("InstituteContained",true);
                mother.getPersistentData().putLong("InstituteAnchor",at.asLong());
                if(!level.addFreshEntity(mother))return;
            }
            anchor.assignMother(mother);anchor.addEnergy(24);initialized=true;anchor.setChanged();
        }
        // Initialized is a tombstone. Missing or dead entities never cause a respawn.
        if(mother==null||!mother.isAlive())return;
        if(!breached&&!intact(level,at)){
            breached=true;mother.releaseInstituteContainment();anchor.setChanged();
        }
        if(anchor.active()){
            var start=mother.position().add(0,mother.getBbHeight()*.55,0);var end=at.getCenter();
            for(int i=1;i<9;i++){
                var point=start.lerp(end,i/9.0);level.sendParticles(ParticleTypes.PORTAL,point.x,point.y,point.z,1,.015,.015,.015,0);
            }
        }
    }
}
