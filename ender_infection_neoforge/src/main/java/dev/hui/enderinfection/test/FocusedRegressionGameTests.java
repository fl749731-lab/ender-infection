package dev.hui.enderinfection.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused reproduction of navigation and wave regressions; assertions stay in the original tests. */
@GameTestHolder("ender_regression_checks")
@PrefixGameTestTemplate(false)
public final class FocusedRegressionGameTests {
    @GameTest(template="empty64", templateNamespace="ender_regression_checks", timeoutTicks=170, batch="wave")
    public static void wave(GameTestHelper helper) {
        InfectionGameTests.bossCrystalWaveHitsDistantEnemyOnceAndSparesFaction(helper);
    }
    @GameTest(template="empty64", templateNamespace="ender_regression_checks", timeoutTicks=260, batch="cargo")
    public static void cargo(GameTestHelper helper) { BroodTacticsGameTests.carrierDeliversEvenOneTissueToItsOwnMother(helper); }
    @GameTest(template="empty64", templateNamespace="ender_regression_checks", timeoutTicks=360, batch="guard")
    public static void guard(GameTestHelper helper) { RiftGameTests.guardHitsNearbyPreyThenReturnsInsteadOfChasingOutOfRange(helper); }
    @GameTest(template="empty64", templateNamespace="ender_regression_checks", timeoutTicks=240, batch="spider")
    public static void spider(GameTestHelper helper) { DesignCombatGameTests.spiderMotherStrikes(helper); }
    @GameTest(template="empty64", templateNamespace="ender_regression_checks", timeoutTicks=250, batch="archer")
    public static void archer(GameTestHelper helper) { InfectionGameTests.skeletonClearsAllyFireLaneAndBacksAwayFromCloseEnemies(helper); }
}
