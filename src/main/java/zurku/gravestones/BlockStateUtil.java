package zurku.gravestones;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Since Hytale Update 5 the old {@code BlockState}/{@code ItemContainerState} class hierarchy
 * (com.hypixel.hytale.server.core.universe.world.meta.state.*) was removed entirely and replaced
 * with an ECS component model: blocks are entities on the {@link ChunkStore}, and a container is
 * just a regular component ({@link ItemContainerBlock}) attached to that entity.
 *
 * IMPORTANT: always resolve the {@link BlockComponentChunk} through the same {@link WorldChunk}
 * instance that placed the block (via WorldChunk.getBlockComponentChunk()), not via a fresh
 * lookup through the ChunkStore's component registry - the two didn't reliably observe the same
 * mutations in testing (likely a stale/duplicate lookup, or a timing issue around chunk load
 * state), which was the actual root cause of the container never resolving after placement.
 *
 * Also note: a freshly-placed block entity is not always immediately a "live" entity with a
 * Ref. BlockEntity.setBlockEntity() only calls accessor.addEntity(...) (a live Ref, found via
 * BlockComponentChunk.getEntityReference()) if the owning chunk is currently "ticking". If not,
 * the engine instead stores a raw Holder (BlockComponentChunk.getEntityHolder()) until a loading
 * system promotes it later - so both cases need to be checked.
 */
public final class BlockStateUtil {

    private BlockStateUtil() {}

    /**
     * Returns the {@link ItemContainerBlock} component for the block at the given position, or
     * {@code null} if there isn't one (chunk not loaded, no block entity, or the block doesn't
     * carry a container).
     */
    public static ItemContainerBlock getItemContainerBlock(World world, int x, int y, int z) {
        try {
            WorldChunk chunk = getLoadedChunk(world, x, z);
            if (chunk == null) return null;

            BlockComponentChunk blockComponentChunk = chunk.getBlockComponentChunk();
            if (blockComponentChunk == null) return null;

            final int columnBlockIndex = ChunkUtil.indexBlockInColumn(x, y, z);

            Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(columnBlockIndex);
            if (blockRef != null && blockRef.isValid()) {
                return blockRef.getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
            }

            // Not yet promoted to a live entity (chunk wasn't ticking at placement time) -
            // read straight off the raw Holder instead.
            var holder = blockComponentChunk.getEntityHolder(columnBlockIndex);
            if (holder != null) {
                return holder.getComponent(ItemContainerBlock.getComponentType());
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves the loaded {@link WorldChunk} for a block position, or {@code null} if it
     * isn't currently loaded.
     */
    public static WorldChunk getLoadedChunk(World world, int x, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        return world.getChunkIfLoaded(chunkIndex);
    }
}
