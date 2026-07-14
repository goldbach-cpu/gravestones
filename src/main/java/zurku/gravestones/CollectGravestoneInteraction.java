package zurku.gravestones;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import org.joml.Vector3d;
import org.joml.Vector3i;
import zurku.gravestones.event.GravestoneCollectedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a chest-style inventory window for the gravestone's contents instead of instantly
 * transferring everything to the player and destroying the block (the old, pre-Update-5
 * behaviour). The block only despawns once the player has actually emptied it and closed
 * the window - mirrors the engine's own built-in OpenContainerInteraction (used by chests),
 * with our owner/access checks layered in front of it.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CollectGravestoneInteraction extends SimpleBlockInteraction {

    private static GravestoneManager manager;

    public static final BuilderCodec<CollectGravestoneInteraction> CODEC = BuilderCodec.builder(
        CollectGravestoneInteraction.class,
        CollectGravestoneInteraction::new,
        SimpleBlockInteraction.CODEC
    ).build();

    public static final CollectGravestoneInteraction INSTANCE = new CollectGravestoneInteraction("Collect_Gravestone");

    public static final RootInteraction ROOT = new RootInteraction(
        INSTANCE.getId(),
        new String[] { INSTANCE.getId() }
    );

    // Same hardcoded state ids the built-in OpenContainerInteraction uses; our block JSON's
    // State.Definitions already wires these to the chest open/close sounds.
    private static final String OPEN_WINDOW = "OpenWindow";
    private static final String CLOSE_WINDOW = "CloseWindow";

    public static void setManager(GravestoneManager mgr) {
        manager = mgr;
    }

    public CollectGravestoneInteraction() {
        super();
    }

    public CollectGravestoneInteraction(String id) {
        super(id);
    }

    @Override
    protected void interactWithBlock(
            World world,
            CommandBuffer<EntityStore> buffer,
            InteractionType type,
            InteractionContext ctx,
            ItemStack held,
            Vector3i pos,
            CooldownHandler cooldown) {

        Ref ref = ctx.getEntity();
        Store store = ref.getStore();
        Player player = buffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = buffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        if (manager != null) {
            manager.registerWorld(world);
        }

        // External access checker (before built-in owner check)
        boolean skipOwnerCheck = false;
        if (manager != null && manager.getAccessChecker() != null) {
            UUID owner = manager.getGravestoneOwner(pos.x, pos.y, pos.z);
            UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
            UUID accessorUuid = uuidComp != null ? uuidComp.getUuid() : null;
            if (accessorUuid != null) {
                GravestoneAccessChecker.AccessResult result = manager.getAccessChecker().canAccess(
                        accessorUuid, owner, pos.x, pos.y, pos.z, world.getName());
                if (result == GravestoneAccessChecker.AccessResult.DENY) return;
                if (result == GravestoneAccessChecker.AccessResult.ALLOW) skipOwnerCheck = true;
            }
        }

        if (!skipOwnerCheck && manager != null && manager.getSettings().isOwnerProtection()) {
            UUID owner = manager.getGravestoneOwner(pos.x, pos.y, pos.z);
            if (owner != null) {
                UUIDComponent uuidComp = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
                if (uuidComp == null || !owner.equals(uuidComp.getUuid())) {
                    return;
                }
            }
        }

        // Resolve the block entity + its container the same way the engine's own
        // OpenContainerInteraction (used by chests) does.
        final var chunkStore = world.getChunkStore();
        final long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        final var chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) return;

        final var chunkComponentStore = chunkStore.getStore();
        final var blockComponentChunk = chunkComponentStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) return;

        final int columnBlockIndex = ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z);
        final var blockRef = blockComponentChunk.getEntityReference(columnBlockIndex);
        if (blockRef == null) return;

        final ItemContainerBlock containerBlock = chunkComponentStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (containerBlock == null) return;

        // /gsitems toggle: instant (old, pre-Update-5 behaviour) vs. GUI (default).
        if (manager != null && manager.getSettings().isInstantCollect()) {
            doInstantCollect(world, buffer, ref, pos, containerBlock);
            return;
        }

        final var blockChunkComponent = chunkComponentStore.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunkComponent == null) return;

        final int blockId = blockChunkComponent.getBlock(pos.x, pos.y, pos.z);
        final BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return;

        final var section = blockChunkComponent.getSectionAtBlockY(pos.y);
        final int rotationIndex = section.getRotationIndex(pos.x, pos.y, pos.z);

        final var window = new ContainerBlockWindow(pos.x, pos.y, pos.z, rotationIndex, blockType, containerBlock.getItemContainer());
        final var windows = containerBlock.getWindows();

        final var uuidComponent = buffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;
        final var uuid = uuidComponent.getUuid();

        if (windows.putIfAbsent(uuid, window) == null) {
            if (player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
                window.registerCloseEvent(unused -> onWindowClose(world, ref, uuid, pos, windows, containerBlock, buffer));

                if (windows.size() == 1) {
                    world.setBlockInteractionState(pos, blockType, OPEN_WINDOW);
                }
            } else {
                windows.remove(uuid, window);
            }
        }
    }

    /**
     * Old (pre-Update-5) behaviour: forces everything straight into the player's inventory
     * and destroys the gravestone immediately, dropping anything that didn't fit on the
     * ground. Kept behind the /gsitems toggle for admins who prefer it over the GUI.
     */
    private void doInstantCollect(World world, CommandBuffer<EntityStore> buffer, Ref ref,
                                   Vector3i pos, ItemContainerBlock containerBlock) {
        CombinedItemContainer playerInv = InventoryComponent.getCombined(buffer, ref,
            InventoryComponent.Storage.getComponentType(),
            InventoryComponent.Armor.getComponentType(),
            InventoryComponent.Hotbar.getComponentType(),
            InventoryComponent.Utility.getComponentType(),
            InventoryComponent.Backpack.getComponentType(),
            InventoryComponent.Tool.getComponentType());

        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : containerBlock.getItemContainer().dropAllItemStacks()) {
            var tx = playerInv.addItemStack(stack);
            ItemStack remainder = tx.getRemainder();
            if (remainder != null && !ItemStack.isEmpty(remainder)) {
                leftover.add(remainder);
            }
        }

        if (!leftover.isEmpty()) {
            HeadRotation headRotation = buffer.getComponent(ref, HeadRotation.getComponentType());
            Rotation3fc rotation = headRotation != null ? headRotation.getRotation() : Rotation3f.IDENTITY;
            TransformComponent transform = buffer.getComponent(ref, TransformComponent.getComponentType());
            Vector3d dropPos = transform != null
                ? transform.getPosition()
                : new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            ItemComponent.generateItemDrops(buffer, leftover, dropPos, rotation);
        }

        UUIDComponent uuidComponent = buffer.getComponent(ref, UUIDComponent.getComponentType());
        UUID uuid = uuidComponent != null ? uuidComponent.getUuid() : null;
        fireCollectedEvent(world, uuid, pos);

        if (manager != null) {
            manager.removeGravestoneAtPosition(pos.x, pos.y, pos.z);
            manager.breakGravestoneBlock(world, pos.x, pos.y, pos.z);
        }
    }

    private void fireCollectedEvent(World world, UUID accessorUuid, Vector3i pos) {
        try {
            UUID ownerUuid = manager != null ? manager.getGravestoneOwner(pos.x, pos.y, pos.z) : null;
            IEventDispatcher<GravestoneCollectedEvent, GravestoneCollectedEvent> dispatcher =
                HytaleServer.get().getEventBus().dispatchFor(GravestoneCollectedEvent.class);
            if (dispatcher.hasListener()) {
                dispatcher.dispatch(new GravestoneCollectedEvent(accessorUuid, ownerUuid, pos.x, pos.y, pos.z, world.getName()));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Once the window is closed: if the player emptied the gravestone, clean up the
     * bookkeeping and despawn the (now empty) block. Otherwise leave it in place so the
     * player (or whoever else is allowed) can come back for the rest later.
     */
    private void onWindowClose(World world, Ref<EntityStore> ref, UUID uuid, Vector3i pos,
                                Map<UUID, ContainerBlockWindow> windows, ItemContainerBlock containerBlock,
                                CommandBuffer<EntityStore> buffer) {
        windows.remove(uuid, containerBlock.getWindows().get(uuid));

        if (!containerBlock.getItemContainer().isEmpty()) {
            // Still has items left - leave the gravestone standing.
            return;
        }

        fireCollectedEvent(world, uuid, pos);

        if (manager != null) {
            manager.removeGravestoneAtPosition(pos.x, pos.y, pos.z);
            manager.breakGravestoneBlock(world, pos.x, pos.y, pos.z);
        }
    }

    @Override
    protected void simulateInteractWithBlock(
            InteractionType type,
            InteractionContext ctx,
            ItemStack held,
            World world,
            Vector3i pos) {
    }
}
