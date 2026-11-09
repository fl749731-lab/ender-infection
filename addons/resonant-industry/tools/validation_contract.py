"""Evidence required by the addon build and release commands."""

NATIVE_MINIMUM = {"forge": 47, "neoforge": 55}
PERSISTENCE_GROUPS = {
    "forge": ("persistence", "compound_persistence"),
    "neoforge": ("persistence", "compound_persistence", "labware_persistence", "workshop_persistence"),
}
INDUSTRY_FRAMES = (
    "preview_missing", "generator_running", "processor_running", "centrifuge_running", "centrifuge_complete",
    "production_line", "generator_detail", "processor_detail", "centrifuge_detail", "cable_detail", "wrench_first_person", "handbook",
    "manual_structure_3d", "layer_blueprint", "compound_running", "compound_complete", "compound_guide",
)
LABWARE_FRAMES = (
    "lab_empty", "lab_water", "lab_pipette_sample", "lab_six_vessels", "lab_transfer", "lab_blocked",
    "lab_five_mb", "lab_retrieve", "lab_handbook", "chemistry_measured", "chemistry_waiting_signal",
    "chemistry_stirring", "chemistry_neutralized", "chemistry_crystallizing", "chemistry_crystals",
    "chemistry_stations", "chemistry_handbook", "separation_filtered", "separation_waiting_receiver",
    "separation_distilling", "separation_recovered", "separation_stations", "separation_handbook",
    "chemistry_narrow_controls", "chemistry_narrow_carried", "chemistry_narrow_transfer",
)


def verify_blueprints(common, actual, loader):
    extras = {"resonant_industry:plate_press"} if loader == "neoforge" else set()
    actual_machines = actual["machines"]
    ids = [machine["id"] for machine in actual_machines]
    assert len(set(ids)) == len(ids), "Duplicate native blueprint"
    assert set(ids).intersection(extras) == extras, "Missing loader blueprint"
    shared = [machine for machine in actual_machines if machine["id"] not in extras]
    assert dict(actual, machines=shared) == common, f"{loader} common blueprint layout mismatch"
