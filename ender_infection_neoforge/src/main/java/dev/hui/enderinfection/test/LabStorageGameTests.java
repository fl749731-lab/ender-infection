package dev.hui.enderinfection.test;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.design.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

/** Native storage semantics in small independent arenas; never shares the giant institute run. */
@GameTestHolder("ender_storage_checks")
@PrefixGameTestTemplate(false)
public final class LabStorageGameTests {
    private static final String[] KEYS={"lab_cabinet","lab_cabinet_eroded","lab_workbench","lab_workbench_eroded",
            "lab_freezer","lab_storage_crate","lab_shelf","lab_glass_cabinet","lab_cryo_cabinet",
            "lab_server_rack","lab_control_console","lab_rolling_cart","lab_sink"};
    private static final String LOOT="ender_infection:chests/central_institute/furniture/research";
    private static ResourceLocation id(String key){return ResourceLocation.parse(key);}
    private static Block block(String key){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id("ender_infection:"+key)).orElseThrow();}
    private static ServerPlayer player(GameTestHelper h,BlockPos root){
        var profile=new GameProfile(UUID.randomUUID(),"Store"+UUID.randomUUID().toString().substring(0,8));
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        // NeoForge's FakePlayer deliberately disables openMenu. Use the actual vanilla
        // ServerPlayer implementation, with a no-op transport from the loader fixture.
        player.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        player.setPos(root.getX()+.5,root.getY(),root.getZ()-2.5);player.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(player);return player;
    }
    private static void removePlayer(GameTestHelper h,ServerPlayer player){player.closeContainer();h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
    private static BlockPos root(GameTestHelper h){return h.absolutePos(new BlockPos(12,2,12));}
    private static void clean(GameTestHelper h,BlockPos root){
        var level=h.getLevel();if(level.getBlockEntity(root) instanceof LabStorageInventory inventory)inventory.clearContent();
        level.removeBlock(root,false);
        for(var pos:BlockPos.betweenClosed(root.offset(-4,0,-4),root.offset(4,4,4)))level.removeBlock(pos,false);
        for(var pos:BlockPos.betweenClosed(root.offset(-5,-1,-5),root.offset(5,-1,5)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(7)))item.discard();
    }
    private static LabStorageInventory place(GameTestHelper h,BlockPos root,String key,ServerPlayer player){
        clean(h,root);player.getInventory().clearContent();player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setPos(root.getX()+.5,root.getY(),root.getZ()-2.5);player.setYRot(0);
        var stack=new ItemStack(block(key).asItem());player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var support=root.below();var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
        var result=((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit));
        h.assertTrue(result.consumesAction()&&h.getLevel().getBlockState(root).is(block(key)),"Native BlockItem placement failed: "+key);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(h.getLevel().getBlockEntity(root) instanceof LabStorageInventory,"Storage block has no container BE: "+key);
        var inventory=(LabStorageInventory)h.getLevel().getBlockEntity(root);
        h.assertTrue(inventory.isStorage()&&inventory.getContainerSize()==27,"Storage must expose exactly 27 slots: "+key);return inventory;
    }
    private static void load(LabStorageInventory inventory,CompoundTag tag,GameTestHelper h){inventory.loadWithComponents(tag,h.getLevel().registryAccess());}
    private static void assignLoot(LabStorageInventory inventory,GameTestHelper h){
        var tag=inventory.saveInventoryTag();tag.remove("Items");tag.putString("LootTable",LOOT);tag.putLong("LootTableSeed",7312026L);load(inventory,tag,h);
    }
    private static LabStorageInventory reload(GameTestHelper h,BlockPos root,CompoundTag tag){
        var state=h.getLevel().getBlockState(root);var inventory=(LabStorageInventory)((BaseEntityBlock)state.getBlock()).newBlockEntity(root,state);
        h.getLevel().setBlockEntity(inventory);load(inventory,tag,h);return inventory;
    }
    private static InteractionResult use(GameTestHelper h,ServerPlayer player,BlockPos at){
        var level=h.getLevel();var state=level.getBlockState(at);var hit=new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false);
        if(state.getBlock() instanceof LabFixtureBlock fixture)return fixture.use(state,level,at,player,InteractionHand.MAIN_HAND,hit);
        if(state.getBlock() instanceof LabStorageBlock storage)return storage.use(state,level,at,player,InteractionHand.MAIN_HAND,hit);
        if(state.getBlock() instanceof LabPartBlock part)return part.use(state,level,at,player,InteractionHand.MAIN_HAND,hit);
        throw new AssertionError("Not an interactive lab block: "+state);
    }
    private static ChestMenu open(GameTestHelper h,ServerPlayer player,BlockPos at,LabStorageInventory inventory){
        use(h,player,at);h.assertTrue(player.containerMenu instanceof ChestMenu,"Storage interaction did not open the real server ChestMenu: "+at);
        var menu=(ChestMenu)player.containerMenu;h.assertTrue(menu.getType()==MenuType.GENERIC_9x3&&menu.slots.size()==63&&menu.getRowCount()==3&&menu.getContainer()==inventory,"Wrong native menu/slot backing");return menu;
    }
    private static boolean hasUnopenedLoot(CompoundTag tag){return tag.contains("LootTable",8);}
    private static int countItems(net.minecraft.world.Container container,Item item){int count=0;for(int slot=0;slot<container.getContainerSize();slot++)if(container.getItem(slot).is(item))count+=container.getItem(slot).getCount();return count;}
    private static int drops(GameTestHelper h,BlockPos root,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(root).inflate(3)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();}

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=160,batch="storage_lazy_loot")
    public static void everyVariantUsesNativeMenuAndDefersLootUntilOpening(GameTestHelper h){
        var root=root(h);var player=player(h,root);
        for(String key:KEYS){
            var inventory=place(h,root,key,player);assignLoot(inventory,h);
            var before=inventory.saveInventoryTag();h.assertTrue(hasUnopenedLoot(before)&&before.getString("LootTable").equals(LOOT)&&before.getList("Items",10).isEmpty(),"Saving eagerly generated loot: "+key);
            var update=inventory.getUpdateTag(h.getLevel().registryAccess());h.assertTrue(!update.contains("Items")&&!update.contains("LootTable"),"Chunk update leaked container contents/loot: "+key);
            inventory=reload(h,root,before);h.assertTrue(hasUnopenedLoot(inventory.saveInventoryTag()),"Reload eagerly generated loot: "+key);
            var target=h.getLevel().getBlockState(root).getBlock() instanceof LabFixtureBlock?root.above():root;
            var menu=open(h,player,target,inventory);h.assertTrue(!hasUnopenedLoot(inventory.saveInventoryTag()),"First native menu did not consume the deferred loot table: "+key);
            h.assertTrue(!inventory.saveInventoryTag().getList("Items",10).isEmpty(),"Room loot was empty after real opening: "+key);
            var generated=inventory.saveInventoryTag();player.closeContainer();open(h,player,root,inventory);player.closeContainer();
            h.assertTrue(generated.equals(inventory.saveInventoryTag()),"Reopening rerolled or changed loot: "+key);
        }
        removePlayer(h,player);clean(h,root);System.out.println("LAB_STORAGE_LAZY_LOOT_PASS variants="+KEYS.length);h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=160,batch="storage_menu_persistence")
    public static void nativeShiftTransferAndSaveReloadKeepContentsWithoutReroll(GameTestHelper h){
        var root=root(h);var player=player(h,root);
        for(String key:KEYS){
            var inventory=place(h,root,key,player);inventory.setItem(0,new ItemStack(Items.DIAMOND,7));
            var menu=open(h,player,root,inventory);menu.quickMoveStack(player,0);
            h.assertTrue(countItems(inventory,Items.DIAMOND)==0&&countItems(player.getInventory(),Items.DIAMOND)==7,"Native shift extraction lost/duplicated items: "+key);
            int playerSlot=-1;for(int slot=27;slot<63;slot++)if(menu.getSlot(slot).getItem().is(Items.DIAMOND)){playerSlot=slot;break;}
            h.assertTrue(playerSlot>=27,"Transfer did not enter player inventory");menu.quickMoveStack(player,playerSlot);
            h.assertTrue(countItems(inventory,Items.DIAMOND)==7&&countItems(player.getInventory(),Items.DIAMOND)==0,"Native shift insertion lost/duplicated items: "+key);player.closeContainer();
            var saved=inventory.saveInventoryTag();inventory=reload(h,root,saved);h.assertTrue(countItems(inventory,Items.DIAMOND)==7&&!hasUnopenedLoot(inventory.saveInventoryTag()),"Saved contents were lost or acquired a new loot table: "+key);
            open(h,player,root,inventory);player.closeContainer();h.assertTrue(countItems(inventory,Items.DIAMOND)==7,"Reload/reopen changed stored contents: "+key);
        }
        removePlayer(h,player);clean(h,root);System.out.println("LAB_STORAGE_TRANSFER_SAVE_PASS variants="+KEYS.length);h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=160,batch="storage_drop_once")
    public static void breakingRootsOrFootprintPartsDropsInventoryAndBlockExactlyOnce(GameTestHelper h){
        var root=root(h);var player=player(h,root);int destructions=0;
        for(String key:KEYS)for(boolean part:new boolean[]{false,true}){
            if(part&&!(block(key) instanceof LabFixtureBlock))continue;
            var inventory=place(h,root,key,player);inventory.setItem(0,new ItemStack(Items.IRON_INGOT,11));inventory.setItem(7,new ItemStack(Items.DIAMOND,3));
            var target=part?root.above():root;h.getLevel().destroyBlock(target,true);
            h.assertTrue(h.getLevel().getBlockEntity(root)==null&&h.getLevel().getBlockState(root).isAir(),"Destroying part did not remove its root: "+key);
            h.assertTrue(drops(h,root,Items.IRON_INGOT)==11&&drops(h,root,Items.DIAMOND)==3,"Root/part removal duplicated or lost contents: "+key+" part="+part);
            h.assertTrue(drops(h,root,block(key).asItem())==1,"Root block must drop once: "+key+" part="+part);
            for(var at:BlockPos.betweenClosed(root.offset(-2,0,-2),root.offset(2,2,2)))h.assertTrue(!(h.getLevel().getBlockState(at).getBlock() instanceof LabPartBlock),"Orphan part after container destruction: "+at);
            destructions++;
        }
        removePlayer(h,player);clean(h,root);System.out.println("LAB_STORAGE_DROP_ONCE_PASS destructions="+destructions);h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=160,batch="storage_openers")
    public static void multipleViewersClosingAndDisconnectingReleaseOpenState(GameTestHelper h){
        var root=root(h);var first=player(h,root);var second=player(h,root);
        for(String key:KEYS){
            var inventory=place(h,root,key,first);second.setPos(first.getX()+1,first.getY(),first.getZ());
            open(h,first,root,inventory);open(h,second,root,inventory);
            h.assertTrue(inventory.openerCount()==2&&h.getLevel().getBlockState(root).getValue(BlockStateProperties.OPEN),"Two real server menus did not hold the drawer open: "+key);
            first.closeContainer();h.assertTrue(inventory.openerCount()==1&&h.getLevel().getBlockState(root).getValue(BlockStateProperties.OPEN),"First close shut another viewer's drawer: "+key);
            // Entity removal models the authoritative disconnect/untracking path without a network client.
            h.getLevel().removePlayerImmediately(second,Entity.RemovalReason.UNLOADED_WITH_PLAYER);inventory.recheckOpeners();
            h.assertTrue(inventory.openerCount()==0&&!h.getLevel().getBlockState(root).getValue(BlockStateProperties.OPEN),"Disconnected viewer left a stale opener: "+key);
            second.closeContainer();second=player(h,root);
            var persisted=inventory.saveInventoryTag();var state=h.getLevel().getBlockState(root);h.getLevel().setBlock(root,state.setValue(BlockStateProperties.OPEN,true),3);
            inventory=reload(h,root,persisted);inventory.recheckOpeners();h.assertTrue(!h.getLevel().getBlockState(root).getValue(BlockStateProperties.OPEN),"Reload left a persisted OPEN with no viewers: "+key);
        }
        removePlayer(h,first);removePlayer(h,second);clean(h,root);System.out.println("LAB_STORAGE_VIEWERS_PASS variants="+KEYS.length);h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=160,batch="storage_player_hopper")
    public static void playerPlacementStartsEmptyAndVanillaHoppersCanPullAndPush(GameTestHelper h){
        var root=root(h);var player=player(h,root);var level=h.getLevel();
        for(String key:KEYS){
            var inventory=place(h,root,key,player);var saved=inventory.saveInventoryTag();h.assertTrue(!hasUnopenedLoot(saved)&&saved.getList("Items",10).isEmpty(),"Player placement received generated structure loot: "+key);
            inventory.setItem(0,new ItemStack(Items.IRON_INGOT,4));var lower=root.below();level.setBlock(lower,Blocks.HOPPER.defaultBlockState(),3);var pull=(HopperBlockEntity)level.getBlockEntity(lower);
            h.assertTrue(HopperBlockEntity.suckInItems(level,pull)&&countItems(inventory,Items.IRON_INGOT)==3&&countItems(pull,Items.IRON_INGOT)==1,"Vanilla hopper cannot extract from storage root: "+key);
            level.removeBlock(lower,false);var input=root.north();var hopperState=Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,Direction.SOUTH);level.setBlock(input,hopperState,3);var push=(HopperBlockEntity)level.getBlockEntity(input);push.setItem(0,new ItemStack(Items.GOLD_INGOT,2));push.setCooldown(0);HopperBlockEntity.pushItemsTick(level,input,hopperState,push);
            h.assertTrue(countItems(inventory,Items.GOLD_INGOT)==1&&countItems(push,Items.GOLD_INGOT)==1,"Vanilla hopper cannot insert into storage root: "+key);
        }
        var stacked=place(h,root,"lab_storage_crate",player);stacked.setItem(0,new ItemStack(Items.PAPER,3));level.setBlock(root.above(),block("lab_storage_crate").defaultBlockState(),3);
        var stackedMenu=open(h,player,root,stacked);
        h.assertTrue(stackedMenu.getSlot(0).getItem().is(Items.PAPER)&&stackedMenu.getSlot(0).getItem().getCount()==3&&stacked.openerCount()==1,"Stacked crate menu lost its real inventory/viewer");
        h.assertTrue(!level.getBlockState(root).getValue(BlockStateProperties.OPEN),"Stacked crate lid clips into the colliding crate above");
        level.removeBlock(root.above(),false);stacked.recheckOpeners();
        h.assertTrue(player.containerMenu==stackedMenu&&stacked.openerCount()==1&&level.getBlockState(root).getValue(BlockStateProperties.OPEN),"Unblocked crate did not resume its open model while the same menu remained usable");player.closeContainer();
        removePlayer(h,player);clean(h,root);System.out.println("LAB_STORAGE_PLAYER_HOPPER_PASS variants="+KEYS.length+" stacked_crate_menu=true");h.succeed();
    }

    @GameTest(template="empty64",templateNamespace="ender_storage_checks",timeoutTicks=100,batch="storage_noncontainers")
    public static void nonStoragePropsStayNonContainersAndCurtainOpensFromItsPart(GameTestHelper h){
        var root=root(h);var player=player(h,root);var level=h.getLevel();
        for(String key:new String[]{"incubator","surgery_table","lab_window","lab_door","lab_hatch","lab_vent","lab_privacy_curtain"}){
            clean(h,root);var block=(LabFixtureBlock)block(key);var state=block.defaultBlockState();level.setBlock(root,state,3);block.fillParts(level,root,state);
            var be=(LabStorageInventory)level.getBlockEntity(root);h.assertTrue(!be.isStorage()&&be.getContainerSize()==0,"Nonstorage prop accidentally exposes inventory: "+key);
            use(h,player,root);h.assertTrue(!(player.containerMenu instanceof ChestMenu),"Nonstorage prop opened a chest menu: "+key);
            if(key.equals("lab_privacy_curtain")){h.assertTrue(level.getBlockState(root).getValue(BlockStateProperties.OPEN),"Curtain did not open");use(h,player,root.above());h.assertTrue(!level.getBlockState(root).getValue(BlockStateProperties.OPEN),"Curtain part did not close its root");}
        }
        for(String key:new String[]{"lab_microscope","lab_centrifuge","lab_cooling_pump","lab_task_lamp"}){
            clean(h,root);level.setBlock(root,block(key).defaultBlockState(),3);h.assertTrue(level.getBlockEntity(root)==null&&level.getBlockState(root).getMenuProvider(level,root)==null,"Instrument unexpectedly has a chest inventory: "+key);
        }
        h.assertTrue(level.getBlockState(root).getLightEmission()==14,"Task lamp lost its real block light");
        removePlayer(h,player);clean(h,root);System.out.println("LAB_STORAGE_NONCONTAINERS_PASS");h.succeed();
    }
}
