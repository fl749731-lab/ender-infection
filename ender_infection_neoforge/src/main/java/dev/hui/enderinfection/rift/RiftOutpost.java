package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.*;
import dev.hui.enderinfection.entity.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in, bounded encounter fixture. Natural world generation is a later milestone. */
@EventBusSubscriber(modid=EnderInfection.MOD_ID)
public final class RiftOutpost {
    private RiftOutpost() {}
    public static boolean clearSite(ServerLevel level,BlockPos center){
        for(var p:BlockPos.betweenClosed(center.offset(-9,-1,-9),center.offset(9,7,9)))
            if(!level.hasChunkAt(p)||level.getBlockEntity(p)!=null||!level.getBlockState(p).getFluidState().isEmpty()
                ||p.getY()>=center.getY()&&!level.getBlockState(p).isAir()&&!level.getBlockState(p).canBeReplaced())return false;
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)if(!level.getBlockState(center.offset(x,-1,z)).isFaceSturdy(level,center.offset(x,-1,z),Direction.UP))return false;
        return true;
    }
    public static InfectedMob spawn(ServerLevel level,BlockPos p,InfectedKind kind){
        var mob=InfectedContent.TYPES.get(kind).get().create(level);if(mob==null)throw new IllegalStateException("Missing rift entity");
        mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,180,0);mob.setPersistenceRequired();level.addFreshEntity(mob);return mob;
    }
    public static BlockPos place(ServerLevel level,BlockPos center){
        if(!clearSite(level,center))throw new IllegalArgumentException("Rift outpost needs a loaded, dry, empty 19x19 site with solid floor");
        for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)level.setBlock(center.offset(x,-1,z),(Math.abs(x)==9||Math.abs(z)==9?Blocks.POLISHED_DEEPSLATE:Blocks.END_STONE_BRICKS).defaultBlockState(),3);
        for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
            var p=center.offset(sx*7,0,sz*7);int h=sx==sz?5:3;
            for(int y=0;y<h;y++)level.setBlock(p.above(y),Blocks.DEEPSLATE_BRICKS.defaultBlockState(),3);
            level.setBlock(p.above(h),Blocks.AMETHYST_BLOCK.defaultBlockState(),3);
            for(int i=1;i<=3;i++)level.setBlock(center.offset(sx*(4+i),0,sz*7),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),3);
        }
        for(int s:new int[]{-1,1})for(int z=-2;z<=2;z++)level.setBlock(center.offset(s*5,0,z),Blocks.END_STONE_BRICKS.defaultBlockState(),3);
        var anchorPos=center;level.setBlock(anchorPos,RiftContent.ANCHOR.get().defaultBlockState(),3);
        var core=spawn(level,center.offset(0,0,6),InfectedKind.CORE);
        var tag=new CompoundTag();core.addAdditionalSaveData(tag);tag.putInt("MotherStage",2);tag.putInt("ColonyBiomass",64);tag.putBoolean("ExpansionNodeCreated",true);core.readAdditionalSaveData(tag);
        var anchor=(RiftAnchorEntity)level.getBlockEntity(anchorPos);anchor.assignMother(core);anchor.addEnergy(24);
        for(var entry:new Object[][]{{InfectedKind.ANCHOR_GUARD,new int[]{0,-4}},{InfectedKind.EARLY_ENDERMAN,new int[]{-4,2}},{InfectedKind.ENDERMAN,new int[]{4,2}}}){
            var offset=(int[])entry[1];var mob=spawn(level,center.offset(offset[0],0,offset[1]),(InfectedKind)entry[0]);
            mob.getPersistentData().putUUID("ColonyMother",core.getUUID());mob.getPersistentData().putString("ColonyDuty","hunter");RiftCombat.bind(mob,anchorPos);
        }
        var chest=center.offset(0,0,-8);level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);
        if(level.getBlockEntity(chest) instanceof ChestBlockEntity box){box.setItem(0,new ItemStack(RiftContent.SUPPRESSOR.get()));box.setItem(1,new ItemStack(InfectedContent.VOID_CRYSTAL.get(),3));box.setItem(2,new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE,2));}
        return anchorPos;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("enderrift").requires(s->s.hasPermission(2))
            .then(Commands.literal("outpost").executes(c->{
                var source=c.getSource();var level=source.getLevel();var column=BlockPos.containing(source.getPosition()).offset(32,0,0);
                if(!level.hasChunkAt(column)){source.sendFailure(Component.translatable("message.ender_infection.rift_site_invalid"));return 0;}
                var center=new BlockPos(column.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ()),column.getZ());
                if(!clearSite(level,center)){source.sendFailure(Component.translatable("message.ender_infection.rift_site_invalid"));return 0;}
                place(level,center);source.sendSuccess(()->Component.translatable("message.ender_infection.rift_outpost_created",center.getX(),center.getY(),center.getZ()),false);return 1;
            }))
            .then(Commands.literal("status").executes(c->{
                var source=c.getSource();var found=RiftAnchorEntity.nearby(source.getLevel(),BlockPos.containing(source.getPosition()),32);
                if(found.isEmpty()){source.sendFailure(Component.translatable("message.ender_infection.no_rift_target"));return 0;}
                var a=found.get(0);source.sendSuccess(()->Component.translatable("message.ender_infection.anchor_status",a.energy(),RiftAnchorEntity.CAPACITY,a.statusName()),false);return 1;
            })));
    }
}
