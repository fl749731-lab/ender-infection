package dev.hui.enderinfection.rift;

import dev.hui.enderinfection.EnderInfection;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

public final class RiftContent {
    private RiftContent() {}
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,EnderInfection.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,EnderInfection.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,EnderInfection.MOD_ID);
    public static final RegistryObject<Block> ANCHOR=BLOCKS.register("rift_anchor",()->new RiftAnchorBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s->s.getValue(RiftAnchorBlock.SUPPRESSED)?3:s.getValue(RiftAnchorBlock.ACTIVE)?11:0)));
    public static final RegistryObject<Item> ANCHOR_ITEM=ITEMS.register("rift_anchor",()->new BlockItem(ANCHOR.get(),new Item.Properties()));
    public static final RegistryObject<Item> SUPPRESSOR=ITEMS.register("phase_suppressor",()->new RiftSuppressorItem(new Item.Properties().durability(128)));
    public static final RegistryObject<BlockEntityType<RiftAnchorEntity>> ANCHOR_ENTITY=ENTITIES.register("rift_anchor",()->BlockEntityType.Builder.of(RiftAnchorEntity::new,ANCHOR.get()).build(null));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
}
