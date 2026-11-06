package dev.hui.enderinfection.test;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.institute.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("ender_institute_protection_checks")
@PrefixGameTestTemplate(false)
public final class InstituteProtectionGameTests {
    private static BlockPos reset(GameTestHelper h){
        h.getLevel().getDataStorage().set(OperationsData.NAME,OperationsData.load(new CompoundTag()));
        return h.absolutePos(BlockPos.ZERO);
    }
    private static FakePlayer actor(GameTestHelper h,GameType mode){
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"InstituteBreak"));player.setGameMode(mode);return player;
    }
    @GameTest(template="empty64",templateNamespace="ender_institute_protection_checks",timeoutTicks=100,batch="institute_lock_players")
    public static void survivalAndCreativeCannotBreakButOutsideTerrainRemainsEditable(GameTestHelper h){
        var level=h.getLevel();var origin=reset(h);InstituteProtection.register(level,origin);
        var wall=origin.offset(8,29,20);var outside=origin.offset(7,29,20);
        level.setBlock(wall,Blocks.GLASS.defaultBlockState(),3);level.setBlock(outside,Blocks.GLASS.defaultBlockState(),3);
        h.assertTrue(InstituteProtection.protectedBlock(level,wall,true)&&!InstituteProtection.protectedBlock(level,outside,false),"Template mask includes external terrain or misses its wall");
        var survival=actor(h,GameType.SURVIVAL);h.assertTrue(!survival.gameMode.destroyBlock(wall)&&level.getBlockState(wall).is(Blocks.GLASS),"Survival broke a locked wall");
        var creative=actor(h,GameType.CREATIVE);h.assertTrue(!creative.gameMode.destroyBlock(wall)&&level.getBlockState(wall).is(Blocks.GLASS),"Creative bypassed story protection");
        h.assertTrue(creative.gameMode.destroyBlock(outside)&&level.getBlockState(outside).isAir(),"Protection locked unrelated outside terrain");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_institute_protection_checks",timeoutTicks=100,batch="institute_lock_objective")
    public static void onlyTheRequiredStoryTargetCanBreakAndItsCompletionSurvivesReload(GameTestHelper h){
        var level=h.getLevel();var origin=reset(h);var offset=new BlockPos(8,29,20);var target=origin.offset(offset);var neighbour=target.south();
        InstituteProtection.register(level,origin);level.setBlock(target,Blocks.GLASS.defaultBlockState(),3);level.setBlock(neighbour,Blocks.GLASS.defaultBlockState(),3);
        InstituteProtection.requireBreakTargets(level,origin,Map.of(offset,Blocks.GLASS));
        var save=OperationsData.get(level).save(new CompoundTag());level.getDataStorage().set(OperationsData.NAME,OperationsData.load(save));
        var player=actor(h,GameType.CREATIVE);
        h.assertTrue(!player.gameMode.destroyBlock(neighbour),"An adjacent non-objective became breakable");
        h.assertTrue(player.gameMode.destroyBlock(target)&&level.getBlockState(target).isAir(),"The required objective was blocked");
        // Reload while deferred completion is pending; no stale facility object may retain the exception.
        level.getDataStorage().set(OperationsData.NAME,OperationsData.load(OperationsData.get(level).save(new CompoundTag())));
        h.runAfterDelay(3,()->{
            level.setBlock(target,Blocks.GLASS.defaultBlockState(),3);
            h.assertTrue(!player.gameMode.destroyBlock(target),"Completed objective remained a permanent protection bypass");h.succeed();
        });
    }
    @GameTest(template="empty64",templateNamespace="ender_institute_protection_checks",timeoutTicks=100,batch="institute_lock_progress")
    public static void riftUnlockPersistsAndOnlyAffectsItsOwnFacility(GameTestHelper h){
        var level=h.getLevel();var origin=reset(h);var other=origin.offset(256,0,0);InstituteProtection.register(level,origin);InstituteProtection.register(level,other);
        var wall=origin.offset(8,29,20);var second=other.offset(8,29,20);
        h.assertTrue(InstituteProtection.protectedBlock(level,wall,true)&&InstituteProtection.protectedBlock(level,second,true),"Fresh facilities started unlocked");
        InstituteProtection.openStoryRift(level,origin,wall);
        level.getDataStorage().set(OperationsData.NAME,OperationsData.load(OperationsData.get(level).save(new CompoundTag())));
        h.assertTrue(!InstituteProtection.protectedBlock(level,wall,false)&&InstituteProtection.protectedBlock(level,second,true),"Rift unlock reset on reload or leaked to another facility");
        level.setBlock(wall,Blocks.GLASS.defaultBlockState(),3);h.assertTrue(actor(h,GameType.CREATIVE).gameMode.destroyBlock(wall),"Opened story rift still blocked real mining");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_institute_protection_checks",timeoutTicks=120,batch="institute_lock_environment")
    public static void explosionsMobsAndPistonsCannotBypassTheLockedShell(GameTestHelper h){
        var level=h.getLevel();var origin=reset(h);InstituteProtection.register(level,origin);var wall=origin.offset(8,29,20);var outside=origin.offset(7,29,20);
        level.setBlock(wall,Blocks.GLASS.defaultBlockState(),3);level.setBlock(outside,Blocks.GLASS.defaultBlockState(),3);
        level.explode(null,outside.getX()+.5,outside.getY()+.5,outside.getZ()+.5,3,Level.ExplosionInteraction.TNT);
        h.assertTrue(level.getBlockState(wall).is(Blocks.GLASS)&&level.getBlockState(outside).isAir(),"Explosion destroyed the shell or failed to affect unprotected terrain");
        var mob=EntityType.ZOMBIE.create(level);var event=new LivingDestroyBlockEvent(mob,wall,level.getBlockState(wall));NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled(),"Mob block destruction bypassed the story lock");
        level.setBlock(outside,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,Direction.EAST),3);
        var piston=new PistonEvent.Pre(level,outside,Direction.EAST,PistonEvent.PistonMoveType.EXTEND);NeoForge.EVENT_BUS.post(piston);
        h.assertTrue(piston.isCanceled(),"Piston moved protected building blocks");h.succeed();
    }
    @GameTest(template="empty64",templateNamespace="ender_institute_protection_checks",timeoutTicks=100,batch="institute_lock_registration")
    public static void loadedBuildingMetadataProtectsBeforeAnyDeviceInteraction(GameTestHelper h){
        var level=h.getLevel();var origin=reset(h);var offset=new BlockPos(8,29,20);var at=origin.offset(offset);
        level.setBlock(at,Blocks.BARREL.defaultBlockState(),3);var be=level.getBlockEntity(at);be.getPersistentData().putIntArray("InstituteOriginOffset",new int[]{8,29,20});
        var saved=be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(saved,level.registryAccess());
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(level.getChunkAt(at),true));
        h.runAfterDelay(3,()->{h.assertTrue(OperationsData.get(level).find(origin)!=null&&InstituteProtection.protectedBlock(level,at,true),"Loaded template needed a first interaction before protection");h.succeed();});
    }
}
