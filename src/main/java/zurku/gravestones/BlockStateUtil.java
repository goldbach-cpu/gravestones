package zurku.gravestones;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

/**
 * Hytale 0.7.0 PORT: chunk/block helpers.
 *
 * In 0.7.0 World no longer has getChunkIfLoaded/getChunkAsync and WorldChunk no longer has
 * setBlock/getBlockType. Chunks are split into sections stored in the ChunkStore; blocks are
 * written with BlockOperations.setBlock(ChunkStore, sectionRef, ...) and read with SectionReader.
 */
public final class BlockStateUtil {

    private BlockStateUtil() {}

    /** Resolves the loaded {@link WorldChunk} for a block position, or {@code null} if not loaded. */
    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        if (world == null) return null;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getChunkStore().getChunkComponent(chunkIndex, WorldChunk.getComponentType());
    }

    /** Block type currently in the world at the position, or {@code null} (not loaded / empty). */
    public static BlockType getBlockType(World world, int x, int y, int z) {
        if (world == null) return null;
        int id = new SectionReader(world.getChunkStore()).getBlock(x, y, z);
        return BlockType.getAssetMap().getAsset(id);
    }

    /**
     * Sets a block. Must run on the world thread. Returns false if the section is not loaded.
     */
    public static boolean setBlockNow(World world, int x, int y, int z, int blockId, BlockType type) {
        ChunkStore store = world.getChunkStore();
        Ref<ChunkStore> section = store.getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null) return false;
        return BlockOperations.setBlock(store, section, x, y, z, blockId, type, 0,
            FillerBlockUtil.NO_FILLER, SetBlockSettings.NONE);
    }

    /**
     * Sets a block from any thread: hops onto the world thread, and loads the section
     * asynchronously first if it isn't loaded yet.
     */
    public static void setBlock(World world, int x, int y, int z, int blockId, BlockType type) {
        world.execute(() -> {
            if (setBlockNow(world, x, y, z, blockId, type)) return;
            world.getChunkStore().getChunkSectionReferenceAtBlockAsync(x, y, z).thenAccept(ref -> {
                if (ref != null) world.execute(() -> setBlockNow(world, x, y, z, blockId, type));
            });
        });
    }

    /** Replaces the block with air (no item drop). */
    public static void clearBlock(World world, int x, int y, int z) {
        setBlock(world, x, y, z, BlockType.EMPTY_ID, BlockType.EMPTY);
    }
}
