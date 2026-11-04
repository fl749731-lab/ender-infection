package dev.hui.enderinfection.test;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

/** Exercise target acquisition through impact, without assigning the specialist a target. */
@GameTestHolder("ender_ai_checks")
@PrefixGameTestTemplate(false)
public final class DesignCombatGameTests {
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void foragerHunts(GameTestHelper h) { combat(h, InfectedKind.FORAGER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void scoutFires(GameTestHelper h) { combat(h, InfectedKind.SPOREWING_SCOUT); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void burrowerStrikes(GameTestHelper h) { combat(h, InfectedKind.BURROWER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_carrier")
    public static void carrierDefendsItself(GameTestHelper h) { combat(h, InfectedKind.CARRIER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_spider")
    public static void spiderMotherStrikes(GameTestHelper h) { combat(h, InfectedKind.SPIDER_MOTHER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void sentinelStrikes(GameTestHelper h) { combat(h, InfectedKind.SHIELD_SENTINEL); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void spitterFires(GameTestHelper h) { combat(h, InfectedKind.THROAT_SPITTER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void ambusherStrikes(GameTestHelper h) { combat(h, InfectedKind.LEAP_AMBUSHER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void turretFires(GameTestHelper h) { combat(h, InfectedKind.ROOT_TURRET); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void priestFires(GameTestHelper h) { combat(h, InfectedKind.RESONANCE_PRIEST); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void siegebreakerStrikes(GameTestHelper h) { combat(h, InfectedKind.SIEGEBREAKER); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void walkingNestStrikes(GameTestHelper h) { combat(h, InfectedKind.WALKING_NEST); }
    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void burrowKingCasts(GameTestHelper h) { combat(h, InfectedKind.BURROW_KING); }

    @GameTest(template="empty64", templateNamespace="ender_ai_checks", timeoutTicks=240, batch="design_combat")
    public static void hatchSacRespondsWithBrood(GameTestHelper h) {
        floor(h);
        var core = unit(h, InfectedKind.CORE, 6, 20);
        var tag = new CompoundTag();
        core.addAdditionalSaveData(tag);
        tag.putInt("ColonyBiomass", 36);
        tag.putBoolean("ExpansionNodeCreated", true);
        tag.putLong("LastBrood", h.getLevel().getGameTime());
        core.readAdditionalSaveData(tag);
        var hatch = unit(h, InfectedKind.HATCH_SAC, 20, 20);
        hatch.getPersistentData().putUUID("ColonyMother", core.getUUID());
        var prey = prey(h, 26, 20);
        boolean[] cast = {false};
        h.succeedWhen(() -> {
            cast[0] |= hatch.attacking();
            var children = h.getLevel().getEntitiesOfClass(InfectedMob.class,
                hatch.getBoundingBox().inflate(8), e -> e.kind == InfectedKind.FORAGER);
            h.assertTrue(cast[0] && !children.isEmpty(), "Hatch did not respond with brood: " + state(hatch, prey)
                + " biomass=" + core.colonyBiomass() + " population=" + core.colonyPopulation(h.getLevel()));
            children.forEach(InfectedMob::discard);
            hatch.discard(); core.discard(); prey.discard();
        });
    }

    private static void combat(GameTestHelper h, InfectedKind kind) {
        floor(h);
        var actor = unit(h, kind, 12, 16);
        int targetX = kind == InfectedKind.CARRIER ? 15 : 22;
        Mob[] target = {prey(h, targetX, 16)};
        if (kind == InfectedKind.CARRIER) {
            // This worker should retaliate; it intentionally does not hunt passive animals.
            h.runAfterDelay(40, () -> actor.hurt(actor.damageSources().mobAttack(target[0]), 1));
        }
        boolean[] acquired = {false}, cast = {false};
        int[] phase = {0};
        h.succeedWhen(() -> {
            var prey = target[0];
            acquired[0] |= actor.getTarget() == prey;
            cast[0] |= actor.attacking();
            h.assertTrue(acquired[0] && cast[0] && prey.getHealth() < prey.getMaxHealth(),
                "No autonomous hit in " + (phase[0] == 0 ? "animal" : "hostile") + " phase: "
                    + state(actor, prey) + " acquired=" + acquired[0] + " cast=" + cast[0]);
            if (phase[0] == 0) {
                prey.discard();
                // A husk is a vanilla hostile mob that cannot lose health to sunlight.
                target[0] = prey(h, EntityType.HUSK, targetX, 16);
                phase[0] = 1;
                acquired[0] = cast[0] = false;
                if (kind == InfectedKind.CARRIER)
                    h.runAfterDelay(20, () -> actor.hurt(actor.damageSources().mobAttack(target[0]), 1));
                h.assertTrue(false, "Waiting for autonomous retargeting to the hostile mob");
            }
            actor.discard(); prey.discard();
        });
    }

    private static String state(InfectedMob actor, Mob prey) {
        return actor.kind + " target=" + (actor.getTarget() == prey) + " distance=" + actor.distanceTo(prey)
            + " attacking=" + actor.attacking() + " visible=" + actor.hasLineOfSight(prey)
            + " emerging=" + actor.emerging() + " actorPos=" + actor.position() + " preyPos=" + prey.position()
            + " path=" + actor.getNavigation().getPath() + " pathDone=" + actor.getNavigation().isDone()
            + " task=" + actor.getPersistentData().getString("ColonyTask")
            + " reason=" + actor.getPersistentData().getString("TaskReason") + " preyHealth=" + prey.getHealth();
    }

    private static InfectedMob unit(GameTestHelper h, InfectedKind kind, int x, int z) {
        return place(h, InfectedContent.TYPES.get(kind).get(), x, z);
    }

    private static Mob prey(GameTestHelper h, int x, int z) {
        return prey(h, EntityType.COW, x, z);
    }

    private static <T extends Mob> T prey(GameTestHelper h, EntityType<T> type, int x, int z) {
        var prey = place(h, type, x, z);
        prey.setNoAi(true);
        Objects.requireNonNull(prey.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(1000);
        prey.setHealth(1000);
        return prey;
    }

    private static <T extends Mob> T place(GameTestHelper h, EntityType<T> type, int x, int z) {
        T entity = Objects.requireNonNull(type.create(h.getLevel()));
        var pos = h.absolutePos(new BlockPos(x, 1, z));
        entity.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 36; x++) for (int z = 0; z < 36; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 10; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }
}
