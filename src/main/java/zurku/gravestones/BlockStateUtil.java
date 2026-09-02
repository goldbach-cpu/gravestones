package zurku.gravestones;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

/**
 * Hytale 0.6.1 PORT: Simplified utility for chunk operations.
 * Block data is now primarily managed via GravestoneManager (JSON),
 * not through BlockStates.
 */
public final class BlockStateUtil {

    private BlockStateUtil() {}

    /**
     * Resolves the loaded {@link WorldChunk} for a block position, or {@code null} if it
     * isn't currently loaded.
     */
    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        if (world == null) return null;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getChunkIfLoaded(chunkIndex);
    }
}
