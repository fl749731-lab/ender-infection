package dev.hui.enderinfection.worldgen;

import com.mojang.serialization.Codec;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A sealed research station: three rooms, culture tanks, benches and a live mother in the middle. */
public final class InfectedRuinFeature extends Feature<NoneFeatureConfiguration> {
    private static final int HALF_WIDTH=6;
    private static final int HALF_DEPTH=4;
    private static final int WALL_HEIGHT=5;
    public InfectedRuinFeature(Codec<NoneFeatureConfiguration> codec){super(codec);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        WorldGenLevel level=context.level();
        RandomSource random=context.random();
        BlockPos.MutableBlockPos cursor=context.origin().mutable();
        int minY=level.getMinBuildHeight();
        while(cursor.getY()>minY&&level.getBlockState(cursor).isAir())cursor.move(0,-1,0);
        BlockPos base=cursor.getY()<=minY+6?context.origin():cursor.immutable().above();
        BlockState wall=InfectedContent.TERRAIN.get("eroded_stone").get().defaultBlockState();
        BlockState crust=InfectedContent.TERRAIN.get("crystal_crust").get().defaultBlockState();
        BlockState tissue=InfectedContent.TERRAIN.get("living_tissue").get().defaultBlockState();
        BlockState mat=InfectedContent.TERRAIN.get("mycelial_mat").get().defaultBlockState();
        BlockState soil=InfectedContent.TERRAIN.get("necrotic_soil").get().defaultBlockState();
        BlockState glass=Blocks.TINTED_GLASS.defaultBlockState();
        BlockState amethyst=Blocks.AMETHYST_BLOCK.defaultBlockState();
        int width=HALF_WIDTH, depth=HALF_DEPTH;
        for(int dx=-width-2;dx<=width+2;dx++){
            for(int dz=-depth-2;dz<=depth+2;dz++){
                for(int dy=-1;dy<=WALL_HEIGHT+3;dy++){
                    level.setBlock(base.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
                }
            }
        }
        for(int dx=-width-1;dx<=width+1;dx++){
            for(int dz=-depth-1;dz<=depth+1;dz++){
                boolean edge=Math.abs(dx)==width+1||Math.abs(dz)==depth+1;
                level.setBlock(base.offset(dx,-1,dz),edge?crust:(Math.abs(dx)%2==Math.abs(dz)%2?mat:soil),2);
                if(Math.abs(dx)<=width&&Math.abs(dz)<=depth)level.setBlock(base.offset(dx,-2,dz),crust,2);
            }
        }
        for(int dx=-width;dx<=width;dx++){
            for(int dz=-depth;dz<=depth;dz++){
                boolean edge=Math.abs(dx)==width||Math.abs(dz)==depth;
                boolean corner=Math.abs(dx)==width&&Math.abs(dz)==depth;
                boolean pillar=corner||(Math.abs(dx)==width&&dz==0)||(Math.abs(dz)==depth&&dx==0);
                boolean door=dz==-depth&&Math.abs(dx)<=1;
                for(int dy=0;dy<WALL_HEIGHT;dy++){
                    BlockPos pos=base.offset(dx,dy,dz);
                    if(!edge)continue;
                    if(door&&dy<3){level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);continue;}
                    if(pillar){level.setBlock(pos,dy==WALL_HEIGHT-1?crust:tissue,2);continue;}
                    boolean window=(dy==2||dy==3)&&(Math.abs(dx)%3==1||Math.abs(dz)%3==1);
                    boolean band=dy==WALL_HEIGHT-1;
                    if(window)level.setBlock(pos,glass,2);
                    else if(band)level.setBlock(pos,crust,2);
                    else level.setBlock(pos,wall,2);
                }
                if(edge)level.setBlock(base.offset(dx,WALL_HEIGHT,dz),Math.abs(dx)==width&&Math.abs(dz)==depth?amethyst:(Math.abs(dx)+Math.abs(dz))%3==0?crust:tissue,2);
            }
        }
        for(int dx=-width;dx<=width;dx++){
            for(int dz=-depth;dz<=depth;dz++){
                boolean edge=Math.abs(dx)==width||Math.abs(dz)==depth;
                boolean rib=(Math.abs(dx)+Math.abs(dz))%4==0;
                level.setBlock(base.offset(dx,WALL_HEIGHT,dz),edge||rib?crust:tissue,2);
            }
        }
        for(int dx=-1;dx<=1;dx++){
            for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;
                level.setBlock(base.offset(dx,WALL_HEIGHT+1,dz),crust,2);
            }
        }
        level.setBlock(base.offset(0,WALL_HEIGHT+2,0),amethyst,2);
        for(int dx=-1;dx<=1;dx++){
            level.setBlock(base.offset(dx,0,-depth-1),crust,2);
            level.setBlock(base.offset(dx,0,-depth-2),Math.abs(dx)==0?amethyst:crust,2);
        }
        for(int side=-1;side<=1;side+=2){
            int px=side*3;
            for(int dz=-depth+1;dz<=depth-1;dz++){
                if(Math.abs(dz)<=1)continue;
                for(int dy=0;dy<WALL_HEIGHT;dy++){
                    level.setBlock(base.offset(px,dy,dz),dy==WALL_HEIGHT-1?crust:wall,2);
                }
            }
        }
        for(int side=-1;side<=1;side+=2){
            BlockPos bench=base.offset(side*4,0,-2);
            level.setBlock(bench,Blocks.BREWING_STAND.defaultBlockState(),2);
            level.setBlock(bench.offset(1,0,0),Blocks.CAULDRON.defaultBlockState(),2);
            level.setBlock(bench.offset(0,0,1),Blocks.BOOKSHELF.defaultBlockState(),2);
            level.setBlock(bench.offset(1,0,1),Blocks.CRAFTING_TABLE.defaultBlockState(),2);
            BlockPos tank=base.offset(side*5,0,1);
            for(int dx=-1;dx<=1;dx++){
                for(int dz=-1;dz<=1;dz++){
                    for(int dy=0;dy<3;dy++){
                        boolean shell=Math.abs(dx)==1||Math.abs(dz)==1||dy==2;
                        level.setBlock(tank.offset(dx,dy,dz),shell?(dy==2?crust:glass):(dy==0?mat:tissue),2);
                    }
                }
            }
            level.setBlock(tank.offset(0,1,0),InfectedContent.PLANTS.get("infected_fungus").get().defaultBlockState(),2);
            BlockPos chestPos=base.offset(side*4,0,3);
            level.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),2);
            if(level.getBlockEntity(chestPos) instanceof RandomizableContainerBlockEntity chest){
                chest.setLootTable(new ResourceLocation("ender_infection","chests/infected_lab"),random.nextLong());
            }
        }
        level.setBlock(base.offset(0,0,0),InfectedContent.NEST.get().defaultBlockState(),2);
        for(int dy=1;dy<=2;dy++){
            for(int dx=-1;dx<=1;dx++){
                for(int dz=-1;dz<=1;dz++){
                    if(dx==0&&dz==0)continue;
                    if(random.nextFloat()<.45F)level.setBlock(base.offset(dx,dy,dz),crust,2);
                }
            }
        }
        level.setBlock(base.offset(0,3,0),amethyst,2);
        level.setBlock(base.offset(0,4,0),crust,2);
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos lamp=base.offset(direction.getStepX()*(width-1),WALL_HEIGHT-1,direction.getStepZ()*(depth-1));
            level.setBlock(lamp,Blocks.SOUL_LANTERN.defaultBlockState(),2);
        }
        for(int i=0;i<8;i++){
            BlockPos spot=base.offset(random.nextInt(width*2+1)-width,0,random.nextInt(depth*2+1)-depth);
            if(!level.getBlockState(spot).isAir())continue;
            level.setBlock(spot,i%2==0?InfectedContent.PLANTS.get("infected_flower").get().defaultBlockState():InfectedContent.PLANTS.get("infected_grass").get().defaultBlockState(),2);
        }
        if(level.getBlockEntity(base.offset(0,-1,0)) instanceof ExpansionNodeEntity node)node.markWild();
        return true;
    }
}
