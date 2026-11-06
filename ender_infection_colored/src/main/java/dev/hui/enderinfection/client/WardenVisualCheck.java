package dev.hui.enderinfection.client;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.entity.ContainmentWarden;
import dev.hui.enderinfection.institute.*;
import dev.hui.enderinfection.warden.WardenContent;
import dev.hui.enderinfection.worldgen.CentralInstitute;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in isolated native-client acceptance. Research unlocks and final kill are fixture actions. */
@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,value=Dist.CLIENT)
public final class WardenVisualCheck {
    private static final String WORLD="F8Warden_"+System.currentTimeMillis();
    private static final BlockPos ORIGIN=new BlockPos(0,-40,0);
    private static final List<String> SHOTS=new ArrayList<>();
    private static volatile WardenEncounter.Definition definition;
    private static volatile WardenEncounter.Status status;
    private static volatile String failure;
    private static volatile boolean built,busy,hold=true;
    private static volatile ContainmentWarden.Attack attack=ContainmentWarden.Attack.NONE;
    private static volatile int attackTick,deathTick;
    private static volatile double moved;
    private static boolean launched,finished;
    private static net.minecraft.world.entity.decoration.ArmorStand observer;
    private static boolean recovering;
    private static int step,age;
    private static long began;
    private static volatile Vec3 aim;
    private static BlockPos aimBlock;
    private static boolean vfx(){return Boolean.getBoolean("ender_infection.wardenVfxCheck");}
    private static boolean enabled(){return vfx()||Boolean.getBoolean("ender_infection.wardenVisualCheck");}
    private static Item item(String key){return BuiltInRegistries.ITEM.get(key.contains(":")?new ResourceLocation(key):new ResourceLocation(EnderInfection.MOD_ID,key));}
    private static ContainmentWarden boss(ServerPlayer p){
        var s=WardenEncounter.status(p.serverLevel(),ORIGIN.offset(definition.control().pos())).orElseThrow();
        return s.identity()!=null&&p.serverLevel().getEntity(s.identity()) instanceof ContainmentWarden mob?mob:null;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!enabled()||!(event.getEntity() instanceof ServerPlayer p)||!p.server.getWorldData().getLevelName().equals(WORLD))return;
        try{
            var level=p.serverLevel();definition=WardenEncounter.definition(level).orElseThrow();var futures=new ArrayList<CompletableFuture<?>>();
            for(int x=1;x<=5;x++)for(int z=1;z<=4;z++){level.setChunkForced(x,z,true);futures.add(level.getChunkSource().getChunkFuture(x,z,ChunkStatus.FULL,true));}
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(()->p.server.execute(()->{try{
                var a=ORIGIN.offset(definition.min()).below();var b=ORIGIN.offset(definition.max()).offset(-1,-1,-1);
                var clip=new BoundingBox(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ());int count=0;
                for(var tile:CentralInstitute.tiles())if(tile.x()<80&&tile.x()+tile.width()>40&&tile.z()<60&&tile.z()+tile.length()>36){var at=ORIGIN.offset(tile.x(),tile.y(),tile.z());if(!CentralInstitute.template(level,tile).placeInWorld(level,at,at,CentralInstitute.settings(clip),level.random,18))throw new IllegalStateException("F8 tile failed");count++;}
                if(count!=6)throw new IllegalStateException("Expected six clipped actual F8 tiles");
                InstituteOperations.registerFacility(level,ORIGIN);var data=OperationsData.get(level).save(new CompoundTag());
                for(var value:data.getList("Facilities",10)){var f=(CompoundTag)value;if(f.getLong("Origin")==ORIGIN.asLong()){
                    for(String key:List.of("Repaired","ContainmentPowered","Analyzed","Separated","ArchiveClaimed"))f.putBoolean(key,true);
                    var incident=new CompoundTag();incident.putString("Phase","COMPLETE");f.put("E8Incident",incident);
                }}
                level.getDataStorage().set(OperationsData.NAME,OperationsData.load(data));
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);level.setDayTime(6000);p.server.setDifficulty(Difficulty.NORMAL,true);
                p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.setNoGravity(true);p.getInventory().clearContent();p.getInventory().add(new ItemStack(item("research_keycard")));
                point(p,definition.control());built=true;System.out.println("WARDEN_VISUAL_SCENE actual_tiles=6 room=F8 fixture=research_unlock,stationary_combat_capture,final_kill");
            }catch(Exception e){failure=e.toString();}})).exceptionally(e->{failure=e.toString();return null;});
        }catch(Exception e){failure=e.toString();}
    }
    private static void call(Minecraft mc,Consumer<ServerPlayer> action){
        if(busy)return;busy=true;var server=mc.getSingleplayerServer();server.execute(()->{try{var p=server.getPlayerList().getPlayer(mc.player.getUUID());if(p==null)throw new IllegalStateException("Missing native player");action.accept(p);}catch(Exception e){failure=e.toString();}finally{busy=false;}});
    }
    private static void point(ServerPlayer p,WardenEncounter.Target target){var at=ORIGIN.offset(target.approach());aimBlock=ORIGIN.offset(target.pos());aim=Vec3.atCenterOf(aimBlock);p.teleportTo(p.serverLevel(),at.getX()+.5,at.getY(),at.getZ()+.5,0,0);}
    private static void view(ServerPlayer p,double degrees,double distance){
        var mob=boss(p);if(mob==null)throw new IllegalStateException("Missing subject");
        mob.setYRot(180);mob.setYHeadRot(180);mob.yBodyRot=180;
        aim=mob.position().add(0,1.7,0);aimBlock=null;
        for(double height:new double[]{0,1.5,3})for(double offset:new double[]{0,15,-15,30,-30}){
            double angle=Math.toRadians(degrees+offset);Vec3 at=mob.position().add(Math.sin(angle)*distance,height,Math.cos(angle)*distance);
            var box=p.getBoundingBox().move(at.subtract(p.position()));var eye=at.add(0,p.getEyeHeight(),0);
            if(!p.serverLevel().noCollision(p,box)||p.serverLevel().clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            p.teleportTo(p.serverLevel(),at.x,at.y,at.z,0,0);return;
        }
        throw new IllegalStateException("No unobstructed camera at "+degrees+" / "+distance);
    }
    private static void actionView(ServerPlayer p,double distance){
        var mob=boss(p);hold=true;mob.setNoAi(true);var at=Vec3.atBottomCenterOf(ORIGIN.offset(definition.spawn()));mob.teleportTo(at.x,at.y,at.z);mob.setYRot(180);mob.yBodyRot=180;
        view(p,180,distance);mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);mob.setTarget(p);mob.setNoAi(false);hold=false;
    }
    /** Client-only overview camera; the actual survival player still drives the real AI. */
    private static void overview(Minecraft mc){
        boolean overview=vfx()&&aimBlock==null&&(step>=8&&step<=12||step==17||step==24);
        if(!overview){if(mc.getCameraEntity()!=mc.player)mc.setCameraEntity(mc.player);if(vfx())mc.options.hideGui=false;return;}
        mc.options.hideGui=true;
        ContainmentWarden mob=null;
        for(var e:mc.level.entitiesForRendering())if(e instanceof ContainmentWarden w&&w.isAlive()){mob=w;break;}
        if(mob==null)return;
        if(observer==null||observer.level()!=mc.level)observer=new net.minecraft.world.entity.decoration.ArmorStand(mc.level,0,0,0);
        double angle=Math.toRadians(mob.visualAim());var forward=new Vec3(-Math.sin(angle),0,Math.cos(angle));var right=new Vec3(-Math.cos(angle),0,-Math.sin(angle));
        var focus=mob.position().add(forward.scale(1.8)).add(0,.9,0);Vec3 at=null;
        for(double side:new double[]{5,-5,0}){
            var candidate=mob.position().add(forward.scale(9)).add(right.scale(side)).add(0,5,0);
            if(mc.level.clip(new ClipContext(candidate,focus,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player)).getType()==HitResult.Type.MISS){at=candidate;break;}
        }
        if(at==null)at=mob.position().add(forward.scale(7)).add(0,4,0);
        var delta=focus.subtract(at);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        observer.moveTo(at.x,at.y-observer.getEyeHeight(),at.z,yaw,pitch);observer.xo=observer.getX();observer.yo=observer.getY();observer.zo=observer.getZ();observer.yRotO=yaw;observer.xRotO=pitch;
        observer.yHeadRot=observer.yHeadRotO=observer.yBodyRot=observer.yBodyRotO=yaw;
        mc.setCameraEntity(observer);mc.options.fov().set(78);
    }
    private static void camera(Minecraft mc){if(aim==null)return;Vec3 delta=aim.subtract(mc.player.getEyePosition(1));mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));}
    private static void equip(Minecraft mc,String key){for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(item(key))){mc.player.getInventory().selected=i;return;}throw new IllegalStateException("Missing hotbar item "+key);}
    private static boolean use(Minecraft mc){
        if(age<25||busy||mc.screen!=null)return false;equip(mc,"research_keycard");Vec3 from=mc.player.getEyePosition(1);
        var hit=mc.level.clip(new ClipContext(from,from.add(aim.subtract(from).normalize().scale(4.8)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(aimBlock))throw new IllegalStateException("Native terminal ray missed "+aimBlock+" hit "+hit.getBlockPos());
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);age=0;return true;
    }
    private static void shot(Minecraft mc,String name)throws Exception{
        mc.getToasts().clear();
        if(!name.equals("warning")&&!name.startsWith("console")&&!name.startsWith("reward"))mc.gui.getChat().clearMessages(false);
        var dir=mc.gameDirectory.toPath().resolve("screenshots");Files.createDirectories(dir);if(SHOTS.contains(name))throw new IllegalStateException("Duplicate shot");
        try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(dir.resolve((vfx()?"warden_vfx_":"warden_")+name+".png"));}SHOTS.add(name);System.out.println((vfx()?"WARDEN_VFX_CAPTURE ":"WARDEN_VISUAL_CAPTURE ")+name);
    }
    private static void finish(Minecraft mc,boolean pass){
        if(pass&&vfx()){try{WardenVfxClient.verify();System.out.println("WARDEN_VFX_STATS "+WardenVfxClient.statistics());}catch(Exception e){pass=false;failure=e.toString();}}
        finished=true;String result=(vfx()?(pass?"WARDEN_VFX_PASS":"WARDEN_VFX_FAIL"):(pass?"WARDEN_VISUAL_PASS":"WARDEN_VISUAL_FAIL"))+" step="+step+" shots="+SHOTS.size()+" "+failure;System.out.println(result);
        try{Files.writeString(mc.gameDirectory.toPath().resolve(vfx()?"warden-vfx-result.txt":"warden-visual-result.txt"),result+"\n"+String.join("\n",SHOTS)+"\n");}catch(Exception e){throw new RuntimeException(e);}mc.stop();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!enabled()||finished||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(began==0)began=System.currentTimeMillis();
        if(!launched&&mc.screen!=null&&(mc.screen instanceof TitleScreen||mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding"))&&mc.getOverlay()==null){
            launched=true;mc.options.pauseOnLostFocus=false;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.renderDistance().set(4);mc.options.fov().set(50);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,Difficulty.NORMAL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(7312026L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;
        }
        if(failure!=null||System.currentTimeMillis()-began>360000){if(failure==null)failure="timeout";finish(mc,false);return;}
        if(!built||mc.player==null||mc.level==null||mc.getOverlay()!=null)return;camera(mc);overview(mc);age++;
        mc.getToasts().clear();
        if(step>=3&&step<=12||step>=17&&step<=19)mc.gui.getChat().clearMessages(false);
        if(age%3==0&&!busy)call(mc,p->{
            status=WardenEncounter.status(p.serverLevel(),ORIGIN.offset(definition.control().pos())).orElseThrow();var mob=boss(p);
            if(mob!=null){if(hold&&mob.isAlive())mob.setNoAi(true);attack=mob.attack();attackTick=mob.attackTicks();deathTick=mob.deathTime;moved=mob.position().distanceTo(Vec3.atBottomCenterOf(ORIGIN.offset(definition.spawn())));}
        });
        if(status==null||busy)return;if(age>300&&step>0){failure="Stalled step "+step+" "+status+" attack="+attack+" tick="+attackTick;finish(mc,false);return;}
        // Capture after the observed client snapshot, not an integrated-server thread poll.
        ContainmentWarden observed=null;
        if(vfx())for(var entity:mc.level.entitiesForRendering())if(entity instanceof ContainmentWarden mob&&mob.isAlive()){observed=mob;break;}
        var clipAttack=observed!=null?observed.attack():attack;
        int clipTick=observed!=null?observed.visualClock():attackTick;
        try{switch(step){
            case 0->{if(age>50&&use(mc))step=1;}
            case 1->{if(status.phase()==WardenEncounter.Phase.WARNING&&age>8){shot(mc,"warning");step=2;}}
            case 2->{if(status.phase()==WardenEncounter.Phase.ACTIVE){call(mc,p->view(p,180,7));age=0;step=3;}}
            case 3->{if(age>40){shot(mc,"front");mc.options.hideGui=true;call(mc,p->view(p,90,7));age=0;step=4;}}
            case 4->{if(age>30){shot(mc,"side");call(mc,p->view(p,0,7));age=0;step=5;}}
            case 5->{if(age>30){shot(mc,"back");mc.options.hideGui=false;call(mc,p->{p.serverLevel().setDayTime(18000);view(p,180,7);});age=0;step=6;}}
            case 6->{if(age>35){shot(mc,"night_glow");call(mc,p->{var mob=boss(p);view(p,180,6);mob.setTarget(p);mob.setNoAi(false);hold=false;});age=0;step=7;}}
            case 7->{if(moved>.25&&age>8){shot(mc,"walk");call(mc,p->actionView(p,3));age=0;step=8;}}
            case 8->{if(clipAttack==ContainmentWarden.Attack.CLAW&&clipTick>=6&&clipTick<=13){shot(mc,"claw_windup");step=9;}}
            case 9->{if(clipAttack==ContainmentWarden.Attack.CLAW&&clipTick>=18&&clipTick<=26){shot(mc,"claw_hit");if(vfx()){mc.options.particles().set(ParticleStatus.MINIMAL);System.out.println("WARDEN_VFX_SETTINGS particles=MINIMAL case=stomp");}call(mc,p->actionView(p,5));age=0;step=10;}}
            case 10->{if(clipAttack==ContainmentWarden.Attack.STOMP&&clipTick>=11&&clipTick<=19){shot(mc,"stomp_windup");step=11;}}
            case 11->{if(clipAttack==ContainmentWarden.Attack.STOMP&&clipTick>=(vfx()?27:23)&&clipTick<=32){shot(mc,"stomp_hit");if(vfx()){mc.options.particles().set(ParticleStatus.ALL);System.out.println("WARDEN_VFX_SETTINGS particles=ALL case=burst");}call(mc,p->actionView(p,9));age=0;step=12;}}
            case 12->{
                if(vfx()&&clipAttack==ContainmentWarden.Attack.BURST){
                    if(clipTick>=10&&clipTick<=16&&!SHOTS.contains("burst_warning"))shot(mc,"burst_warning");
                    for(int i=0;i<3;i++)if(clipTick>=23+i*6&&clipTick<=25+i*6&&!SHOTS.contains("pulse_"+(i+1)))shot(mc,"pulse_"+(i+1));
                }
                if(clipAttack==ContainmentWarden.Attack.BURST&&clipTick>=(vfx()?38:25)&&clipTick<=40){shot(mc,"burst");hold=true;call(mc,p->{boss(p).setNoAi(true);point(p,definition.consoles().get(0));});age=0;step=13;}
            }
            case 13->{if(use(mc))step=14;}
            case 14->{if(vfx()&&status.consoles()==1&&age>=5&&!SHOTS.contains("console_discharge"))shot(mc,"console_discharge");if(status.consoles()==1&&age>20){shot(mc,"console_one");call(mc,p->point(p,definition.consoles().get(1)));age=0;step=15;}}
            case 15->{if(use(mc))step=16;}
            case 16->{if(status.exposed()&&age>15){call(mc,p->view(p,180,7));age=0;step=17;}}
            case 17->{if(age>30){shot(mc,"core_exposed");
                if(vfx()){hold=false;call(mc,p->boss(p).setNoAi(false));recovering=false;age=0;step=24;}
                else{call(mc,p->boss(p).hurt(p.serverLevel().damageSources().generic(),100000));age=0;step=18;}
            }}
            case 24->{
                if(!status.exposed()&&!recovering){recovering=true;age=0;hold=true;call(mc,p->boss(p).setNoAi(true));}
                if(recovering&&age>=4){shot(mc,"core_recovery");call(mc,p->boss(p).hurt(p.serverLevel().damageSources().generic(),100000));age=0;step=18;}
            }
            case 18->{if(status.phase()==WardenEncounter.Phase.DEFEATED&&deathTick>=12&&deathTick<=30){shot(mc,"death_mid");step=19;}}
            case 19->{if(deathTick>=43&&deathTick<=57){shot(mc,"death_kneel");call(mc,p->point(p,definition.control()));age=0;step=20;}}
            case 20->{if(use(mc))step=21;}
            case 21->{if(status.rewardClaimed()&&age>25){shot(mc,"reward_claimed");mc.setScreen(new InventoryScreen(mc.player));age=0;step=22;}}
            case 22->{if(age>15){shot(mc,"inventory");mc.setScreen(null);equip(mc,"containment_core");mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);age=0;step=23;}}
            case 23->{if(age>25&&mc.player.getCooldowns().isOnCooldown(item("containment_core"))){shot(mc,"core_cooldown");if(SHOTS.size()!=(vfx()?24:18))throw new IllegalStateException("Wrong native frame count");finish(mc,true);}}
            default->throw new IllegalStateException("Invalid scene step");
        }}catch(Exception e){failure=e.toString();finish(mc,false);}
    }
}
