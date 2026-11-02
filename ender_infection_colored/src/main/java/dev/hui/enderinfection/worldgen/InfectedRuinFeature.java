package dev.hui.enderinfection.worldgen;

import com.mojang.serialization.Codec;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** An abandoned laboratory: three sealed rooms, culture tanks and a live mother in the middle. */
public final class InfectedRuinFeature extends Feature<NoneFeatureConfiguration> {
    private static final int HALF_WIDTH=5;
    private static final int HALF_DEPTH=4;
    private static final int HEIGHT=4;
    public InfectedRuinFeature(Codec<NoneFeatureConfiguration> codec){super(codec);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        WorldGenLevel level=context.level();
        RandomSource random=context.random();
        BlockPos.MutableBlockPos cursor=context.origin().mutable();
        int minY=level.getMinBuildHeight();
        while(cursor.getY()>minY&&!level.getBlockState(cursor).isSolidRender(level,cursor))cursor.move(0,-1,0);
        if(cursor.getY()<=minY+4)return false;
        BlockPos base=cursor.immutable().above();
        BlockState wall=InfectedContent.TERRAIN.get("eroded_stone").get().defaultBlockState();
        BlockState crust=InfectedContent.TERRAIN.get("crystal_crust").get().defaultBlockState();
        BlockState tissue=InfectedContent.TERRAIN.get("living_tissue").get().defaultBlockState();
        BlockState floor=InfectedContent.TERRAIN.get("mycelial_mat").get().defaultBlockState();
        BlockState soil=InfectedContent.TERRAIN.get("necrotic_soil").get().defaultBlockState();
        for(int dx=-HALF_WIDTH-1;dx<=HALF_WIDTH+1;dx++){
            for(int dz=-HALF_DEPTH-1;dz<=HALF_DEPTH+1;dz++){
                boolean insideShell=Math.abs(dx)<=HALF_WIDTH&&Math.abs(dz)<=HALF_DEPTH;
                for(int dy=-1;dy<=HEIGHT+1;dy++){
                    BlockPos pos=base.offset(dx,dy,dz);
                    if(dy==-1)level.setBlock(pos,insideShell?(random.nextFloat()<.2F?soil:floor):crust,2);
                    else if(insideShell&&dy==HEIGHT)level.setBlock(pos,Math.abs(dx)==HALF_WIDTH||Math.abs(dz)==HALF_DEPTH?crust:tissue,2);
                    else level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                }
            }
        }
        for(int dy=0;dy<HEIGHT;dy++){
            for(int dx=-HALF_WIDTH;dx<=HALF_WIDTH;dx++){
                for(int dz=-HALF_DEPTH;dz<=HALF_DEPTH;dz++){
                    boolean edge=Math.abs(dx)==HALF_WIDTH||Math.abs(dz)==HALF_DEPTH;
                    if(!edge)continue;
                    BlockPos pos=base.offset(dx,dy,dz);
                    boolean door=dz==-HALF_DEPTH&&dy<2&&Math.abs(dx)<=1;
                    boolean window=dy==2&&((Math.abs(dx)%3)==0||(Math.abs(dz)%3)==0);
                    if(door)level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                    else if(window)level.setBlock(pos,crust,2);
                    else level.setBlock(pos,random.nextFloat()<.15F?crust:wall,2);
                }
            }
        }
        for(int side=-1;side<=1;side+=2){
            for(int dy=0;dy<HEIGHT;dy++){
                for(int dz=-HALF_DEPTH;dz<=HALF_DEPTH;dz++){
                    if(Math.abs(dz)<=1)continue;
                    level.setBlock(base.offset(side*2,dy,dz),dy==1&&Math.abs(dz)%2==0?crust:wall,2);
                }
            }
        }
        for(int side=-1;side<=1;side+=2){
            BlockPos tank=base.offset(side*4,0,2);
            for(int dx=-1;dx<=1;dx++){
                for(int dz=-1;dz<=1;dz++){
                    for(int dy=0;dy<3;dy++){
                        boolean shell=Math.abs(dx)==1||Math.abs(dz)==1||dy==2;
                        level.setBlock(tank.offset(dx,dy,dz),shell?crust:tissue,2);
                    }
                }
            }
            level.setBlock(tank.offset(0,1,0),InfectedContent.PLANTS.get("infected_fungus").get().defaultBlockState(),2);
        }
        BlockPos chestPos=base.offset(4,0,-2);
        level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),2);
        if(level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity chest){
            chest.setLootTable(new ResourceLocation("ender_infection","chests/infected_lab"),random.nextLong());
        }
        for(int i=0;i<6;i++){
            BlockPos spot=base.offset(random.nextInt(HALF_WIDTH*2+1)-HALF_WIDTH,0,random.nextInt(HALF_DEPTH*2+1)-HALF_DEPTH);
            if(!level.getBlockState(spot).isAir())continue;
            level.setBlock(spot,i%2==0?InfectedContent.PLANTS.get("infected_flower").get().defaultBlockState():InfectedContent.PLANTS.get("infected_grass").get().defaultBlockState(),2);
        }
        for(int side=-1;side<=1;side+=2){
            BlockPos foot=base.offset(side*3,0,-3);
            int height=2+random.nextInt(3);
            for(int dy=1;dy<=height;dy++)level.setBlock(foot.above(dy),dy%2==0?crust:tissue,2);
        }
        BlockPos nest=base.offset(0,-1,0);
        level.setBlock(nest,InfectedContent.NEST.get().defaultBlockState(),2);
        if(level.getBlockEntity(nest) instanceof ExpansionNodeEntity node)node.markWild();
        return true;
    }
}
