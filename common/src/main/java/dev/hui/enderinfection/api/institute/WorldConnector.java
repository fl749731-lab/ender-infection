package dev.hui.enderinfection.api.institute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record WorldConnector(String name, BlockPos pos, Direction facing, String socket) {}
