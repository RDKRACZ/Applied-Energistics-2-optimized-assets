package appeng.siteexport;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class Scene {
    SceneRenderSettings settings;
    String filename;
    Map<BlockPos, BlockState> blocks = new HashMap<>();
    Consumer<ClientLevel> postSetup;

    public Scene(SceneRenderSettings settings, String filename) {
        this.settings = settings;
        this.filename = filename;
    }

    public BlockPos getMin() {
        return BoundingBox.encapsulatingPositions(blocks.keySet())
                .map(bb -> new BlockPos(bb.minX(), bb.minY(), bb.minZ()))
                .orElseThrow();
    }

    public BlockPos getMax() {
        return BoundingBox.encapsulatingPositions(blocks.keySet())
                .map(bb -> new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()))
                .orElseThrow();
    }

    public void clearArea(ClientLevel level) {
        var padding = 1;
        var min = getMin().offset(-padding, -padding, -padding);
        var max = getMax().offset(padding, padding, padding);

        var lightEngine = level.getLightEngine();
        var nibbles = new byte[DataLayer.SIZE];
        Arrays.fill(nibbles, (byte) 0xFF);
        DataLayer dataLayer = new DataLayer(nibbles);

        var secMin = SectionPos.of(min);
        var secMax = SectionPos.of(max);
        SectionPos.betweenClosedStream(
                secMin.x(), secMin.y(), secMin.z(),
                secMax.x(), secMax.y(), secMax.z()
        ).forEach(sectionPos -> {
            lightEngine.queueSectionData(LightLayer.SKY, sectionPos, dataLayer, true);
            lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, dataLayer, true);
            lightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        });

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    public void setUp(ClientLevel level) {
        for (var entry : blocks.entrySet()) {
            var pos = entry.getKey();
            var state = entry.getValue();

            level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
        }

        if (postSetup != null) {
            postSetup.accept(level);
        }
    }
}
