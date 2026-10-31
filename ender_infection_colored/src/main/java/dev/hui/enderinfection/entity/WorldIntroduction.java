package dev.hui.enderinfection.entity;

import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid=EnderInfection.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldIntroduction {
    private WorldIntroduction(){}
    public static final class Data extends SavedData {
        private boolean eligible,completed;
        private UUID player;
        private long due;
        public static Data forWorldAge(long age){Data data=new Data();data.eligible=age<200;data.setDirty();return data;}
        public static Data load(CompoundTag tag){Data data=new Data();data.eligible=tag.getBoolean("Eligible");data.completed=tag.getBoolean("Completed");data.due=tag.getLong("Due");if(tag.hasUUID("Player"))data.player=tag.getUUID("Player");return data;}
        public boolean schedule(UUID id,long now){if(!eligible||completed||player!=null)return false;player=id;due=now+100;setDirty();return true;}
        public boolean ready(long now){return eligible&&!completed&&player!=null&&now>=due;}
        public void complete(){completed=true;setDirty();}
        @Override public CompoundTag save(CompoundTag tag){tag.putBoolean("Eligible",eligible);tag.putBoolean("Completed",completed);tag.putLong("Due",due);if(player!=null)tag.putUUID("Player",player);return tag;}
    }
    private static Data data(ServerLevel world){return world.getDataStorage().computeIfAbsent(Data::load,()->Data.forWorldAge(world.getGameTime()),"ender_infection_world_introduction");}
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        ServerLevel world=player.serverLevel().getServer().overworld();data(world).schedule(player.getUUID(),world.getGameTime());
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        ServerLevel world=server.overworld();Data data=data(world);
        if(!data.ready(world.getGameTime())||world.getDifficulty()==Difficulty.PEACEFUL)return;
        ServerPlayer player=server.getPlayerList().getPlayer(data.player);
        if(player==null||player.serverLevel()!=world)return;
        // Retry sparse/unsafe terrain at a bounded rate, without forcing chunks to load.
        if(world.getGameTime()!=data.due&&world.getGameTime()%40!=0)return;
        InfectedMob mother=spawnNear(world,player.blockPosition());if(mother==null)return;
        data.complete();
        BlockPos p=mother.blockPosition();
        Component message=Component.translatable("message.ender_infection.mother_arrived",p.getX(),p.getY(),p.getZ());
        for(ServerPlayer recipient:server.getPlayerList().getPlayers())if(recipient.serverLevel()==world)recipient.sendSystemMessage(message);
    }
    public static InfectedMob spawnNear(ServerLevel world,BlockPos center){
        for(int attempt=0;attempt<32;attempt++){
            double angle=world.random.nextDouble()*Math.PI*2;int radius=24+world.random.nextInt(9);
            BlockPos column=center.offset((int)Math.round(Math.cos(angle)*radius),0,(int)Math.round(Math.sin(angle)*radius));
            if(!world.hasChunkAt(column))continue;
            BlockPos p=world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column);
            if(!world.getBlockState(p.below()).isFaceSturdy(world,p.below(),Direction.UP)||!world.getBlockState(p.below()).getFluidState().isEmpty())continue;
            InfectedMob mother=InfectedContent.TYPES.get(InfectedKind.CORE).get().create(world);if(mother==null)return null;
            mother.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,world.random.nextFloat()*360,0);
            if(!world.getWorldBorder().isWithinBounds(mother.getBoundingBox())||!world.noCollision(mother,mother.getBoundingBox())||world.containsAnyLiquid(mother.getBoundingBox()))continue;
            mother.setPersistenceRequired();
            if(world.addFreshEntity(mother))return mother;
        }
        return null;
    }
}
