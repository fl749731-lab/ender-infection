package dev.hui.enderinfection.test;

import com.mojang.authlib.GameProfile;
import dev.hui.enderinfection.EnderInfection;
import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.entity.WorldIntroduction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

@GameTestHolder("ender_ore_checks")
@PrefixGameTestTemplate(false)
public final class CrystalProgressionGameTests {
    @GameTest(template = "empty64", templateNamespace = "ender_ore_checks", timeoutTicks = 100, batch = "crystal_gear")
    public static void crystalEquipmentUsesItsOwnRecipesAndPerks(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String material : new String[]{"resonant_crystal", "ember_crystal", "void_crystal"}) {
            Item crystal = item(material.equals("resonant_crystal") ? "resonant_shard" : material);
            for (String slot : new String[]{"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"}) {
                String id = material + "_" + slot;
                Item equipment = item(id);
                helper.assertTrue(equipment != null && equipment.getMaxDamage(new ItemStack(equipment)) > 0
                    && equipment.isValidRepairItem(new ItemStack(equipment), new ItemStack(crystal))
                    && level.getRecipeManager().byKey(key(id)).isPresent(), "Missing recipe or crystal repair for " + id);
            }
            helper.assertTrue(level.getServer().getAdvancements().getAdvancement(key(material + "_found")) != null
                && level.getServer().getAdvancements().getAdvancement(key(material + "_armor_set")) != null,
                "Crystal progress branch did not load for " + material);
        }
        FakePlayer player = player(helper);
        var zombie = EntityType.ZOMBIE.create(level);
        var infected = InfectedContent.TYPES.get(InfectedKind.EARLY_COW).get().create(level);

        equip(player, "resonant_crystal");
        assertDamage(helper, new LivingHurtEvent(player, level.damageSources().mobAttack(infected), 10), 8);
        assertDamage(helper, new LivingHurtEvent(player, level.damageSources().mobAttack(zombie), 10), 10);
        equip(player, "ember_crystal");
        assertDamage(helper, new LivingHurtEvent(player, level.damageSources().inFire(), 10), 6);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        assertDamage(helper, new LivingHurtEvent(player, level.damageSources().inFire(), 10), 10);
        equip(player, "void_crystal");
        assertDamage(helper, new LivingHurtEvent(player, level.damageSources().fall(), 10), 5);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("resonant_crystal_pickaxe")));
        helper.assertTrue(Math.abs(speed(helper, player, Blocks.IRON_ORE) - 8.775F) < .01F
            && Math.abs(speed(helper, player, Blocks.STONE) - 6.5F) < .01F, "Resonant ore boost did not distinguish rock from ore");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("void_crystal_shovel")));
        player.setOnGround(false);
        helper.assertTrue(speed(helper, player, Blocks.DIRT) == 24F, "Void shovel did not receive its airborne boost");
        player.setOnGround(true);
        helper.assertTrue(speed(helper, player, Blocks.DIRT) == 8F, "Void boost persisted on the ground");

        var sword = new ItemStack(item("resonant_crystal_sword"));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        assertDamage(helper, new LivingHurtEvent(infected, level.damageSources().playerAttack(player), 8), 10);
        sword.getItem().hurtEnemy(sword, infected, player);
        helper.assertTrue(infected.hasEffect(MobEffects.GLOWING), "Resonant blade did not mark infected target");
        var ember = new ItemStack(item("ember_crystal_sword"));
        ember.getItem().hurtEnemy(ember, zombie, player);
        helper.assertTrue(zombie.isOnFire(), "Ember blade did not ignite its victim");

        var voidSword = new ItemStack(item("void_crystal_sword"));
        player.setItemInHand(InteractionHand.MAIN_HAND, voidSword);
        var result = voidSword.getItem().use(level, player, InteractionHand.MAIN_HAND);
        var pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, player.getBoundingBox().inflate(4));
        helper.assertTrue(result.getResult().consumesAction() && pearls.size() == 1 && voidSword.getDamageValue() == 2,
            "Void sword did not create a pearl and charge its durability");
        helper.assertTrue(player.getCooldowns().isOnCooldown(voidSword.getItem())
            && !voidSword.getItem().use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
            "Void sword allowed a second throw during cooldown");
        pearls.forEach(ThrownEnderpearl::discard);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty64", templateNamespace = "ender_ore_checks", timeoutTicks = 100, batch = "infection_blink")
    public static void infectionDodgesProjectilesWhileKeepingMeleeVulnerability(GameTestHelper helper) {
        var level = helper.getLevel();
        FakePlayer attacker = player(helper);
        Arrow arrow = new Arrow(level, attacker);
        for (InfectedKind kind : InfectedKind.values()) {
            Mob mob = InfectedContent.TYPES.get(kind).get().create(level);
            float health = mob.getHealth();
            helper.assertTrue(!mob.hurt(level.damageSources().arrow(arrow, attacker), 8) && mob.getHealth() == health,
                kind + " took projectile damage");
        }
        Mob mutant = EnderInfection.MUTANT_ZOMBIE.get().create(level);
        helper.assertTrue(!mutant.hurt(level.damageSources().arrow(arrow, attacker), 8), "Mutant zombie took projectile damage");

        for (int x = 2; x <= 24; x++) for (int z = 2; z <= 24; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        var cow = InfectedContent.TYPES.get(InfectedKind.EARLY_COW).get().create(level);
        BlockPos start = helper.absolutePos(new BlockPos(13, 1, 13));
        cow.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5, 0, 0);
        var saved = new CompoundTag();
        cow.addAdditionalSaveData(saved);
        saved.putInt("EmergenceTicks", 0);
        cow.readAdditionalSaveData(saved);
        level.addFreshEntity(cow);
        var from = cow.position();
        helper.assertTrue(!cow.hurt(level.damageSources().arrow(arrow, attacker), 10)
            && cow.position().distanceToSqr(from) >= 4, "Early cow did not blink away from an arrow on an open floor");
        var landed = cow.position();
        helper.assertTrue(!cow.hurt(level.damageSources().arrow(arrow, attacker), 10) && cow.position().equals(landed),
            "Projectile immunity or blink cooldown failed");
        float health = cow.getHealth();
        cow.hurt(level.damageSources().playerAttack(attacker), 8);
        helper.assertTrue(cow.getHealth() < health, "Blink also blocked melee damage");
        cow.discard();
        attacker.discard();
        helper.succeed();
    }

    @GameTest(template = "empty64", templateNamespace = "ender_ore_checks", timeoutTicks = 100, batch = "opening_meteor")
    public static void openingMeteorFallsResumesAndSpawnsExactlyOneMother(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = WorldIntroduction.Data.forWorldAge(0);
        var player = UUID.randomUUID();
        helper.assertTrue(data.schedule(player, 0) && !data.ready(99) && data.ready(100), "New-world warning timer changed");
        BlockPos origin = new BlockPos(13, 7, 13);
        BlockPos site = helper.absolutePos(origin);
        for (BlockPos pos : BlockPos.betweenClosed(site.offset(-7, -7, -7), site.offset(7, -1, 7))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos container = site.east(3);
        level.setBlockAndUpdate(container, Blocks.CHEST.defaultBlockState());
        var witness = EntityType.COW.create(level);
        witness.setNoAi(true);
        witness.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
        witness.setHealth(100);
        witness.moveTo(site.getX() + 2.5, site.getY(), site.getZ() + .5, 0, 0);
        level.addFreshEntity(witness);
        float witnessHealth = witness.getHealth();
        data.beginFlight(site);
        helper.assertTrue(WorldIntroduction.advanceMeteor(level, data) == null, "Mother appeared before impact");
        var state = data.save(new CompoundTag());
        var id = state.getUUID("Display");
        var display = level.getEntity(id);
        helper.assertTrue(display instanceof Display.BlockDisplay && display.getY() > site.getY() + 20, "Meteor did not appear in the sky");
        var appearance = display.saveWithoutId(new CompoundTag());
        helper.assertTrue(appearance.getCompound("block_state").getString("Name").equals("minecraft:crying_obsidian"),
            "Falling display has no meteor block model");
        double height = display.getY();
        for (int tick = 0; tick < 15; tick++) WorldIntroduction.advanceMeteor(level, data);
        data = WorldIntroduction.Data.load(data.save(new CompoundTag()));
        helper.assertTrue(WorldIntroduction.advanceMeteor(level, data) == null && level.getEntity(id) == display
            && display.getY() < height, "Reload reset the falling meteor or duplicated its display");

        InfectedMob mother = null;
        for (int tick = 0; tick < 60 && mother == null; tick++) mother = WorldIntroduction.advanceMeteor(level, data);
        long excavated = BlockPos.betweenClosedStream(site.offset(-3, -1, -3), site.offset(3, -1, 3))
            .filter(pos -> level.getBlockState(pos).isAir()).count();
        helper.assertTrue(excavated >= 12 && level.getBlockState(site.below()).isAir(),
            "Meteor blast did not excavate a visible crater: " + excavated + " surface blocks");
        helper.assertTrue(mother != null && mother.kind == InfectedKind.CORE && mother.getY() < site.getY()
            && display.isRemoved(), "Mother did not emerge inside the crater. Impact: " + site + ", mother: "
                + (mother == null ? "missing" : mother.blockPosition()) + ", excavated surface blocks: " + excavated);
        helper.assertTrue(witness.getHealth() < witnessHealth && witness.getDeltaMovement().horizontalDistanceSqr() > .01,
            "Meteor blast did not damage and knock back a nearby creature");
        helper.assertTrue(level.getBlockState(container).is(Blocks.CHEST), "Meteor blast destroyed a container");
        witness.discard();
        data = WorldIntroduction.Data.load(data.save(new CompoundTag()));
        var nearby = level.getEntitiesOfClass(InfectedMob.class, new AABB(site).inflate(4), m -> m.kind == InfectedKind.CORE);
        helper.assertTrue(!data.ready(200) && WorldIntroduction.advanceMeteor(level, data) == null && nearby.size() == 1,
            "An opening meteor replayed after impact");
        helper.assertTrue(level.getServer().getAdvancements().getAdvancement(key("meteor_impact")) != null
            && level.getServer().getAdvancements().getAdvancement(key("kill_mother")) != null, "Meteor or mother progress is missing");
        mother.discard();
        helper.succeed();
    }

    private static void equip(FakePlayer player, String prefix) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item(prefix + "_helmet")));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item(prefix + "_chestplate")));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(item(prefix + "_leggings")));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(item(prefix + "_boots")));
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CrystalCheck"));
        player.setGameMode(GameType.SURVIVAL);
        var pos = helper.absolutePos(new BlockPos(7, 1, 7));
        player.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        return player;
    }

    private static float speed(GameTestHelper helper, FakePlayer player, net.minecraft.world.level.block.Block block) {
        var state = block.defaultBlockState();
        var event = new PlayerEvent.BreakSpeed(player, state, player.getMainHandItem().getDestroySpeed(state),
            helper.absolutePos(new BlockPos(7, 0, 7)));
        MinecraftForge.EVENT_BUS.post(event);
        return event.getNewSpeed();
    }

    private static void assertDamage(GameTestHelper helper, LivingHurtEvent event, float expected) {
        MinecraftForge.EVENT_BUS.post(event);
        helper.assertTrue(Math.abs(event.getAmount() - expected) < .01F, "Wrong damage for crystal perk: " + event.getAmount());
    }

    private static Item item(String path) {
        return ForgeRegistries.ITEMS.getValue(key(path));
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation(EnderInfection.MOD_ID, path);
    }
}
