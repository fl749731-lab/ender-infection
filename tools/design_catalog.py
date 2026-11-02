from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PROJECT=ROOT/'ender_infection_colored'
ASSETS=PROJECT/'src/main/resources/assets/ender_infection'
DATA=PROJECT/'src/main/resources/data/ender_infection'
PACK=ROOT/'colored_units/design_pack_2026-11-02_v2'

# Runtime enum, model, label, hp, speed, damage, impact tick, animation ticks, reach, width, height, minimum mother stage, price.
UNITS=[
 ('FORAGER','forager','lurking_forager','潜伏觅食虫',44,.32,5,16,40,2.2,1.15,.8,1,18),
 ('SPOREWING_SCOUT','sporewing_scout','sporewing_scout','孢翼侦察者',48,.26,5,16,40,14,1.0,1.4,2,24),
 ('BURROWER','burrower','mycelial_burrower','菌丝掘地者',120,.25,12,16,40,3,1.3,2.5,3,38),
 ('CARRIER','carrier','crystalback_carrier','晶背搬运兽',180,.22,10,16,40,2.8,1.5,1.8,2,34),
 ('SPIDER_MOTHER','spider_mother','parasitic_spider_mother','寄生蛛母',180,.25,11,16,40,3,2.5,1.8,3,52),
 ('HATCH_SAC','hatch_sac','pulsing_hatch_sac','脉动孵化囊',160,0,0,16,40,8,1.7,2,2,42),
 ('SHIELD_SENTINEL','shield_sentinel','crystal_shield_sentinel','晶盾卫士',160,.22,13,16,40,3,1.1,2.2,3,42),
 ('THROAT_SPITTER','throat_spitter','rift_throat_spitter','裂喉喷吐者',110,.23,9,16,40,18,1.1,2.3,3,36),
 ('LEAP_AMBUSHER','leap_ambusher','leapblade_ambusher','跃刃伏击者',110,.34,16,16,40,7,1.1,2.4,3,42),
 ('ROOT_TURRET','root_turret','rootspike_turret','根刺炮台',160,0,10,16,40,20,1.7,1.8,3,44),
 ('RESONANCE_PRIEST','resonance_priest','resonance_priest','共鸣祭司',130,.22,7,16,40,14,1.0,2.8,4,60),
 ('SIEGEBREAKER','siegebreaker','siegebreaker_colossus','破城巨像',420,.20,24,16,40,4.2,2.6,3.4,4,120),
 ('WALKING_NEST','walking_nest','walking_brood_crab','行巢巨蟹',750,.14,20,16,40,6,5.5,6.2,5,240),
 ('BURROW_KING','burrow_king','abyssal_burrow_king','深渊掘王',850,.18,26,16,40,18,4.8,7.0,5,260),
]
BLOCKS={
 'white_wall_tile':'lab_tile_light','dark_wall_tile':'lab_tile_dark','lab_brick_tile':'lab_tile','checker_floor':'lab_checker',
 'lab_slab':'lab_slab','lab_stair':'lab_stair','vent_panel':'lab_vent','service_hatch':'lab_hatch',
 'reinforced_door':'lab_door','lab_cabinet':'lab_cabinet','crystal_incubator':'incubator','surgery_table':'surgery_table',
 'equipment_bench':'lab_workbench','observation_window':'lab_window'}
ARCHITECTURE=set(list(BLOCKS)[:6])
MATERIALS={
 'infected_tissue_pickup':'infected_tissue','mycelium_bundle':'mycelium_bundle','amethyst_fragment_cluster':'resonant_shard',
 'spore_pod_drop':'spore_pod','chitin_plate_drop':'chitin_plate','condensed_biomass_drop':'condensed_biomass',
 'resonant_organ_drop':'resonance_organ','brood_core_drop':'mother_heart'}
WEAPONS={'prism_spear':'prism_spear','earthsplitter_hammer':'earthsplitter_hammer','crystal_spike_crossbow':'crystal_spike_crossbow','resonance_staff':'resonance_staff'}
ARMOR={'crystal_carapace_light_armor':'crystal_carapace','brood_heavy_armor':'brood_heavy'}
