package dev.hui.resonantindustry;

import dev.hui.resonantindustry.api.FluidAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Bounded loaded-only routing; process each network once per five ticks, including its final 1 mB. */
final class FluidNetwork {
    private record Endpoint(BlockPos pos,Direction side,boolean process){}
    private static final class Visit {long tick=Long.MIN_VALUE;final Set<Long> visited=new HashSet<>();}
    private static final Map<Level,Visit> VISITS=new WeakHashMap<>();
    static void tick(IndustryNode start){
        var level=start.getLevel();long now=level.getGameTime();if(now%5!=0)return;
        var visit=VISITS.computeIfAbsent(level,l->new Visit());if(visit.tick!=now){visit.tick=now;visit.visited.clear();}
        if(visit.visited.contains(start.getBlockPos().asLong()))return;
        var queue=new ArrayDeque<IndustryNode>();var seen=new HashSet<Long>();var pipes=new ArrayList<IndustryNode>();var ends=new LinkedHashMap<String,Endpoint>();
        queue.add(start);seen.add(start.getBlockPos().asLong());
        while(!queue.isEmpty()&&pipes.size()<128){
            var pipe=queue.removeFirst();pipes.add(pipe);visit.visited.add(pipe.getBlockPos().asLong());
            for(var direction:Direction.values()){
                var pos=pipe.getBlockPos().relative(direction);if(!IndustryNode.loaded(level,pos))continue;var next=IndustryNode.at(level,pos);
                if(next!=null&&next.role().equals("fluid_pipe")){if(seen.add(pos.asLong())&&seen.size()<=128)queue.add(next);}
                else if(Platform.fluid(level,pos,direction.getOpposite())!=null)ends.put(pos.asLong()+"/"+direction,new Endpoint(pos,direction.getOpposite(),next!=null&&next.role().equals("fluid_input")));
            }
        }
        var endpoints=new ArrayList<>(ends.values());endpoints.sort(Comparator.comparing(Endpoint::process).reversed());
        int budget=250,offset=(int)Math.floorMod(now/5,pipes.size());
        for(int i=0;i<pipes.size()&&budget>0;i++){
            FluidAccess source=pipes.get((i+offset)%pipes.size()).fluidAccess(null);if(source.amount()==0)continue;
            for(var end:endpoints){
                var target=Platform.fluid(level,end.pos,end.side);if(target==null)continue;String id=source.fluid();
                int amount=target.fill(id,Math.min(budget,source.amount()),true);if(amount<=0)continue;
                int removed=source.drain(amount,false),accepted=Math.max(0,Math.min(removed,target.fill(id,removed,false)));
                if(accepted<removed)source.fill(id,removed-accepted,false);budget-=accepted;if(budget==0||source.amount()==0)break;
            }
        }
    }
    private FluidNetwork(){}
}
