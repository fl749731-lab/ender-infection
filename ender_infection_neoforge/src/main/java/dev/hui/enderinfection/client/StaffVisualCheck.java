package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.entity.WorldIntroduction;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import software.bernie.geckolib.animatable.GeoItem;

/** Opt-in real item use, hand views, GUI/ground render and unlit-room proof in a fresh world. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class StaffVisualCheck {
    private static final String WORLD="StaffVisual_"+System.currentTimeMillis();
    private static final int FLOOR=-60;
    private static final List<String> SHOTS=List.of("first_right_idle","first_right_cast","first_right_recover","first_right_recast","first_left_idle","first_left_cast","third_right","third_left","inventory","ground","dark_right","dark_cast");
    private static boolean launched,finished,actionSent;private static volatile boolean ready,acted;
    private static volatile String failure;private static int index,age,phaseTicks;private static long started;
    private static int groundId=-1;private static long previousId=-1;
    private static boolean enabled(){return Boolean.getBoolean("ender_infection.staffVisualCheck");}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.setDayTime(6000);
            level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(()->WorldIntroduction.Data.forWorldAge(200),(tag,registries)->WorldIntroduction.Data.load(tag),null),"ender_infection_world_introduction").complete();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var b:BlockPos.betweenClosed(new BlockPos(-12,FLOOR-1,-12),new BlockPos(24,FLOOR-1,24)))level.setBlock(b,Blocks.SMOOTH_STONE.defaultBlockState(),2);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();prepare(p);
        }catch(Exception e){failure=e.toString();}
    }
    private static void prepare(ServerPlayer p){
        ready=false;acted=false;var level=p.serverLevel();String phase=SHOTS.get(index);
        level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(-12,FLOOR-3,-12,24,FLOOR+15,24)).forEach(net.minecraft.world.entity.Entity::discard);
        p.teleportTo(level,8,FLOOR,8,0,0);p.setDeltaMovement(Vec3.ZERO);
        if(!phase.equals("first_right_recover")&&!phase.equals("first_right_recast")){
            p.getInventory().clearContent();var stack=new ItemStack(DesignContent.ITEMS_BY_KEY.get("resonance_staff").get());p.setItemInHand(phase.contains("left")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,stack);
            long id=GeoItem.getOrAssignId(stack,level);if(id==previousId)throw new IllegalStateException("Fresh stacks share animation id");previousId=id;
        }
        if(phase.equals("ground")){
            var drop=new ItemEntity(level,8,FLOOR+.2,10,new ItemStack(DesignContent.ITEMS_BY_KEY.get("resonance_staff").get()));drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);drop.setUnlimitedLifetime();drop.setPickUpDelay(32767);level.addFreshEntity(drop);groundId=drop.getId();p.getInventory().clearContent();p.teleportTo(level,8,FLOOR,8,0,35);
        }
        if(phase.startsWith("dark")){
            for(var b:BlockPos.betweenClosed(new BlockPos(-2,FLOOR-1,-2),new BlockPos(18,FLOOR+8,18)))
                if(b.getY()==FLOOR+8||b.getX()==-2||b.getX()==18||b.getZ()==-2||b.getZ()==18)level.setBlock(b,Blocks.BLACK_CONCRETE.defaultBlockState(),2);
            level.setDayTime(18000);
        }
        ready=true;
    }
    private static void act(ServerPlayer p){
        try{
            String phase=SHOTS.get(index);
            if(phase.endsWith("cast")){
                InteractionHand hand=phase.contains("left")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;var stack=p.getItemInHand(hand);var item=(ResonanceStaff)stack.getItem();int damage=stack.getDamageValue();
                if(p.getCooldowns().isOnCooldown(item))throw new IllegalStateException("Previous cooldown did not expire");
                var result=item.use(p.level(),p,hand);
                if(!result.getResult().consumesAction()||stack.getDamageValue()!=damage+2||!p.getCooldowns().isOnCooldown(item))throw new IllegalStateException("Native cast payment/cooldown failed");
                if(item.use(p.level(),p,hand).getResult()!=InteractionResult.FAIL||stack.getDamageValue()!=damage+2)throw new IllegalStateException("Cooldown permits duplicate cast");
                System.out.println("STAFF_NATIVE_CAST phase="+phase+" damage="+stack.getDamageValue()+" instance="+GeoItem.getId(stack)+" duplicate=blocked");
            }
            acted=true;
        }catch(Exception e){failure=e.toString();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled()||finished)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(70);mc.options.renderDistance().set(5);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),null);return;
        }
        if(failure!=null)throw new IllegalStateException("STAFF_VISUAL_FAIL "+failure);
        if(ready&&mc.level!=null&&mc.player!=null&&(mc.screen==null||mc.screen instanceof InventoryScreen)&&mc.getOverlay()==null){
            String phase=SHOTS.get(index);mc.options.hideGui=phase.startsWith("third");mc.options.setCameraType(phase.startsWith("third")?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);mc.player.setDeltaMovement(Vec3.ZERO);age++;
            if(!actionSent&&age>=45&&mc.levelRenderer.isSectionCompiled(mc.player.blockPosition())&&mc.levelRenderer.hasRenderedAllSections()){
                actionSent=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->act(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)));
                if(phase.equals("inventory"))mc.setScreen(new InventoryScreen(mc.player));
            }
            if(!acted)return;
            if(++phaseTicks<(phase.endsWith("cast")?5:phase.equals("first_right_recover")?30:15))return;
            try{
                var stack=mc.player.getItemInHand(phase.contains("left")?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);float angle=0,core=1;boolean triggered=false;
                if(stack.getItem() instanceof ResonanceStaff staff){
                    var renderer=(ResonanceStaffRenderer)GeoRenderProvider.of(stack).getGeoItemRenderer();var model=renderer.getGeoModel();angle=model.getBone("fork_left").orElseThrow().getRotZ();core=model.getBone("crystal_core").orElseThrow().getScaleX();
                    var controller=staff.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(stack)).getAnimationControllers().get("staff");triggered=controller.isPlayingTriggeredAnimation();
                    if(phase.endsWith("cast")&&(!triggered||Math.abs(angle)<.03))throw new IllegalStateException("Cast packet did not move fork: "+angle+" triggered="+triggered);
                    if(phase.equals("first_right_recover")&&(triggered||Math.abs(angle)>.01))throw new IllegalStateException("Staff did not recover");
                }
                int block=mc.level.getBrightness(LightLayer.BLOCK,mc.player.blockPosition().above()),sky=mc.level.getBrightness(LightLayer.SKY,mc.player.blockPosition().above());
                if(phase.startsWith("dark")&&(block!=0||sky!=0))throw new IllegalStateException("Dark room is lit");
                var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(phase+".png"));}
                System.out.println("STAFF_VISUAL_CAPTURE "+phase+" fork="+angle+" core_scale="+core+" triggered="+triggered+" block="+block+" sky="+sky);
            }catch(Exception e){throw new RuntimeException(e);}
            ready=false;actionSent=false;age=phaseTicks=0;mc.setScreen(null);
            if(++index==SHOTS.size()){
                finished=true;var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                    try{
                        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);var item=(ResonanceStaff)DesignContent.ITEMS_BY_KEY.get("resonance_staff").get();
                        // Exercise the last durability point with a fresh, never-rendered stack.
                        var last=new ItemStack(item);last.setDamageValue(last.getMaxDamage()-1);player.setItemInHand(InteractionHand.MAIN_HAND,last);player.getCooldowns().removeCooldown(item);
                        var result=item.use(player.level(),player,InteractionHand.MAIN_HAND);
                        if(!result.getResult().consumesAction()||!last.isEmpty()||!player.getCooldowns().isOnCooldown(item))throw new IllegalStateException("Last-durability cast failed");
                        System.out.println("STAFF_NATIVE_BREAK broken=true");mc.execute(()->{System.out.println("STAFF_VISUAL_PASS screenshots="+index);mc.stop();});
                    }catch(Exception error){mc.execute(()->{throw new IllegalStateException("STAFF_VISUAL_FAIL break",error);});}
                });return;
            }
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Exception e){failure=e.toString();}});
        }
        if(System.currentTimeMillis()-started>300000)throw new IllegalStateException("STAFF_VISUAL_FAIL timeout index="+index);
    }
}
