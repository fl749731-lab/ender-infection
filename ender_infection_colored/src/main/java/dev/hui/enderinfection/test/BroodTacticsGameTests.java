package dev.hui.enderinfection.test;

import dev.hui.enderinfection.InfectedContent;
import dev.hui.enderinfection.entity.InfectedKind;
import dev.hui.enderinfection.entity.InfectedMob;
import dev.hui.enderinfection.item.InfectionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder("ender_ai_checks")
@PrefixGameTestTemplate(false)
public final class BroodTacticsGameTests {
    @GameTest(template = "empty64", templateNamespace = "ender_ai_checks", timeoutTicks = 190, batch = "brood_scout")
    public static void scoutFliesAndMarksPrey(GameTestHelper h) {
        floor(h);
        var scout = infected(h, InfectedKind.SPOREWING_SCOUT, 6, 4, 8);
        var prey = place(h, EntityType.COW, 14, 1, 8, true);
        var start = scout.position();
        h.succeedWhen(() -> {
            h.assertTrue(scout.position().distanceToSqr(start) > 2 && scout.getY() > prey.getY() + 2,
                         "Scout did not reposition in flight");
            h.assertTrue(prey.hasEffect(MobEffects.GLOWING), "Scout did not mark the prey");
            scout.discard();
            prey.discard();
        });
    }

    @GameTest(template = "empty64", templateNamespace = "ender_ai_checks", timeoutTicks = 220, batch = "brood_cargo")
    public static void carrierDeliversEvenOneTissueToItsOwnMother(GameTestHelper h) {
        floor(h);
        var mother = infected(h, InfectedKind.CORE, 4, 1, 8);
        mother.setNoAi(true);
        int initialIncome = mother.colonyIncomeTotal();
        var carrier = infected(h, InfectedKind.CARRIER, 8, 1, 8);
        carrier.getPersistentData().putUUID("ColonyMother", mother.getUUID());
        var p = h.absolutePos(new BlockPos(11, 1, 8));
        var drop = new ItemEntity(h.getLevel(), p.getX() + .5, p.getY(), p.getZ() + .5,
                                  new ItemStack(InfectionItems.TISSUE.get()));
        drop.setDeltaMovement(0, 0, 0);
        h.getLevel().addFreshEntity(drop);
        h.succeedWhen(() -> {
            h.assertTrue(!drop.isAlive() && carrier.getPersistentData().getInt("CarriedBiomass") == 0
                         && mother.colonyIncomeTotal() == initialIncome + 4,
                         "Carrier did not deliver the small load, balance=" + mother.colonyBiomass()
                         + " cargo=" + carrier.getPersistentData().getInt("CarriedBiomass") + " at " + carrier.position()
                         + " mother=" + mother.position() + " task=" + carrier.getPersistentData().getString("ColonyTask")
                         + " path=" + carrier.getNavigation().getPath());
            mother.discard();
            carrier.discard();
        });
    }

    @GameTest(template = "empty64", templateNamespace = "ender_ai_checks", timeoutTicks = 160, batch = "brood_ambush")
    public static void ambusherTravelsBeforeItHits(GameTestHelper h) {
        floor(h);
        var ambusher = infected(h, InfectedKind.LEAP_AMBUSHER, 6, 1, 8);
        var prey = place(h, EntityType.COW, 12, 1, 8, true);
        var origin = ambusher.position();
        h.succeedWhen(() -> {
            h.assertTrue(ambusher.position().distanceToSqr(origin) > 9, "Ambusher did not cross the gap");
            h.assertTrue(prey.getHealth() < prey.getMaxHealth(), "Ambusher did not land its strike");
            ambusher.discard();
            prey.discard();
        });
    }

    @GameTest(template = "empty64", templateNamespace = "ender_ai_checks", timeoutTicks = 190, batch = "brood_spitter")
    public static void spitterRetreatsFromClosePrey(GameTestHelper h) {
        floor(h);
        var spitter = infected(h, InfectedKind.THROAT_SPITTER, 10, 1, 8);
        var prey = place(h, EntityType.COW, 13, 1, 8, true);
        h.succeedWhen(() -> {
            h.assertTrue(spitter.distanceTo(prey) >= 6, "Spitter did not establish its firing distance");
            h.assertTrue(spitter.getTarget() == prey, "Spitter lost its target while backing away");
            spitter.discard();
            prey.discard();
        });
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x <= 22; x++) {
            for (int z = 0; z <= 17; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    private static InfectedMob infected(GameTestHelper h, InfectedKind kind, int x, int y, int z) {
        return place(h, InfectedContent.TYPES.get(kind).get(), x, y, z, false);
    }

    private static <T extends Mob> T place(GameTestHelper h, EntityType<T> type, int x, int y, int z, boolean noAi) {
        T entity = Objects.requireNonNull(type.create(h.getLevel()));
        var p = h.absolutePos(new BlockPos(x, y, z));
        entity.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, 0, 0);
        entity.setNoAi(noAi);
        h.getLevel().addFreshEntity(entity);
        return entity;
    }
}
