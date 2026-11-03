package dev.hui.enderinfection.test;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
/** Opt-in expansion checks delegate to the same assertions as the complete suite. */
@GameTestHolder("ender_expansion_checks")
@PrefixGameTestTemplate(false)
public final class FocusedExpansionGameTests {
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_0")
 public static void check0(GameTestHelper h){InfectionGameTests.expansionWaitsForItsBiomassIntervalAndKeepsProgressOnReload(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_1")
 public static void check1(GameTestHelper h){InfectionGameTests.expansionClimbsSixStepsAndHighlandCountsForIncome(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_2")
 public static void check2(GameTestHelper h){InfectionGameTests.expansionCannotSeedADisconnectedDiagonalIsland(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_3")
 public static void check3(GameTestHelper h){InfectionGameTests.rootedNodesDoNotBuyRadiusOverBlockedTerrain(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_4")
 public static void check4(GameTestHelper h){InfectionGameTests.frontierScoresExcludeTheParentsCoveredLand(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_5")
 public static void check5(GameTestHelper h){InfectionGameTests.aDestroyedOwnedOutpostCanBeRebuiltAfterReload(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_6")
 public static void check6(GameTestHelper h){InfectionGameTests.nodeChargesItsOwnMotherAndPersistsOwnership(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_7")
 public static void check7(GameTestHelper h){InfectionGameTests.matureNodeBuildsOnePaidOwnedFrontierOutpostAndPersistsIt(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_8")
 public static void check8(GameTestHelper h){InfectionGameTests.outpostsRequireTerrainOwnFundsAndBoundedColonyCapacity(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_9")
 public static void check9(GameTestHelper h){InfectionGameTests.frontierErosionClimbsConnectedOneBlockSteps(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_10")
 public static void check10(GameTestHelper h){InfectionGameTests.outpostSelectsFreshNutrientsOverExhaustedLand(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_11")
 public static void check11(GameTestHelper h){InfectionGameTests.fungiAboveMyceliumErodeWithoutBlockingTheFrontier(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_12")
 public static void check12(GameTestHelper h){InfectionGameTests.cropAgeAndFarmlandHeightSurviveAtomicPaidErosion(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_13")
 public static void check13(GameTestHelper h){InfectionGameTests.tallPlantsKeepBothHalvesUntilMatureAndNeverPartiallySpend(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_14")
 public static void check14(GameTestHelper h){InfectionGameTests.landIncomeScalesWithConvertedAreaAndMaturityWithoutReloadDuplicates(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_15")
 public static void check15(GameTestHelper h){InfectionGameTests.overlappingNodesAndMothersDoNotDoubleCountLandOrLoadRemoteChunks(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_16")
 public static void check16(GameTestHelper h){InfectionGameTests.motherExpandsBeforeEvolvingAndKeepsWorkingCapital(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_17")
 public static void check17(GameTestHelper h){InfectionGameTests.nodeErodesDownwardInsteadOfOnlyTheSurfaceLayer(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_18")
 public static void check18(GameTestHelper h){InfectionGameTests.erodedGroundMaturesInsteadOfStayingAtFirstStage(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_19")
 public static void check19(GameTestHelper h){InfectionGameTests.craftedDustPausesOwnedNodeWithoutSpendingThenResumes(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_20")
 public static void check20(GameTestHelper h){InfectionGameTests.infectionClimbsAnExposedCliffWithoutJumpingToAFloatingBlock(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_21")
 public static void check21(GameTestHelper h){InfectionGameTests.richNodeActuallySpreadsSoonerThanPoorNode(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_22")
 public static void check22(GameTestHelper h){InfectionGameTests.expansionPlanningNeverLoadsARemoteNodeChunk(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_23")
 public static void check23(GameTestHelper h){InfectionGameTests.biggerNodeNetworksKeepConstructionCapitalBeforePeaceTimeHatching(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_24")
 public static void check24(GameTestHelper h){InfectionGameTests.youngOutpostCanCrossItsParentsBeltToReachFreshGround(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_25")
 public static void check25(GameTestHelper h){InfectionGameTests.oldOutpostLatchAdoptsSavedChildBeforeRebuilding(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_26")
 public static void check26(GameTestHelper h){InfectionGameTests.aHighLedgeCanHostAnOutpostInsideTheHorizontalRing(h);}
 @GameTest(template="empty64",templateNamespace="ender_expansion_checks",timeoutTicks=500,batch="expansion_check_27")
 public static void check27(GameTestHelper h){InfectionGameTests.snowyGrassUnderASnowLayerCanBeEroded(h);}
}
