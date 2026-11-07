package dev.hui.resonantindustry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.hui.resonantindustry.*;
import dev.hui.resonantindustry.api.IndustryApi;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class IndustryClient {
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{MenuScreens.register(ResonantIndustry.menuType(),IndustryScreen::new);MenuScreens.register(ResonantIndustry.benchMenuType(),LabBenchScreen::new);MenuScreens.register(ResonantIndustry.stationMenuType(),LabBenchScreen::new);for(String name:java.util.List.of("reagent_bottle","dropper_bottle","pipette","test_tube","beaker")){var item=IndustryContent.item(name);net.minecraft.client.renderer.item.ItemProperties.register(item,IndustryContent.id("fill"),(stack,world,entity,seed)->Math.min(1f,LabPlatform.data(stack).getInt("LabAmount")/(float)((LabVesselItem)item).capacity));}IndustryContent.OPEN_MANUAL=()->Minecraft.getInstance().setScreen(new IndustryManualScreen());});}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(ResonantIndustry.nodeType(),Spindle::new);e.registerBlockEntityRenderer(ResonantIndustry.benchType(),Bench::new);}
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item e){for(String name:java.util.List.of("reagent_bottle","dropper_bottle","pipette","test_tube","beaker"))e.register((stack,tint)->{if(tint!=0)return -1;String id=LabPlatform.data(stack).getString("LabFluid");if(!Platform.validFluid(id))return 0xff67bde8;var fluid=new net.minecraftforge.fluids.FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id)),1);return net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);},IndustryContent.item(name));}
    public static final class Bench implements BlockEntityRenderer<LabBenchEntity>{
        public Bench(BlockEntityRendererProvider.Context context){}
        @Override public void render(LabBenchEntity bench,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-bench.getBlockState().getValue(LabBenchBlock.FACING).toYRot()));pose.translate(-.5,0,-.5);
            for(int i=0;i<6;i++){var item=bench.getItem(i);if(item.isEmpty())continue;var p=LabBenchEntity.center(i);pose.pushPose();pose.translate(p.x,p.y+.135,p.z);pose.scale(.27f,.27f,.27f);Minecraft.getInstance().getItemRenderer().renderStatic(item,net.minecraft.world.item.ItemDisplayContext.FIXED,light,overlay,pose,buffers,bench.getLevel(),i);pose.popPose();}pose.popPose();
        }
    }
    public static final class Spindle implements BlockEntityRenderer<IndustryNode>{
        public Spindle(BlockEntityRendererProvider.Context context){}
        private void model(String name,PoseStack pose,MultiBufferSource buffers,int light,int overlay){Minecraft.getInstance().getBlockRenderer().renderSingleBlock(IndustryContent.block(name).defaultBlockState(),pose,buffers,light,overlay);}
        private void part(String name,double x,double y,double z,PoseStack pose,MultiBufferSource buffers,int light,int overlay){pose.pushPose();pose.translate(x,y,z);model(name,pose,buffers,light,overlay);pose.popPose();}
        private void direction(net.minecraft.core.Direction side,PoseStack pose){
            pose.translate(.5,.5,.5);
            if(side==net.minecraft.core.Direction.UP)pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            else if(side==net.minecraft.core.Direction.DOWN)pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
            else pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-side.toYRot()));
            pose.translate(-.5,-.5,-.5);
        }
        @Override public boolean shouldRenderOffScreen(IndustryNode node){return node.role().equals("controller");}
        @Override public void render(IndustryNode node,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            var role=node.role();boolean active=node.getBlockState().getValue(IndustryBlock.ACTIVE);
            float turn=active?(node.getLevel().getGameTime()+partial)*12:0;
            if(role.equals("cable")||role.equals("fluid_pipe")){
                boolean fluid=role.equals("fluid_pipe");String prefix=fluid?"pipe":"wire";model(prefix+"_core_visual",pose,buffers,light,overlay);
                for(var side:net.minecraft.core.Direction.values())if(Connections.connected(node.getLevel(),node.getBlockPos(),side,fluid)){
                    pose.pushPose();direction(side,pose);model(prefix+"_arm_visual",pose,buffers,light,overlay);
                    var block=node.getLevel().getBlockState(node.getBlockPos().relative(side)).getBlock();
                    if(!(block instanceof IndustryBlock b)||!b.role.equals(role))model(prefix+"_plug_visual",pose,buffers,light,overlay);pose.popPose();
                }return;
            }
            if(role.equals("rotor")&&!node.getBlockState().getValue(IndustryBlock.ASSEMBLED)){
                pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn));pose.translate(-.5,-.5,-.5);model("rotor_blades",pose,buffers,light,overlay);pose.popPose();return;
            }
            if(!role.equals("controller")||!node.formed()||!node.getBlockState().getValue(IndustryBlock.ASSEMBLED))return;
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-node.facing().toYRot()));pose.translate(-.5,0,-.5);
            String kind=node.machineId().substring(node.machineId().indexOf(':')+1);
            switch(kind){
                case "generator"->part("generator_visual",0,0,1,pose,buffers,light,overlay);
                case "processor"->{
                    part("processor_lower_visual",0,0,1,pose,buffers,light,overlay);part("processor_upper_visual",0,2,1,pose,buffers,light,overlay);
                    int amount=node.inputAmount()+node.outputAmount();
                    if(amount>0){pose.pushPose();pose.translate(0,.02,1);pose.scale(1,(float)Math.min(1,amount/(double)node.fluidCapacity()),1);model(node.inputFluid().equals("minecraft:water")&&node.outputAmount()==0?"processor_water_visual":"processor_slurry_visual",pose,buffers,light,overlay);pose.popPose();}
                }
                case "centrifuge"->{part("centrifuge_front_visual",0,0,1,pose,buffers,light,overlay);part("centrifuge_back_visual",0,0,4,pose,buffers,light,overlay);}
                default->{pose.popPose();return;}
            }
            pose.pushPose();
            if(kind.equals("processor")){pose.translate(.5,0,1.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(turn));pose.translate(-.5,0,-.5);}
            else {pose.translate(.5,.5,.12);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn));pose.translate(-.5,-.5,-.5);}
            model(kind+"_rotor_visual",pose,buffers,light,overlay);pose.popPose();
            for(var cell:node.definition().cells().entrySet()){
                String hatch=switch(cell.getValue()){case "controller"->"controller";case "energy_port"->"energy";case "item_input","item_output","fluid_input","fluid_output"->cell.getValue();default->null;};
                if(hatch==null)continue;var c=cell.getKey();pose.pushPose();pose.translate(c.x(),c.y(),c.z());
                var side=c.x()<0?net.minecraft.core.Direction.WEST:c.x()>0?net.minecraft.core.Direction.EAST:c.z()==0?net.minecraft.core.Direction.NORTH:net.minecraft.core.Direction.SOUTH;
                direction(side,pose);model("hatch_"+hatch+"_visual",pose,buffers,light,overlay);
                if(hatch.equals("controller")&&active)model("energy_glow_visual",pose,buffers,15728880,overlay);pose.popPose();
            }
            pose.popPose();
        }
    }
    @Mod.EventBusSubscriber(modid=IndustryApi.MOD_ID,value=Dist.CLIENT)
    public static final class Preview {
        private static net.minecraft.core.BlockPos pinned;
        @SubscribeEvent public static void draw(RenderLevelStageEvent event){
            if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
            var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
            boolean wrench=mc.player.getMainHandItem().is(IndustryContent.item("wrench"));
            if(wrench&&mc.hitResult instanceof BlockHitResult hit&&mc.level.getBlockEntity(hit.getBlockPos()) instanceof IndustryNode target&&target.role().equals("controller"))pinned=hit.getBlockPos().immutable();
            if(pinned==null)return;
            if(mc.player.distanceToSqr(Vec3.atCenterOf(pinned))>256){pinned=null;return;}
            if(!(mc.level.getBlockEntity(pinned) instanceof IndustryNode node)||!node.role().equals("controller")||node.definition()==null||node.formed()&&!wrench)return;
            var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
            var consumer=mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            int layer=mc.player.isShiftKeyDown()?(int)(Math.floorMod(mc.level.getGameTime()/30,node.definition().height()))-1:Integer.MIN_VALUE;
            for(var c:node.definition().cells().entrySet()){
                if(layer!=Integer.MIN_VALUE&&c.getKey().y()!=layer)continue;
                var at=node.world(c.getKey());boolean ok=c.getValue().equals("air")?mc.level.getBlockState(at).isAir():mc.level.getBlockState(at).getBlock() instanceof IndustryBlock b&&b.role.equals(c.getValue());
                LevelRenderer.renderLineBox(pose,consumer,new AABB(at).deflate(.015),ok?.22F:1F,ok?.9F:.3F,ok?1F:.25F,.75F);
            }
            pose.popPose();mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
}
