package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Only the controller owns a machine's inventory. Other blocks proxy an identity-checked, loaded owner. */
public final class IndustryNode extends PlatformNodeEntity implements WorldlyContainer,MenuProvider {
    public static final int INVENTORY_SIZE=12, DATA_SIZE=38;
    public static final int[] INPUT_SLOTS={0,4,5,6,7,8}, OUTPUT_SLOTS={2,3,10,11}, CATALYST_SLOTS={1,9};
    private NonNullList<ItemStack> items=NonNullList.withSize(INVENTORY_SIZE,ItemStack.EMPTY);
    private UUID identity=UUID.randomUUID(),ownerIdentity;
    private BlockPos owner;
    private String machineId=IndustryApi.GENERATOR,activeRecipe="",recipeFingerprint="";
    private int fluidChannel;
    private boolean formed;
    private int energy,progress,duration,burn,status;
    private long checkedTick=Long.MIN_VALUE;
    private boolean checkedValid;
    private final Tank input=new Tank(),output=new Tank();
    private final Tank[] inputs={input,new Tank(),new Tank(),new Tank()},outputs={output,new Tank(),new Tank(),new Tank()};
    public record Check(boolean valid,boolean unloaded,BlockPos position,String expected){}
    private static final int[] NONE={},INPUT={0,4,5,6,7,8,1,9},OUTPUT={2,3,10,11};
    private final class Tank {
        String fluid="";int amount;
        int fill(String id,int count,int capacity,boolean simulate){
            if(count<=0||(!fluid.isEmpty()&&!fluid.equals(id))||!Platform.validFluid(id))return 0;
            int accepted=Math.max(0,Math.min(count,capacity-amount));
            if(!simulate&&accepted>0){fluid=id;amount+=accepted;changed();}return accepted;
        }
        int drain(int count,boolean simulate){int removed=Math.max(0,Math.min(count,amount));if(!simulate&&removed>0){amount-=removed;if(amount==0)fluid="";changed();}return removed;}
    }
    public IndustryNode(BlockPos p,BlockState s){super(IndustryContent.NODE,p,s);}
    public String role(){return getBlockState().getBlock() instanceof IndustryBlock block?block.role:"";}
    public String machineId(){return machineId;}
    public boolean formed(){return formed;}
    public int energy(){return energy;}
    public int progress(){return progress;}
    public int status(){return status;}
    public int burn(){return burn;}
    public int inputAmount(){return input.amount;}
    public int outputAmount(){return output.amount;}
    public String inputFluid(){return input.fluid;}
    private int consumption(){var r=IndustryApi.processes().get(activeRecipe);return status==5&&r!=null?r.energyPerTick():0;}
    public int fluidChannel(){return fluidChannel;}
    public boolean setFluidChannel(int channel){
        if(channel<0||channel>=4||!Set.of("fluid_input","fluid_output").contains(role()))return false;
        fluidChannel=channel;Platform.invalidate(this);sync();return true;
    }
    private int fluidNumber(String id){return id.isEmpty()?0:net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(IndustryContent.id(id)));}
    public MachineDefinition definition(){return IndustryApi.machines().get(machineId);}
    public Direction facing(){return getBlockState().getValue(IndustryBlock.FACING);}
    public int energyCapacity(){return role().equals("battery")?200000:role().equals("cable")?4000:100000;}
    public int fluidCapacity(){return role().equals("tank")?16000:role().equals("fluid_pipe")?1000:8000;}
    public BlockPos world(MachineDefinition.Cell cell){
        var right=facing().getClockWise();var back=facing().getOpposite();
        return worldPosition.offset(right.getStepX()*cell.x()+back.getStepX()*cell.z(),cell.y(),right.getStepZ()*cell.x()+back.getStepZ()*cell.z());
    }
    public static boolean loaded(Level level,BlockPos pos){return level instanceof ServerLevel s?s.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null:level.hasChunkAt(pos);}
    public static IndustryNode at(Level level,BlockPos pos){return loaded(level,pos)&&level.getBlockEntity(pos) instanceof IndustryNode n&&!n.isRemoved()?n:null;}
    private boolean claimedBy(IndustryNode node){return owner!=null&&owner.equals(node.worldPosition)&&node.identity.equals(ownerIdentity);}
    private boolean lockedByOther(){
        if(owner==null||level==null)return false;if(!loaded(level,owner))return true;
        var existing=at(level,owner);return existing!=null&&existing.identity.equals(ownerIdentity)&&existing.formed;
    }
    public Check checkStructure(boolean ownership){
        var d=definition();if(level==null||d==null)return new Check(false,false,worldPosition,"blueprint");
        for(var cell:d.cells().entrySet()){
            var pos=world(cell.getKey());if(!loaded(level,pos))return new Check(false,true,pos,cell.getValue());
            String role=cell.getValue();var state=level.getBlockState(pos);
            if(role.equals("air")){if(!state.isAir())return new Check(false,false,pos,"air");continue;}
            var node=at(level,pos);
            if(node==null||!node.role().equals(role))return new Check(false,false,pos,role);
            if(ownership&&!node.claimedBy(this))return new Check(false,false,pos,"claim");
            if(!ownership&&!node.claimedBy(this)&&node.lockedByOther())return new Check(false,false,pos,"claim");
        }
        return new Check(true,false,worldPosition,"");
    }
    public Check form(){
        if(!role().equals("controller"))return new Check(false,false,worldPosition,"controller");
        var check=checkStructure(false);if(!check.valid())return check;
        for(var cell:definition().cells().entrySet())if(!cell.getValue().equals("air")){
            var node=at(level,world(cell.getKey()));node.owner=worldPosition;node.ownerIdentity=identity;node.assembled(Set.of(IndustryApi.GENERATOR,IndustryApi.PROCESSOR,IndustryApi.CENTRIFUGE).contains(machineId));node.setChanged();Platform.invalidate(node);
        }
        formed=true;checkedTick=Long.MIN_VALUE;status=1;sync();return check;
    }
    public boolean configure(String id){
        if(!role().equals("controller")||formed||!isEmpty()||Arrays.stream(inputs).anyMatch(t->t.amount!=0)||Arrays.stream(outputs).anyMatch(t->t.amount!=0)||progress!=0||burn!=0||!IndustryApi.machines().containsKey(id))return false;
        machineId=id;activeRecipe="";sync();return true;
    }
    public boolean operational(){
        if(!formed||isRemoved()||level==null||level.isClientSide)return false;
        long now=level.getGameTime();if(checkedTick==now)return checkedValid;
        checkedTick=now;var c=checkStructure(true);checkedValid=c.valid();
        if(!c.valid()){status=c.unloaded()?6:0;if(!c.unloaded())unform();}
        return checkedValid;
    }
    private void unform(){
        formed=false;status=0;checkedValid=false;checkedTick=Long.MIN_VALUE;
        if(definition()!=null&&level!=null)for(var cell:definition().cells().entrySet())if(!cell.getValue().equals("air")){
            var node=at(level,world(cell.getKey()));if(node!=null&&node.claimedBy(this)){node.owner=null;node.ownerIdentity=null;node.setVisual(false,0);node.assembled(false);node.setChanged();Platform.invalidate(node);}
        }
        sync();
    }
    public IndustryNode owner(){
        if(role().equals("controller"))return this;
        if(owner==null||ownerIdentity==null||level==null||!loaded(level,owner))return null;
        var node=at(level,owner);return node!=null&&node.identity.equals(ownerIdentity)&&node.role().equals("controller")&&node.operational()?node:null;
    }
    public void broken(){
        var controller=owner();if(controller!=null){controller.checkedTick=Long.MIN_VALUE;controller.unform();}
        if(role().equals("controller")){if(formed)unform();}
        if(role().equals("controller")||role().equals("battery")||role().equals("tank")||role().equals("pump")){
            for(var stack:items)if(!stack.isEmpty())Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,stack);
            items=NonNullList.withSize(INVENTORY_SIZE,ItemStack.EMPTY);
        }
    }
    public InteractionResult interact(ServerPlayer player){
        if(LabVesselItem.vessel(player.getMainHandItem())){var h=LabPlatform.handler(level,worldPosition,null);LabFluids.report(player,LabFluids.interact(player.getMainHandItem(),h,player.isShiftKeyDown(),level));return InteractionResult.CONSUME;}
        if(player.isShiftKeyDown()&&player.getMainHandItem().isEmpty()&&setFluidChannel((fluidChannel+1)%4)){
            tell(player,"fluid_channel",fluidChannel+1);return InteractionResult.CONSUME;
        }
        if(player.getMainHandItem().getItem() instanceof BlueprintItem blueprint){
            boolean ok=configure(blueprint.machine);tell(player,ok?"configured":"configure_busy");return ok?InteractionResult.CONSUME:InteractionResult.FAIL;
        }
        if(player.getMainHandItem().is(IndustryContent.item("wrench"))){
            var node=role().equals("controller")?this:owner();if(node==null){tell(player,"unbound");return InteractionResult.FAIL;}
            if(player.isShiftKeyDown()&&node.formed){node.unform();tell(player,"unformed");return InteractionResult.CONSUME;}
            var result=node.form();if(result.valid())tell(player,"formed",Component.translatable(node.definition().nameKey()));
            else tell(player,result.unloaded()?"unloaded":"missing",Component.translatable("block.resonant_industry."+result.expected()),result.position().getX(),result.position().getY(),result.position().getZ());
            return result.valid()?InteractionResult.CONSUME:InteractionResult.FAIL;
        }
        if(bucket(player))return InteractionResult.CONSUME;
        var node=role().equals("controller")?this:owner();
        if(node!=null){player.openMenu(node);return InteractionResult.CONSUME;}
        tell(player,Set.of("battery","cable","pump").contains(role())?"energy_status":"unbound",energy,energyCapacity(),input.amount,fluidCapacity());return InteractionResult.CONSUME;
    }
    private static void tell(ServerPlayer p,String key,Object... args){p.displayClientMessage(Component.translatable("message.resonant_industry."+key,args),false);}
    private boolean bucket(ServerPlayer player){
        var held=player.getMainHandItem();String fluid=held.is(Items.WATER_BUCKET)?"minecraft:water":held.is(IndustryContent.item("slurry_canister"))?IndustryApi.SLURRY:"";
        FluidAccess access=null;
        if(role().equals("controller"))access=fluidView(fluid.isEmpty()?(output.amount>=1000?output:input):input,acceptsFluid(0,fluid),true,this);
        else access=fluidAccess(null);
        if(access==null)return false;
        if(!fluid.isEmpty()){
            if(access.fill(fluid,1000,true)!=1000){tell(player,"fluid_rejected");return true;}
            access.fill(fluid,1000,false);if(!player.isCreative())player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));return true;
        }
        if(held.is(Items.BUCKET)){
            if(access.amount()<1000||(!access.fluid().equals("minecraft:water")&&!access.fluid().equals(IndustryApi.SLURRY))){tell(player,"fluid_rejected");return true;}
            var full=new ItemStack(access.fluid().equals(IndustryApi.SLURRY)?IndustryContent.item("slurry_canister"):Items.WATER_BUCKET);
            // Reserve the destination before removing any fluid, including a stack of empty buckets.
            boolean single=held.getCount()==1,room=single||player.isCreative()||player.getInventory().getFreeSlot()>=0;
            if(!room){tell(player,"inventory_full");return true;}
            if(access.drain(1000,true)!=1000){tell(player,"fluid_rejected");return true;}
            access.drain(1000,false);if(!player.isCreative()){held.shrink(1);if(single)player.setItemInHand(InteractionHand.MAIN_HAND,full);else player.getInventory().add(full);}return true;
        }
        return false;
    }
    public EnergyAccess energyAccess(Direction side){
        String role=role();boolean standalone=Set.of("cable","battery","pump").contains(role);
        if(!standalone&&!role.equals("energy_port"))return null;
        return new EnergyAccess(){
            private IndustryNode target(){return isRemoved()?null:standalone?IndustryNode.this:owner();}
            private boolean receiveAllowed(IndustryNode n){return role.equals("cable")||role.equals("pump")||role.equals("battery")&&side!=facing()||role.equals("energy_port")&&!n.definition().generator();}
            private boolean extractAllowed(IndustryNode n){return role.equals("cable")||role.equals("battery")&&(side==null||side==facing())||role.equals("energy_port")&&n.definition().generator();}
            public int stored(){var n=target();return n==null?0:n.energy;}
            public int capacity(){var n=target();return n==null?0:n.energyCapacity();}
            public int receive(int amount,boolean simulate){var n=target();if(n==null||!receiveAllowed(n)||amount<=0)return 0;int accepted=Math.max(0,Math.min(Math.min(amount,512),n.energyCapacity()-n.energy));if(!simulate&&accepted>0){n.energy+=accepted;n.changed();}return accepted;}
            public int extract(int amount,boolean simulate){var n=target();if(n==null||!extractAllowed(n)||amount<=0)return 0;int sent=Math.min(Math.min(amount,512),n.energy);if(!simulate&&sent>0){n.energy-=sent;n.changed();}return sent;}
        };
    }
    private FluidAccess fluidView(Tank tank,boolean fill,boolean drain,IndustryNode root){
        return new FluidAccess(){
            public String fluid(){return tank.fluid;}public int amount(){return tank.amount;}public int capacity(){return root.fluidCapacity();}
            public int fill(String id,int amount,boolean simulate){return fill&&!root.isRemoved()?tank.fill(id,amount,capacity(),simulate):0;}
            public int drain(int amount,boolean simulate){return drain&&!root.isRemoved()?tank.drain(amount,simulate):0;}
        };
    }
    public FluidAccess fluidAccess(Direction side){
        String role=role();if(Set.of("tank","fluid_pipe","pump").contains(role))return fluidView(input,!role.equals("pump"),true,this);
        if(!role.equals("fluid_input")&&!role.equals("fluid_output"))return null;
        final int channel=fluidChannel;
        return new FluidAccess(){
            private IndustryNode target(){return isRemoved()||fluidChannel!=channel?null:owner();}
            public String fluid(){var n=target();return n==null?"":(role.equals("fluid_input")?n.inputs[channel]:n.outputs[channel]).fluid;}
            public int amount(){var n=target();return n==null?0:(role.equals("fluid_input")?n.inputs[channel]:n.outputs[channel]).amount;}
            public int capacity(){return 8000;}
            public int fill(String id,int amount,boolean simulate){var n=target();if(n==null||!role.equals("fluid_input")||!n.acceptsFluid(channel,id))return 0;return n.inputs[channel].fill(id,amount,capacity(),simulate);}
            public int drain(int amount,boolean simulate){var n=target();return n==null||!role.equals("fluid_output")?0:n.outputs[channel].drain(amount,simulate);}
        };
    }
    private void sendEnergy(Direction side){
        var source=energyAccess(side);if(source==null)return;var dest=Platform.energy(level,worldPosition.relative(side),side.getOpposite());if(dest==null)return;
        int want=256;var neighbor=at(level,worldPosition.relative(side));if(role().equals("cable")&&neighbor!=null&&neighbor.role().equals("cable"))want=Math.max(0,(source.stored()-dest.stored()+1)/2);
        int reserve=dest.receive(source.extract(want,true),true);if(reserve<=0)return;
        int removed=source.extract(reserve,false),accepted=dest.receive(removed,false);
        if(accepted<removed){var root=role().equals("energy_port")?owner():this;if(root!=null){root.energy+=removed-Math.max(0,accepted);root.changed();}}
    }
    private void sendFluid(Direction side){
        var source=fluidAccess(side);if(source==null||source.amount()==0)return;var dest=Platform.fluid(level,worldPosition.relative(side),side.getOpposite());if(dest==null)return;
        int want=250;var neighbor=at(level,worldPosition.relative(side));
        if(role().equals("fluid_pipe")&&neighbor!=null&&neighbor.role().equals("fluid_pipe"))want=Math.max(0,(source.amount()-dest.amount()+1)/2);
        int reserve=dest.fill(source.fluid(),Math.min(want,source.amount()),true);if(reserve<=0)return;
        String id=source.fluid();int removed=source.drain(reserve,false),accepted=dest.fill(id,removed,false);
            if(accepted<removed){var root=role().equals("fluid_output")?owner():this;if(root!=null)(role().equals("fluid_output")?root.outputs[fluidChannel]:root.input).fill(id,removed-Math.max(0,accepted),root.fluidCapacity(),false);}
    }
    public static void tick(Level l,BlockPos p,BlockState s,IndustryNode n){if(l instanceof ServerLevel&&!n.isRemoved())n.work();}
    private void work(){
        if(role().equals("fluid_pipe")){FluidNetwork.tick(this);return;}
        if(role().equals("controller")){
            if(operational()){
                if(definition().generator())generate();else process();
                if(level.getGameTime()%5==0)for(var cell:definition().cells().entrySet())if(Set.of("energy_port","fluid_output").contains(cell.getValue())){
                    var port=at(level,world(cell.getKey()));for(var side:Direction.values())if(cell.getValue().equals("energy_port"))port.sendEnergy(side);else port.sendFluid(side);
                }
            }
            if(level.getGameTime()%10==0)refreshVisuals();return;
        }
        if(role().equals("pump")&&level.getGameTime()%20==0&&energy>=100&&input.amount<=fluidCapacity()-1000){
            var below=worldPosition.below();if(loaded(level,below)&&level.getBlockState(below).is(Blocks.WATER)&&level.getFluidState(below).isSource()){
                int sources=0;for(var d:Direction.Plane.HORIZONTAL)if(loaded(level,below.relative(d))&&level.getFluidState(below.relative(d)).isSource()&&level.getBlockState(below.relative(d)).is(Blocks.WATER))sources++;
                if(input.fill("minecraft:water",1000,fluidCapacity(),true)==1000){if(sources<2)level.setBlock(below,Blocks.AIR.defaultBlockState(),3);input.fill("minecraft:water",1000,fluidCapacity(),false);energy-=100;changed();}
            }
        }
        for(int i=0;i<6;i++){
            var d=Direction.values()[(i+(int)Math.floorMod(level.getGameTime()+worldPosition.asLong(),6))%6];
            if(role().equals("cable")||role().equals("battery")&&d==facing())sendEnergy(d);
            if(role().equals("fluid_pipe")||(role().equals("tank")||role().equals("pump"))&&d==facing())sendFluid(d);
        }
        if(level.getGameTime()%20==0)setVisual(energy>0||input.amount>0,input.amount*4/fluidCapacity());
    }
    private void generate(){
        status=1;if(energy>energyCapacity()-64){status=8;return;}
        if(burn==0){int value=IndustryApi.fuelTicks(IndustryContent.itemId(items.get(0)));if(value==0)return;items.get(0).shrink(1);burn=value;}
        burn--;energy+=64;status=5;changed();
    }
    private static boolean itemMatches(String selector,ItemStack stack){
        if(stack.isEmpty())return false;
        return selector.startsWith("#")?stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,IndustryContent.id(selector.substring(1)))):IndustryContent.itemId(stack).equals(selector);
    }
    private static boolean fluidMatches(String selector,String fluid){
        if(fluid.isEmpty())return false;
        if (!selector.startsWith("#")) return selector.equals(fluid);
        var registry = net.minecraft.core.registries.Registries.FLUID;
        var key = net.minecraft.resources.ResourceKey.create(registry, IndustryContent.id(fluid));
        var tag = net.minecraft.tags.TagKey.create(registry, IndustryContent.id(selector.substring(1)));
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.getHolder(key).map(holder -> holder.is(tag)).orElse(false);
    }
    private boolean acceptsFluid(int channel,String fluid){return IndustryApi.processes().values().stream().filter(r->r.machine().equals(machineId)).flatMap(r->r.fluids().stream()).anyMatch(p->p.tank()==channel&&fluidMatches(p.ingredient(),fluid));}
    private boolean matchesItems(ProcessRecipe r){return r.inputs().stream().allMatch(p->itemMatches(p.ingredient(),items.get(INPUT_SLOTS[p.slot()]))&&items.get(INPUT_SLOTS[p.slot()]).getCount()>=p.amount())
            &&r.catalysts().stream().allMatch(p->itemMatches(p.ingredient(),items.get(CATALYST_SLOTS[p.slot()]))&&items.get(CATALYST_SLOTS[p.slot()]).getCount()>=p.amount());}
    private boolean matches(ProcessRecipe r){return matchesItems(r)&&r.fluids().stream().allMatch(p->fluidMatches(p.ingredient(),inputs[p.tank()].fluid)&&inputs[p.tank()].amount>=p.amount());}
    private boolean stageable(ProcessRecipe r){return matchesItems(r)&&r.fluids().stream().allMatch(p->inputs[p.tank()].amount==0||fluidMatches(p.ingredient(),inputs[p.tank()].fluid));}
    private boolean fits(int slot,String id,int count){if(count==0)return true;var stack=items.get(slot);var item=IndustryContent.item(id);return item!=Items.AIR&&(stack.isEmpty()?count<=item.getDefaultInstance().getMaxStackSize():stack.is(item)&&!stack.hasTag()&&stack.getCount()+count<=stack.getMaxStackSize());}
    private void put(int slot,String id,int count){if(count==0)return;if(items.get(slot).isEmpty())items.set(slot,new ItemStack(IndustryContent.item(id),count));else items.get(slot).grow(count);}
    private void process(){
        var recipe=IndustryApi.processes().get(activeRecipe);
        if(recipe!=null&&!recipe.machine().equals(machineId)){progress=0;activeRecipe="";recipeFingerprint="";recipe=null;changed();}
        if(recipe!=null&&progress>0&&(!recipeFingerprint.isEmpty()&&!recipeFingerprint.equals(IndustryApi.processFingerprint(activeRecipe))||recipeFingerprint.isEmpty()&&!IndustryApi.recipes().containsKey(activeRecipe))){progress=0;activeRecipe="";recipe=null;changed();}
        if(progress==0||recipe==null){
            recipe=IndustryApi.processes().values().stream().filter(r->r.machine().equals(machineId)&&stageable(r))
                .sorted(Comparator.comparingInt(ProcessRecipe::priority).reversed().thenComparing(ProcessRecipe::id)).findFirst().orElse(null);
            if(recipe==null){progress=0;activeRecipe="";recipeFingerprint="";status=items.get(0).isEmpty()?1:input.amount==0?3:7;return;}
            if(!activeRecipe.equals(recipe.id()))progress=0;activeRecipe=recipe.id();
        }
        recipeFingerprint=IndustryApi.processFingerprint(recipe.id());duration=recipe.ticks();
        if(!matches(recipe)){status=matchesItems(recipe)?3:7;return;}
        if(recipe.outputs().stream().anyMatch(p->!fits(OUTPUT_SLOTS[p.slot()],p.ingredient(),p.amount()))
            ||recipe.outputFluids().stream().anyMatch(p->outputs[p.tank()].fill(p.ingredient(),p.amount(),fluidCapacity(),true)!=p.amount())){status=4;return;}
        if(energy<recipe.energyPerTick()){status=2;return;}
        energy-=recipe.energyPerTick();status=5;progress++;changed();
        if(progress>=duration){
            for(var p:recipe.inputs())items.get(INPUT_SLOTS[p.slot()]).shrink(p.amount());
            for(var p:recipe.fluids())inputs[p.tank()].drain(p.amount(),false);
            for(var p:recipe.outputs())put(OUTPUT_SLOTS[p.slot()],p.ingredient(),p.amount());
            for(var p:recipe.outputFluids())outputs[p.tank()].fill(p.ingredient(),p.amount(),fluidCapacity(),false);
            progress=0;activeRecipe="";recipeFingerprint="";changed();
        }
    }
    private void setVisual(boolean active,int fill){var state=getBlockState();int safe=Math.max(0,Math.min(4,fill));if(state.getValue(IndustryBlock.ACTIVE)!=active||state.getValue(IndustryBlock.FILL)!=safe)level.setBlock(worldPosition,state.setValue(IndustryBlock.ACTIVE,active).setValue(IndustryBlock.FILL,safe),2);}
    private void assembled(boolean yes){var s=getBlockState();if(s.getValue(IndustryBlock.ASSEMBLED)!=yes)level.setBlock(worldPosition,s.setValue(IndustryBlock.ASSEMBLED,yes),2);}
    private void refreshVisuals(){
        setVisual(formed&&status==5,0);if(!formed||definition()==null)return;
        int fill=(int)Math.ceil((input.amount+output.amount)*4.0/(fluidCapacity()*2));
        for(var c:definition().cells().entrySet())if(!c.getValue().equals("air")){var n=at(level,world(c.getKey()));if(n!=null){n.assembled(Set.of(IndustryApi.GENERATOR,IndustryApi.PROCESSOR,IndustryApi.CENTRIFUGE).contains(machineId));if(c.getValue().equals("window")||c.getValue().equals("rotor"))n.setVisual(status==5,fill);}}
        sync();
    }
    private void changed(){setChanged();}
    private void sync(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    private IndustryNode inventory(){return role().equals("controller")?this:owner();}
    public boolean accepts(ItemStack stack){return accepts(0,stack);}
    private boolean accepts(int slot,ItemStack stack){
        if(definition()==null)return false;
        if(definition().generator())return slot==0&&IndustryApi.fuelTicks(IndustryContent.itemId(stack))>0;
        return IndustryApi.processes().values().stream().filter(r->r.machine().equals(machineId)).anyMatch(r->
            r.inputs().stream().anyMatch(p->INPUT_SLOTS[p.slot()]==slot&&itemMatches(p.ingredient(),stack))
            ||r.catalysts().stream().anyMatch(p->CATALYST_SLOTS[p.slot()]==slot&&itemMatches(p.ingredient(),stack)));
    }
    @Override public int getContainerSize(){return INVENTORY_SIZE;}
    @Override public boolean isEmpty(){var n=role().equals("controller")?this:inventory();return n==null||n.items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int i){var n=inventory();return n==null||i<0||i>=INVENTORY_SIZE?ItemStack.EMPTY:n.items.get(i);}
    @Override public ItemStack removeItem(int i,int count){var n=inventory();if(n==null||i<0||i>=INVENTORY_SIZE)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(n.items,i,count);if(!result.isEmpty())n.changed();return result;}
    @Override public ItemStack removeItemNoUpdate(int i){var n=inventory();return n==null||i<0||i>=INVENTORY_SIZE?ItemStack.EMPTY:ContainerHelper.takeItem(n.items,i);}
    @Override public void setItem(int i,ItemStack stack){var n=inventory();if(n==null||i<0||i>=INVENTORY_SIZE)return;n.items.set(i,stack);if(stack.getCount()>stack.getMaxStackSize())stack.setCount(stack.getMaxStackSize());n.changed();}
    @Override public void clearContent(){var n=inventory();if(n!=null){n.items.clear();n.changed();}}
    @Override public boolean stillValid(Player p){return level!=null&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(Vec3.atCenterOf(worldPosition))<=64;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){var n=inventory();return n!=null&&n.accepts(slot,stack);}
    @Override public int[] getSlotsForFace(Direction side){return role().equals("item_input")?INPUT:role().equals("item_output")?OUTPUT:NONE;}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return role().equals("item_input")&&canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return role().equals("item_output")&&Arrays.stream(OUTPUT_SLOTS).anyMatch(s->s==slot)&&inventory()!=null;}
    @Override public Component getDisplayName(){return Component.translatable(definition()==null?"block.resonant_industry.controller":definition().nameKey());}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new IndustryMenu(id,inv,this,data);}
    public final ContainerData data=new ContainerData(){
        public int getCount(){return DATA_SIZE;}
        public int get(int i){if(i>=22&&i<DATA_SIZE){int channel=(i-22)/4;return switch((i-22)%4){case 0->fluidNumber(inputs[channel].fluid);case 1->inputs[channel].amount;case 2->fluidNumber(outputs[channel].fluid);default->outputs[channel].amount;};}return switch(i){case 0->energy&65535;case 1->energy>>>16;case 2->energyCapacity()&65535;case 3->energyCapacity()>>>16;case 4->progress&65535;case 5->duration&65535;case 6->input.amount;case 7->output.amount;case 8->status;case 9->formed?1:0;case 10->burn&65535;case 11->fluidNumber(input.fluid);case 12->progress>>>16;case 13->duration>>>16;case 14->burn>>>16;case 15->definition()!=null&&definition().generator()&&status==5?64:0;case 16->consumption()&65535;case 17->consumption()>>>16;case 18->fluidNumber(output.fluid);case 19,20->fluidCapacity();case 21->definition()!=null&&definition().generator()?1:0;default->0;};}
        public void set(int i,int v){}
    };
    private CompoundTag state(CompoundTag t){
        t.putUUID("Identity",identity);t.putString("Machine",machineId);t.putBoolean("Formed",formed);t.putInt("Energy",energy);t.putInt("Progress",progress);t.putInt("Duration",duration);t.putInt("Burn",burn);t.putString("Recipe",activeRecipe);
        if(owner!=null&&ownerIdentity!=null){t.putLong("Owner",owner.asLong());t.putUUID("OwnerIdentity",ownerIdentity);}
        t.putString("InputFluid",input.fluid);t.putInt("InputAmount",input.amount);t.putString("OutputFluid",output.fluid);t.putInt("OutputAmount",output.amount);
        t.putInt("ProcessVersion",2);t.putInt("FluidChannel",fluidChannel);t.putString("ProcessSignature",recipeFingerprint);
        var tanks=new ListTag();for(int i=1;i<4;i++){var tank=new CompoundTag();tank.putInt("Channel",i);tank.putString("In",inputs[i].fluid);tank.putInt("InAmount",inputs[i].amount);tank.putString("Out",outputs[i].fluid);tank.putInt("OutAmount",outputs[i].amount);tanks.add(tank);}t.put("ProcessTanks",tanks);return t;
    }
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);state(t);ContainerHelper.saveAllItems(t,items);}
    @Override public void load(CompoundTag t){
        super.load(t);identity=t.hasUUID("Identity")?t.getUUID("Identity"):UUID.randomUUID();machineId=t.getString("Machine");if(machineId.isEmpty())machineId=IndustryApi.GENERATOR;
        formed=t.getBoolean("Formed");energy=Math.max(0,Math.min(energyCapacity(),t.getInt("Energy")));progress=Math.max(0,Math.min(72000,t.getInt("Progress")));duration=Math.max(0,Math.min(72000,t.getInt("Duration")));burn=Math.max(0,Math.min(72000,t.getInt("Burn")));activeRecipe=t.getString("Recipe");
        owner=t.contains("Owner",4)?BlockPos.of(t.getLong("Owner")):null;ownerIdentity=t.hasUUID("OwnerIdentity")?t.getUUID("OwnerIdentity"):null;
        input.fluid=t.getString("InputFluid");input.amount=Platform.validFluid(input.fluid)?Math.max(0,Math.min(fluidCapacity(),t.getInt("InputAmount"))):0;
        output.fluid=t.getString("OutputFluid");output.amount=Platform.validFluid(output.fluid)?Math.max(0,Math.min(fluidCapacity(),t.getInt("OutputAmount"))):0;
        if(input.amount==0)input.fluid="";if(output.amount==0)output.fluid="";
        fluidChannel=Math.max(0,Math.min(3,t.getInt("FluidChannel")));recipeFingerprint=t.getString("ProcessSignature");
        for(int i=1;i<4;i++){inputs[i].fluid="";inputs[i].amount=0;outputs[i].fluid="";outputs[i].amount=0;}
        for(var entry:t.getList("ProcessTanks",10)){var tank=(CompoundTag)entry;int i=tank.getInt("Channel");if(i<1||i>=4)continue;readTank(inputs[i],tank.getString("In"),tank.getInt("InAmount"));readTank(outputs[i],tank.getString("Out"),tank.getInt("OutAmount"));}
        items=NonNullList.withSize(INVENTORY_SIZE,ItemStack.EMPTY);ContainerHelper.loadAllItems(t,items);checkedTick=Long.MIN_VALUE;
    }
    private void readTank(Tank tank,String id,int amount){tank.amount=Platform.validFluid(id)?Math.max(0,Math.min(fluidCapacity(),amount)):0;tank.fluid=tank.amount>0?id:"";}
    @Override public CompoundTag getUpdateTag(){return state(new CompoundTag());}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
