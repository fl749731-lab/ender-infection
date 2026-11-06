package dev.hui.enderinfection.institute;

import com.google.gson.JsonParser;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.LabPartBlock;
import dev.hui.enderinfection.worldgen.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Per-facility story protection. Only the template's original non-air cells are locked. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class InstituteProtection {
    private static final Map<Object,BitSet> MASKS=new WeakHashMap<>();
    private record PendingBreak(BlockPos origin,BlockPos root,long offset,String expected,long revision){}
    private static final Map<ServerLevel,List<PendingBreak>> PENDING=new WeakHashMap<>();
    private InstituteProtection(){}
    private static BitSet mask(ServerLevel level){
        var resources=level.getServer().getResourceManager();var cached=MASKS.get(resources);if(cached!=null)return cached;
        try(var reader=resources.getResourceOrThrow(CentralInstitute.id("central_institute.json")).openAsReader()){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var result=new BitSet();
            if(root.has("protection")){
                var definition=root.getAsJsonObject("protection");
                if(!definition.get("encoding").getAsString().equals("zlib-base64-bitset-le"))throw new IllegalArgumentException("Unsupported institute protection encoding");
                try(var in=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(definition.get("mask").getAsString())))){
                    int bytes=(CentralInstitute.WIDTH*CentralInstitute.HEIGHT*CentralInstitute.LENGTH+7)/8;
                    byte[] value=in.readNBytes(bytes+1);if(value.length!=bytes)throw new IllegalArgumentException("Invalid institute protection size");result=BitSet.valueOf(value);
                }
            }
            MASKS.put(resources,result);return result;
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    public static void register(ServerLevel level,BlockPos origin){
        if(OperationsData.get(level).find(origin)==null)InstituteOperations.registerFacility(level,origin);
    }
    private static boolean originalCell(ServerLevel level,BlockPos local){
        return InstituteOperations.validOffset(local)&&mask(level).get((local.getY()*CentralInstitute.LENGTH+local.getZ())*CentralInstitute.WIDTH+local.getX());
    }
    private static BlockPos targetRoot(ServerLevel level,BlockPos at){
        var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);if(chunk==null)return at;
        var state=chunk.getBlockState(at);return state.getBlock() instanceof LabPartBlock?LabPartBlock.anchor(at,state):at;
    }
    private static boolean required(ServerLevel level,OperationsData.Facility facility,BlockPos at){
        var root=targetRoot(level,at);String expected=facility.requiredBreaks.get(root.subtract(facility.origin).asLong());if(expected==null)return false;
        var chunk=level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
        return chunk!=null&&BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(root).getBlock()).toString().equals(expected);
    }
    public static boolean protectedBlock(ServerLevel level,BlockPos at,boolean allowRequiredStoryTarget){
        for(var facility:OperationsData.get(level).facilities.values()){
            if(!facility.riftOpened&&originalCell(level,at.subtract(facility.origin))
                    &&!(allowRequiredStoryTarget&&required(level,facility,at)))return true;
        }
        return false;
    }
    /** Scripts replace this list when their active objective changes. No ordinary device is implicitly exempt. */
    public static void requireBreakTargets(ServerLevel level,BlockPos origin,Map<BlockPos,Block> targets){
        var next=new LinkedHashMap<Long,String>();
        for(var target:targets.entrySet()){
            if(!originalCell(level,target.getKey()))throw new IllegalArgumentException("Story break target is outside the institute template");
            next.put(target.getKey().asLong(),BuiltInRegistries.BLOCK.getKey(target.getValue()).toString());
        }
        register(level,origin);var facility=OperationsData.get(level).find(origin);
        facility.requiredBreaks.clear();facility.requiredBreaks.putAll(next);facility.storyTargetRevision++;OperationsData.get(level).setDirty();
    }
    public static void openStoryRift(ServerLevel level,BlockPos origin,BlockPos center){
        register(level,origin);var facility=OperationsData.get(level).find(origin);if(facility.riftOpened)return;
        facility.riftOpened=true;OperationsData.get(level).setDirty();
        riftPulse(level,center);
    }
    static void riftPulse(ServerLevel level,BlockPos center){
        for(int i=0;i<72;i++){
            double angle=i*Math.PI/36;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,center.getX()+.5+Math.cos(angle)*1.8,center.getY()+2.5+Math.sin(angle)*2.5,center.getZ()+.5,2,.08,.08,.08,.04);
        }
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        if(protectedBlock(level,event.getPos(),true)){
            event.setCanceled(true);event.getPlayer().displayClientMessage(Component.translatable("message.ender_infection.institute_protected"),true);return;
        }
        var root=targetRoot(level,event.getPos());
        for(var facility:OperationsData.get(level).facilities.values())if(required(level,facility,event.getPos())){
            var offset=root.subtract(facility.origin).asLong();String expected=facility.requiredBreaks.get(offset);
            // Server.execute can run inline. Recheck after the actual tick instead.
            PENDING.computeIfAbsent(level,ignored->new ArrayList<>()).add(new PendingBreak(facility.origin,root,offset,expected,facility.storyTargetRevision));
        }
    }
    @SubscribeEvent public static void finishBreaks(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var checks=PENDING.remove(level);if(checks==null)return;
        var data=OperationsData.get(level);
        for(var check:checks){
            var chunk=level.getChunkSource().getChunkNow(check.root.getX()>>4,check.root.getZ()>>4);
            if(chunk==null||BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(check.root).getBlock()).toString().equals(check.expected))continue;
            var facility=data.find(check.origin);
            if(facility!=null&&facility.storyTargetRevision==check.revision&&check.expected.equals(facility.requiredBreaks.get(check.offset))){facility.requiredBreaks.remove(check.offset);data.setDirty();}
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){
        if(event.getLevel() instanceof ServerLevel level)event.getAffectedBlocks().removeIf(at->protectedBlock(level,at,false));
    }
    @SubscribeEvent public static void mobBreak(LivingDestroyBlockEvent event){
        if(event.getEntity().level() instanceof ServerLevel level&&protectedBlock(level,event.getPos(),false))event.setCanceled(true);
    }
    @SubscribeEvent public static void piston(PistonEvent.Pre event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var resolver=event.getStructureHelper();if(resolver==null||!resolver.resolve())return;
        var direction=event.getPistonMoveType().isExtend?event.getDirection():event.getDirection().getOpposite();
        if(resolver.getToPush().stream().anyMatch(at->protectedBlock(level,at,false)||protectedBlock(level,at.relative(direction),false))
                ||resolver.getToDestroy().stream().anyMatch(at->protectedBlock(level,at,false)))event.setCanceled(true);
    }
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        level.getServer().execute(()->{
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk)return;
            for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getStructure() instanceof CentralInstituteStructure){
                var box=start.getBoundingBox();register(level,new BlockPos(box.minX(),box.minY(),box.minZ()));
            }
            for(var entity:chunk.getBlockEntities().values()){
                int[] offset=entity.getPersistentData().getIntArray("InstituteOriginOffset");if(offset.length!=3)continue;
                var local=new BlockPos(offset[0],offset[1],offset[2]);var origin=entity.getBlockPos().subtract(local);
                if(InstituteOperations.validOffset(local)&&CentralInstitute.validateSite(level,origin,net.minecraft.world.level.block.Rotation.NONE)==null)register(level,origin);
            }
        });
    }
}
