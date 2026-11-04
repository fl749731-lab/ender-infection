package dev.hui.enderinfection.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Supplier;

public final class CrystalArmorModel {
    private CrystalArmorModel() {}

    public static IClientItemExtensions forMaterial(Supplier<String> material) {
        return new IClientItemExtensions() {
            private HumanoidModel<LivingEntity> model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> original) {
                if (slot == EquipmentSlot.LEGS) return original;
                if (model == null) model = bake(material.get());
                return model;
            }
        };
    }

    private static HumanoidModel<LivingEntity> bake(String material) {
        var mesh = HumanoidModel.createMesh(new CubeDeformation(1), 0);
        var root = mesh.getRoot();
        var body = root.getChild("body");
        var head = root.getChild("head");
        for (int side : new int[]{-1, 1}) {
            var arm = root.getChild(side < 0 ? "right_arm" : "left_arm");
            var leg = root.getChild(side < 0 ? "right_leg" : "left_leg");
            PartDefinition shoulder;
            switch (material) {
                case "resonant_crystal" -> {
                    shoulder = part(arm, "underplate", side * 2, -2, 0, 0, 0, side * -.18F,
                         box(64, 0, -3, 0, -4, 6, 4, 8));
                    part(arm, "pauldron", side * 3, -2, -.2F, 0, 0, side * -.30F,
                         box(96, 0, -2.5F, -1, -3.5F, 5, 2, 7));
                    shard(body, "fork_" + side, side * 2.5F, 4, 4.4F, -.34F, side * .34F);
                }
                case "ember_crystal" -> {
                    shoulder = part(arm, "forged_shell", side * 2.1F, -2.2F, 0, 0, 0, side * -.13F,
                         box(64, 0, -3, 0, -4, 6, 4, 8));
                    part(arm, "overlapping_lame", side * 4.4F, -.2F, 0, 0, 0, side * .28F,
                         box(96, 0, -2.5F, -1, -3.5F, 5, 2, 7));
                    part(body, "exhaust_" + side, side * 2.55F, 3, 4.4F, -.26F, 0, side * .16F,
                         box(64, 18, -1.5F, -1, -1.5F, 3, 6, 3));
                    shard(body, "ember_" + side, side * 2.55F, 3, 4.7F, -.31F, side * .18F);
                }
                case "void_crystal" -> {
                    shoulder = part(arm, "void_shell", side * 2.6F, -2.7F, 0, 0, 0, side * -.18F,
                         box(64, 0, -3, 0, -4, 6, 4, 8));
                    part(arm, "projecting_blade", side * 3.8F, -3.3F, 0, 0, 0, side * -.37F,
                         box(96, 0, -2.5F, -1, -3.5F, 5, 2, 7));
                    shard(body, "satellite_" + side, side * 3, 5.8F, 5, -.42F, side * .47F);
                }
                default -> throw new IllegalArgumentException(material);
            }
            part(shoulder, "front_bevel", 0, 1.4F, -3.9F, .32F, 0, side * -.15F,
                 box(80, 47, -2.5F, -1, -1, 5, 2, 2));
            for (int end : new int[]{-1, 1}) {
                part(shoulder, "rivet_" + end, side * 2.6F, 2.4F, end * 3F, .1F, 0, 0,
                     box(98, 48, -.5F, -.5F, -.5F, 1, 1, 1));
            }
            part(head, "brow_" + side, side * 3.2F, -6.8F, -4.7F, 0, 0, side * .31F,
                 box(80, 20, -1.5F, -.5F, -1, 3, 2, 2));
            part(leg, "boot_guard", side * .35F, 8.6F, -2.8F, -.10F, 0, 0,
                 box(94, 20, -2, 0, -1, 4, 2, 2));
        }
        part(body, "crystal_mount", 0, 4, 3.4F, 0, 0, 0,
             box(64, 34, -3.5F, -3, -1, 7, 7, 2));
        if (!material.equals("ember_crystal")) {
            float height = material.equals("void_crystal") ? .9F : 5.5F;
            shard(body, "spine", 0, height, 5.4F, -.40F, .08F);
        }
        return new HumanoidModel<>(LayerDefinition.create(mesh, 128, 64).bakeRoot());
    }

    private static void shard(PartDefinition parent, String name, float x, float y, float z,
                              float pitch, float roll) {
        var crystal = part(parent, name, x, y, z, pitch, .31F, roll,
                           box(94, 33, -1.5F, -3, -1.5F, 3, 6, 3));
        part(crystal, "cut_tip", 0, -3, 0, 0, 0, 0, box(108, 33, -1, -2, -1, 2, 2, 2));
        part(crystal, "point", 0, -5, 0, 0, 0, 0, box(118, 33, -.5F, -1, -.5F, 1, 1, 1));
        part(crystal, "collar", 0, 2.7F, 0, 0, 0, 0, box(64, 48, -2, -1, -2, 4, 2, 4));
    }

    private static CubeListBuilder box(int u, int v, float x, float y, float z, int w, int h, int d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }

    private static PartDefinition part(PartDefinition parent, String name, float x, float y, float z,
                                       float pitch, float yaw, float roll, CubeListBuilder cube) {
        return parent.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(x, y, z, pitch, yaw, roll));
    }
}
