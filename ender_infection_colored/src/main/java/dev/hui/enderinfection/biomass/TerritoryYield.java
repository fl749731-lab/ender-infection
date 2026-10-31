package dev.hui.enderinfection.biomass;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.block.ExpansionNodeEntity;
import dev.hui.enderinfection.block.InfectedPlantBlock;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.InfectedKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import java.util.*;

/** Loaded surface columns, uniquely partitioned between the nearest active colony nodes. */
public final class TerritoryYield {
    private TerritoryYield(){}
    public record Surface(int columns,int weight,int nutrientCredits,int earnedCredits){}
    private record Node(UUID owner,BlockPos pos,int radius){}
    public static Surface measure(ServerLevel level,InfectedMob mother){
        return evaluate(level,mother,false);
    }
    public static Surface harvest(ServerLevel level,InfectedMob mother){return evaluate(level,mother,true);}
    private static Surface evaluate(ServerLevel level,InfectedMob mother,boolean harvest){
        var nodes=new ArrayList<Node>();
        for(InfectedMob core:level.getEntitiesOfClass(InfectedMob.class,mother.getBoundingBox().inflate(128),e->e.kind==InfectedKind.CORE&&e.isAlive()))
            for(ExpansionNodeEntity node:core.activeColonyNodes())nodes.add(new Node(core.getUUID(),node.getBlockPos(),node.radius()));
        var columns=new HashMap<Long,BlockPos>();
        for(Node node:nodes){
            if(!node.owner().equals(mother.getUUID()))continue;
            for(int dx=-node.radius();dx<=node.radius();dx++)for(int dz=-node.radius();dz<=node.radius();dz++){
                if(dx*dx+dz*dz>node.radius()*node.radius())continue;
                BlockPos column=node.pos().offset(dx,0,dz);if(!level.hasChunkAt(column))continue;
                for(int dy=3;dy>=-4;dy--){
                    BlockPos pos=column.offset(0,dy,0);String key=InfectedContent.terrainKey(level.getBlockState(pos));
                    if(!ground(key))continue;
                    var above=level.getBlockState(pos.above());
                    if(!above.isAir()&&!above.canBeReplaced()&&!(above.getBlock() instanceof InfectedPlantBlock)&&!above.is(BlockTags.LOGS))continue;
                    long id=((long)pos.getX()<<32)^(pos.getZ()&0xffffffffL);BlockPos previous=columns.get(id);
                    if(previous==null||pos.getY()>previous.getY())columns.put(id,pos.immutable());break;
                }
            }
        }
        int total=0,weight=0,nutrition=0,earned=0;LandNutrients soil=LandNutrients.get(level);
        for(BlockPos pos:columns.values()){
            Node closest=null;long best=Long.MAX_VALUE;
            for(Node candidate:nodes){
                int dy=pos.getY()-candidate.pos().getY();if(dy< -4||dy>3)continue;
                long dx=pos.getX()-candidate.pos().getX(),dz=pos.getZ()-candidate.pos().getZ(),distance=dx*dx+dz*dz;
                if(distance>candidate.radius()*candidate.radius())continue;
                if(distance<best||distance==best&&(closest==null||candidate.owner().compareTo(closest.owner())<0)){closest=candidate;best=distance;}
            }
            if(closest!=null&&closest.owner().equals(mother.getUUID())){
                int base=InfectedContent.terrainKey(level.getBlockState(pos)).startsWith("eroded_")?1:2;
                total++;weight+=base;int credits=base*soil.remaining(pos);nutrition+=credits;earned+=harvest?soil.harvest(pos,base,level.getGameTime()):credits;
            }
        }
        return new Surface(total,weight,nutrition,earned);
    }
    private static boolean ground(String key){return key!=null&&!key.contains("log")&&!key.contains("leaves");}
}
