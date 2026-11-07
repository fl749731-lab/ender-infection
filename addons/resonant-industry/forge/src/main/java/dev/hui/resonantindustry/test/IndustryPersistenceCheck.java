package dev.hui.resonantindustry.test;

import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Three actual processes use one dedicated world; no offline or simulated production. */
@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID)
public final class IndustryPersistenceCheck {
    private static IndustryPersistenceCheck check;
    private final MinecraftServer server;private final IndustryScene scene;private final String stage;
    private final Path receipt=Path.of("industry-identities.txt");private int ticks,step,pausedAt;
    private boolean finished;private List<IndustryNode> old;private List<CompoundTag> paused;
    private final boolean compound=Boolean.getBoolean("resonant_industry.compoundPersistence");
    private static CompoundTag save(IndustryNode n){return n.saveWithoutMetadata();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        var stage=System.getProperty("resonant_industry.persistenceStage","");if(stage.isEmpty()||event.phase!=TickEvent.Phase.END)return;
        try{if(check==null)check=new IndustryPersistenceCheck(event.getServer(),stage);if(!check.finished)check.advance();}
        catch(Exception|AssertionError e){e.printStackTrace();System.err.println("INDUSTRY_PERSISTENCE_FAIL "+stage+" "+e);if(check!=null)check.stop(false);else event.getServer().halt(false);}
    }
    private IndustryPersistenceCheck(MinecraftServer server,String stage){
        this.server=server;this.stage=stage;scene=new IndustryScene(server.overworld(),new BlockPos(1000000,64,1000000));
        scene.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        if(stage.equals("seed")){require(scene.generator()==null,"Seed world already used");scene.build();}else scene.force(true);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private List<IndustryNode> nodes(){return List.of(scene.generator(),scene.processor(),scene.centrifuge());}
    private List<String> ids(){return nodes().stream().map(n->save(n).getUUID("Identity").toString()).toList();}
    private IndustryNode active(){return compound?scene.centrifuge():scene.processor();}
    private void advance() throws Exception{
        require(++ticks<2600,"Persistence timeout stage="+stage+" step="+step);
        if(stage.equals("seed")){
            if(!scene.ready())return;
            if(step==0){for(var n:nodes())require(n.form().valid(),"Fixture structure invalid");scene.generator().setItem(0,new ItemStack(Items.COAL));scene.processor().setItem(0,new ItemStack(IndustryContent.item("ender_infection:mycelium_bundle"),2));scene.centrifuge().setItem(0,new ItemStack(Items.GLASS_BOTTLE,compound?4:2));
                if(compound){scene.centrifuge().setItem(4,new ItemStack(Items.PAPER,2));scene.centrifuge().setItem(1,new ItemStack(Items.CHARCOAL));
                    var port=IndustryNode.at(scene.level,scene.origin.offset(-11,0,4));require(port.setFluidChannel(1),"Compound water channel failed");
                    require(Platform.fluid(scene.level,port.getBlockPos(),Direction.EAST).fill("minecraft:water",2000,false)==2000,"Compound water rejected");port.setFluidChannel(0);}
                var input=scene.origin.offset(-5,0,2);require(Platform.fluid(scene.level,input,Direction.EAST).fill("minecraft:water",2000,false)==2000,"Fixture water rejected");step=1;}
            if(active().progress()>=40){var lines=new ArrayList<>(ids());lines.add(Integer.toString(active().progress()));Files.write(receipt,lines);System.out.println("INDUSTRY_ACTIVE_SEED_PASS compound="+compound+" progress="+active().progress());stop(true);}return;
        }
        require(stage.equals("resume")||stage.equals("finished"),"Unknown stage");
        if(step==0){
            if(!scene.ready())return;var expected=Files.readAllLines(receipt);require(ids().equals(expected.subList(0,3)),"Controller identities changed across real restart");
            if(stage.equals("finished")){completed();stop(true);return;}
            require(active().progress()>=Integer.parseInt(expected.get(3))||active().getItem(0).getCount()<(compound?4:2),"Active recipe progress was lost");
            require(scene.centrifuge().getItem(2).getCount()<(compound?4:2),"Fixture finished before unload could test an active chain");
            System.out.println("INDUSTRY_ACTIVE_RESTART_PASS ids="+ids());old=nodes();scene.force(false);step=1;return;
        }
        if(step==1){
            if(scene.chunks.stream().anyMatch(c->scene.level.getChunkSource().getChunkNow(c.x,c.z)!=null||scene.level.areEntitiesLoaded(c.toLong())))return;
            paused=old.stream().map(IndustryPersistenceCheck::save).toList();require(old.get(2).getItem(2).getCount()<(compound?4:2),"Unload did not pause the active production chain");pausedAt=ticks;System.out.println("INDUSTRY_CHUNKS_UNLOADED_PASS");step=2;return;
        }
        if(step==2){require(paused.equals(old.stream().map(IndustryPersistenceCheck::save).toList()),"Unloaded machine advanced");if(ticks-pausedAt<80)return;scene.force(true);step=3;return;}
        if(step==3){if(!scene.ready())return;require(ids().equals(Files.readAllLines(receipt).subList(0,3)),"Chunk reload changed identities");for(var n:nodes())require(n.operational(),"Reloaded ownership references invalid");System.out.println("INDUSTRY_CHUNKS_RELOADED_PASS");step=4;return;}
        if(scene.centrifuge().getItem(2).getCount()==(compound?4:2)){completed();stop(true);}
    }
    private void completed(){
        require(scene.centrifuge().getItem(2).is(IndustryContent.item("ender_infection:separated_reagent"))&&scene.centrifuge().getItem(2).getCount()==(compound?4:2),"Lost or repeated finished batches");
        require(scene.centrifuge().getItem(3).is(IndustryContent.item("fuel_residue"))&&scene.centrifuge().getItem(3).getCount()==2,"Wrong residual product count");
        require(scene.processor().getItem(0).isEmpty()&&scene.centrifuge().getItem(0).isEmpty(),"Finished batches did not consume their inputs");
        if(compound)require(scene.centrifuge().getItem(4).isEmpty()&&scene.centrifuge().getItem(1).is(Items.CHARCOAL),"Compound paper or catalyst changed across restart");
        int fluid=0;for(var n:nodes()){var t=save(n);fluid+=t.getInt("InputAmount")+t.getInt("OutputAmount");for(var entry:t.getList("ProcessTanks",10)){var tank=(CompoundTag)entry;fluid+=tank.getInt("InAmount")+tank.getInt("OutAmount");}}
        for(int z=2;z<=4;z++)fluid+=Platform.fluid(scene.level,scene.origin.offset(-8,0,z),Direction.UP).amount();for(int x=-9;x>=-10;x--)fluid+=Platform.fluid(scene.level,scene.origin.offset(x,0,4),Direction.UP).amount();
        require(fluid==(compound?1500:0),"Saved chain duplicated or stranded liquid: "+fluid);
        if(compound){var tank=save(scene.centrifuge());require(tank.getInt("OutputAmount")==1000&&tank.getList("ProcessTanks",10).getCompound(0).getInt("OutAmount")==500,"Compound returned fluids changed across real restart");System.out.println("INDUSTRY_COMPOUND_TWO_BATCHES_PASS");}
        System.out.println("INDUSTRY_TWO_BATCHES_PASS");
    }
    private void stop(boolean success){finished=true;server.saveEverything(false,true,true);if(success)System.out.println("INDUSTRY_PERSISTENCE_PASS "+stage);server.halt(false);}
}
