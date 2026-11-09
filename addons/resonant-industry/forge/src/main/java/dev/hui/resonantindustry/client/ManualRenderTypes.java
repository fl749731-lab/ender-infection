package dev.hui.resonantindustry.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** GUI lines stay in the main target after the world's Fabulous transparency pass. */
final class ManualRenderTypes extends RenderStateShard {
    private static final RenderType LINES = RenderType.create(
            "resonant_industry_manual_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    // Context wires must not hide a selected part inside the structure.
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private ManualRenderTypes() {
        super("resonant_industry_manual", () -> {}, () -> {});
    }

    static RenderType lines() { return LINES; }
}
