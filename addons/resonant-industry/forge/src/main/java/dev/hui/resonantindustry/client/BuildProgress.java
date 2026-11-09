package dev.hui.resonantindustry.client;

import dev.hui.resonantindustry.IndustryBlock;
import dev.hui.resonantindustry.IndustryNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/** A read-only survey of placed blocks, not the server's ownership/assembly check. */
record BuildProgress(int placed, int total, SortedMap<String, Integer> missing, int blockedAir,
                     int unknown, int wrongBlocks, List<Cell> cells) {
    enum State { CORRECT, MISSING, WRONG, BLOCKED, UNKNOWN }
    record Cell(BlockPos position, String role, int layer, State state) {}

    BuildProgress {
        missing = Collections.unmodifiableSortedMap(new TreeMap<>(missing));
        cells = List.copyOf(cells);
    }

    static BuildProgress scan(IndustryNode controller) {
        var cells = new ArrayList<Cell>();
        var level = controller.getLevel();
        var definition = controller.definition();
        if (level == null || definition == null) return summarize(cells);
        for (var entry : definition.cells().entrySet()) {
            var position = controller.world(entry.getKey()).immutable();
            String expected = entry.getValue();
            // ClientLevel.hasChunkAt reports true even without a client chunk. Never request/load one.
            var chunk = level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false);
            State result;
            if (chunk == null) result = State.UNKNOWN;
            else {
                var actual = chunk.getBlockState(position);
                if (expected.equals("air")) result = actual.isAir() ? State.CORRECT : State.BLOCKED;
                else if (actual.getBlock() instanceof IndustryBlock block && block.role.equals(expected)) result = State.CORRECT;
                else result = actual.isAir() ? State.MISSING : State.WRONG;
            }
            cells.add(new Cell(position, expected, entry.getKey().y() + 1, result));
        }
        return summarize(cells);
    }

    BuildProgress forLayer(int layer) {
        return layer < 0 ? this : summarize(cells.stream().filter(cell -> cell.layer() == layer).toList());
    }

    boolean complete() { return total > 0 && placed == total && blockedAir == 0 && unknown == 0; }

    private static BuildProgress summarize(List<Cell> cells) {
        int placed = 0, total = 0, blocked = 0, unknown = 0, wrong = 0;
        var missing = new TreeMap<String, Integer>();
        for (var cell : cells) {
            if (!cell.role().equals("air")) {
                total++;
                if (cell.state() == State.CORRECT) placed++;
                else if (cell.state() != State.UNKNOWN) missing.merge(cell.role(), 1, Integer::sum);
            }
            if (cell.state() == State.BLOCKED) blocked++;
            if (cell.state() == State.UNKNOWN) unknown++;
            if (cell.state() == State.WRONG) wrong++;
        }
        return new BuildProgress(placed, total, missing, blocked, unknown, wrong, cells);
    }
}
