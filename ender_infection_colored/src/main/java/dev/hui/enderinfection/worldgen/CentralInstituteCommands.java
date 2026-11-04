package dev.hui.enderinfection.worldgen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

/** Separate literals preserve the room-extension API's existing preview/place grammar. */
public final class CentralInstituteCommands {
    private CentralInstituteCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("enderinstitute").requires(source -> source.hasPermission(2))
            .then(Commands.literal("previewcentral").then(Commands.argument("origin", BlockPosArgument.blockPos())
                .executes(context -> start(context, true))))
            .then(Commands.literal("build").then(Commands.argument("origin", BlockPosArgument.blockPos())
                .executes(context -> start(context, false))))
            .then(Commands.literal("status").executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(CentralInstitute.jobs(context.getSource().getLevel()).status()), false);
                return 1;
            }))
            .then(Commands.literal("cancel").executes(context -> {
                var data = CentralInstitute.jobs(context.getSource().getLevel());
                boolean canceled = data.cancel();
                context.getSource().sendSuccess(() -> Component.literal(data.status()), false);
                return canceled ? 1 : 0;
            })));
    }
    private static int start(CommandContext<CommandSourceStack> context, boolean preview) {
        var source = context.getSource();
        var origin = BlockPosArgument.getBlockPos(context, "origin");
        UUID player = source.getEntity() == null ? new UUID(0, 0) : source.getEntity().getUUID();
        try {
            boolean queued = preview ? CentralInstitute.preview(source.getLevel(), origin, player)
                    : CentralInstitute.queue(source.getLevel(), origin, Rotation.NONE, player);
            if (!queued) {
                source.sendFailure(Component.literal("已有中央研究所任务；使用 /enderinstitute status 或 cancel。"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal((preview ? "已开始中央研究所只读预检" : "已开始中央研究所预检及施工")
                    + "：120×104×55，起点为楼体最小角 " + origin.toShortString() + "，G1 地面 Y=" + (origin.getY() + CentralInstitute.GROUND_OFFSET)
                    + "，固定 none 旋转。" + (preview ? "预检不会放置方块。" : "施工将替换获准覆盖的地形和被动基础建材；取消不回滚。")
                    + "全包围盒（含保留区域）内已有容器、方块实体、实验室部件、活动红石、外部模组或未知类型方块会使任务停止。"
                    + "只处理已加载区块，缺块时等待并在 status 显示坐标。"), true);
            return 1;
        } catch (RuntimeException failure) {
            source.sendFailure(Component.literal("中央研究所任务未创建：" + failure.getMessage()));
            return 0;
        }
    }
}
