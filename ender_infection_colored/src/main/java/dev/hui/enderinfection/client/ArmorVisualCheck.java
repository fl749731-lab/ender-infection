package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.design.*;
import dev.hui.enderinfection.entity.WorldIntroduction;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.cache.object.GeoBone;

/** Opt-in native player input/equipment/render proof; all worlds live under build/. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class ArmorVisualCheck {
    private static final String WORLD="ArmorVisual_"+System.currentTimeMillis();
    private static final int FLOOR=-60;
    private static final List<String> SETS=List.of("brood_heavy","crystal_carapace");
    private static final List<String> SHOTS=List.of("front","back","walk","crouch","bow","seated","helmet","chestplate","leggings","boots","mixed","inventory","dark");
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private static final List<String> PIECES=List.of("helmet","chestplate","leggings","boots");
    private static final Map<EquipmentSlot,String> seen=new EnumMap<>(EquipmentSlot.class);
    private static boolean launched,finished,actionSent;private static volatile boolean ready;
    private static volatile String failure;private static int index,age,phaseTicks;private static long started;
    private static float arm,leg,body,error;
    static boolean enabled(){return Boolean.getBoolean("ender_infection.armorVisualCheck");}
    private static String phase(){return SHOTS.get(index%SHOTS.size());}
    private static String set(){return SETS.get(index/SHOTS.size());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);level.setDayTime(6000);
            level.getDataStorage().computeIfAbsent(WorldIntroduction.Data::load,()->WorldIntroduction.Data.forWorldAge(200),"ender_infection_world_introduction").complete();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
            for(var b:BlockPos.betweenClosed(new BlockPos(-12,FLOOR-1,-12),new BlockPos(24,FLOOR-1,24)))level.setBlock(b,Blocks.SMOOTH_STONE.defaultBlockState(),2);
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();prepare(p);
        }catch(Exception e){failure=e.toString();}
    }
    private static void prepare(ServerPlayer p){
        ready=false;var level=p.serverLevel();String phase=phase();p.stopRiding();p.stopUsingItem();
        level.getEntitiesOfClass(Pig.class,new net.minecraft.world.phys.AABB(-12,FLOOR-3,-12,24,FLOOR+15,24)).forEach(Entity::discard);
        p.teleportTo(level,8,FLOOR,8,0,0);p.setDeltaMovement(Vec3.ZERO);p.getInventory().clearContent();
        for(int i=0;i<SLOTS.size();i++){
            String useSet=phase.equals("mixed")&&i%2==1?SETS.get(1-index/SHOTS.size()):set();
            if(!PIECES.contains(phase)||phase.equals(PIECES.get(i)))p.setItemSlot(SLOTS.get(i),new ItemStack(DesignContent.ITEMS_BY_KEY.get(useSet+"_"+PIECES.get(i)).get()));
        }
        if(phase.equals("bow")){p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));p.getInventory().setItem(1,new ItemStack(Items.ARROW,64));}
        if(phase.equals("seated")){
            var pig=EntityType.PIG.create(level);pig.setNoAi(true);pig.setSilent(true);pig.setInvulnerable(true);pig.moveTo(8,FLOOR,8,0,0);level.addFreshEntity(pig);p.startRiding(pig,true);
        }
        if(phase.equals("inventory"))for(int i=0;i<8;i++)p.getInventory().setItem(9+i,new ItemStack(DesignContent.ITEMS_BY_KEY.get(SETS.get(i/4)+"_"+PIECES.get(i%4)).get()));
        for(var b:BlockPos.betweenClosed(new BlockPos(-2,FLOOR,-2),new BlockPos(18,FLOOR+8,18)))
            if(b.getY()==FLOOR+8||b.getX()==-2||b.getX()==18||b.getZ()==-2||b.getZ()==18)level.setBlock(b,(phase.equals("dark")?Blocks.BLACK_CONCRETE:Blocks.AIR).defaultBlockState(),2);
        level.setDayTime(phase.equals("dark")?18000:6000);ready=true;
    }
    static void observePose(Entity entity,HumanoidModel<?> base,GeoBone[] bones){
        if(!enabled()||!ready||entity!=Minecraft.getInstance().player)return;
        ModelPart[] parts={base.head,base.body,base.rightArm,base.leftArm,base.rightLeg,base.leftLeg,base.rightLeg,base.leftLeg};
        for(int i=0;i<bones.length;i++){
            if(bones[i]==null){failure="Missing native equipment bone "+i;return;}
            error=Math.max(error,Math.max(Math.abs(bones[i].getRotX()+parts[i].xRot),Math.max(Math.abs(bones[i].getRotY()+parts[i].yRot),Math.abs(bones[i].getRotZ()-parts[i].zRot))));
        }
        arm=bones[2].getRotX();leg=bones[4].getRotX();body=bones[1].getRotX();
    }
    static void observeSlot(Entity entity,EquipmentSlot slot,DesignArmor armor,GeoBone[] bones){
        if(!enabled()||!ready||entity!=Minecraft.getInstance().player)return;
        Set<Integer> expected=switch(slot){case HEAD->Set.of(0);case CHEST->Set.of(1,2,3);case LEGS->Set.of(4,5);case FEET->Set.of(6,7);default->Set.of();};
        for(int i=0;i<bones.length;i++)if(bones[i]==null||bones[i].isHidden()==expected.contains(i)){failure="Incorrect slot visibility "+slot+" bone="+i;return;}
        seen.put(slot,armor.set);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(started==0)started=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(50);mc.options.renderDistance().set(5);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(failure!=null)throw new IllegalStateException("ARMOR_VISUAL_FAIL "+failure);
        if(ready&&mc.level!=null&&mc.player!=null&&(mc.screen==null||mc.screen instanceof InventoryScreen)&&mc.getOverlay()==null){
            String phase=phase();mc.options.hideGui=!phase.equals("inventory");mc.options.setCameraType(phase.equals("back")?CameraType.THIRD_PERSON_BACK:CameraType.THIRD_PERSON_FRONT);age++;
            if(!actionSent&&age>=45&&mc.levelRenderer.isChunkCompiled(mc.player.blockPosition())&&mc.levelRenderer.hasRenderedAllChunks()){
                actionSent=true;seen.clear();error=0;
                if(phase.equals("walk"))mc.options.keyUp.setDown(true);
                if(phase.equals("crouch"))mc.options.keyShift.setDown(true);
                if(phase.equals("bow")){mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                if(phase.equals("inventory"))mc.setScreen(new InventoryScreen(mc.player){
                    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
                        // Keep the native item grid visible even when this test window lacks focus.
                        super.render(graphics,width/2+70,height/2+65,partialTick);
                    }
                });
            }
            if(!actionSent||++phaseTicks<15)return;
            try{
                int count=PIECES.contains(phase)?1:4;if(seen.size()!=count||error>.0001)throw new IllegalStateException("Pose/slot mismatch "+seen+" error="+error);
                for(int i=0;i<SLOTS.size();i++)if(seen.containsKey(SLOTS.get(i))){String expected=phase.equals("mixed")&&i%2==1?SETS.get(1-index/SHOTS.size()):set();if(!seen.get(SLOTS.get(i)).equals(expected))throw new IllegalStateException("Cross-set renderer leakage");}
                if(phase.equals("walk")&&Math.abs(leg)<.05)throw new IllegalStateException("Walking did not move armor legs");
                if(phase.equals("crouch")&&(!mc.player.isCrouching()||Math.abs(body)<.2))throw new IllegalStateException("Native crouch not applied");
                if(phase.equals("bow")&&(!mc.player.isUsingItem()||Math.abs(arm)<1))throw new IllegalStateException("Native bow pose not applied");
                if(phase.equals("seated")&&(!mc.player.isPassenger()||Math.abs(leg)<1))throw new IllegalStateException("Native riding pose not applied");
                int block=mc.level.getBrightness(LightLayer.BLOCK,mc.player.blockPosition().above()),sky=mc.level.getBrightness(LightLayer.SKY,mc.player.blockPosition().above());
                if(phase.equals("dark")&&(block!=0||sky!=0))throw new IllegalStateException("Dark room is lit");
                var folder=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(set()+"_"+phase+".png"));}
                System.out.println("ARMOR_VISUAL_CAPTURE "+set()+"_"+phase+" slots="+seen.size()+" pose_error="+error+" arm="+arm+" leg="+leg+" body="+body+" block="+block+" sky="+sky);
            }catch(Exception e){throw new RuntimeException(e);}
            ready=false;actionSent=false;age=phaseTicks=0;mc.options.keyUp.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);mc.setScreen(null);
            if(++index==SHOTS.size()*SETS.size()){finished=true;System.out.println("ARMOR_VISUAL_PASS screenshots="+index);mc.stop();return;}
            var uuid=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid));}catch(Exception e){failure=e.toString();}});
        }
        if(System.currentTimeMillis()-started>420000)throw new IllegalStateException("ARMOR_VISUAL_FAIL timeout index="+index);
    }
}
