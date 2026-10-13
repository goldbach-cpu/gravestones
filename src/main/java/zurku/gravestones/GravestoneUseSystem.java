package zurku.gravestones;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import zurku.gravestones.event.GravestoneCollectedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hytale 0.6.x: right-click ("Use") on a gravestone block.
 *
 * Block interactions are ECS events in 0.6; a plugin listens by registering an
 * {@link EntityEventSystem} on the entity store, exactly like the built-in
 * TriggerVolumeBlockEventSystems.BlockUsed does. The handler runs for the entity
 * that performed the interaction (the player), on the world thread.
 */
@SuppressWarnings({"deprecation", "removal"})
public class GravestoneUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final GravestoneManager manager;
    private final HytaleLogger logger;

    public GravestoneUseSystem(GravestoneManager manager, HytaleLogger logger) {
        super(UseBlockEvent.Pre.class);
        this.manager = manager;
        this.logger = logger;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {
        try {
            if (event.isCancelled()) return;
            if (event.getInteractionType() != InteractionType.Use) return;

            Vector3i pos = event.getTargetBlock();
            World world = store.getExternalData().getWorld();
            if (world == null) return;

            GravestoneData data = manager.getGravestoneAt(pos.x, pos.y, pos.z, world.getName());
            if (data == null) return; // not a gravestone -> let vanilla handle it

            Ref<EntityStore> actor = archetypeChunk.getReferenceTo(index);
            // We handle this interaction ourselves either way.
            event.setCancelled(true);
            collect(manager, logger, store, commandBuffer, actor, world, pos, data);
        } catch (Exception e) {
            logger.atSevere().log("[Gravestones] Use handler error: " + e.getMessage());
        }
    }

    /**
     * Shared collect logic (used by the ECS Use event and by CollectGravestoneInteraction).
     * Runs on the world thread. Safe to call twice: the record is removed on first success.
     */
    public static void collect(GravestoneManager manager, HytaleLogger logger,
                               Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                               Ref<EntityStore> actor, World world, Vector3i pos, GravestoneData data) {
        try {
            PlayerRef playerRef = store.getComponent(actor, PlayerRef.getComponentType());
            UUIDComponent uuidComp = store.getComponent(actor, UUIDComponent.getComponentType());
            UUID accessor = playerRef != null ? playerRef.getUuid()
                          : (uuidComp != null ? uuidComp.getUuid() : null);

            if (!manager.canAccess(accessor, data)) {
                if (playerRef != null) {
                    playerRef.sendMessage(Message.raw("This gravestone belongs to someone else."));
                }
                return;
            }

            if (!manager.getSettings().isInstantCollect()) {
                openGui(manager, logger, store, actor, world, pos, data, playerRef);
                return;
            }

            List<ItemStack> items = data.toItemStacks();
            List<ItemStack> leftover = new ArrayList<>();

            // Instant collect: straight into the player's inventory, overflow drops on the ground.
            // (The 0.5.x container GUI relied on block-state APIs that no longer exist; both
            //  /gsitems modes therefore collect directly for now.)
            Player player = store.getComponent(actor, Player.getComponentType());
            if (player != null && player.getInventory() != null) {
                CombinedItemContainer container = player.getInventory().getCombinedHotbarFirst();
                for (ItemStack stack : items) {
                    ItemStackTransaction tx = container.addItemStack(stack);
                    if (tx == null || !tx.succeeded()) {
                        leftover.add(stack);
                    } else if (tx.getRemainder() != null && !ItemStack.isEmpty(tx.getRemainder())) {
                        leftover.add(tx.getRemainder());
                    }
                }
            } else {
                leftover.addAll(items);
            }

            if (!leftover.isEmpty()) {
                dropItems(store, commandBuffer, actor, leftover, pos);
            }

            manager.removeGravestoneAtPosition(pos.x, pos.y, pos.z);
            manager.clearBlock(world, pos.x, pos.y, pos.z);

            logger.atInfo().log("[Gravestones] " + (playerRef != null ? playerRef.getUsername() : accessor)
                + " collected " + items.size() + " item stack(s) at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("Gravestone collected: " + items.size() + " item stack(s)."));
            }

            try {
                IEventDispatcher<GravestoneCollectedEvent, GravestoneCollectedEvent> dispatcher =
                    HytaleServer.get().getEventBus().dispatchFor(GravestoneCollectedEvent.class);
                if (dispatcher.hasListener()) {
                    dispatcher.dispatch(new GravestoneCollectedEvent(accessor, data.getPlayerId(),
                        pos.x, pos.y, pos.z, world.getName()));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logger.atSevere().log("[Gravestones] Collect error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Container GUI mode (/gsitems toggle)
    // ------------------------------------------------------------------------

    /** Live containers of gravestones that currently have (or had) an open window. */
    private static final Map<String, SimpleItemContainer> LIVE = new ConcurrentHashMap<>();

    private static String liveKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** Called by the manager whenever a gravestone record is removed (break, despawn, collect). */
    public static void discardLive(int x, int y, int z) {
        SimpleItemContainer c = LIVE.remove(liveKey(x, y, z));
        if (c != null) {
            try { c.clear(); } catch (Exception ignored) {}
        }
    }

    private static void openGui(GravestoneManager manager, HytaleLogger logger,
                                Store<EntityStore> store, Ref<EntityStore> actor,
                                World world, Vector3i pos, GravestoneData data, PlayerRef playerRef) {
        Player player = store.getComponent(actor, Player.getComponentType());
        if (player == null) return;

        String key = liveKey(pos.x, pos.y, pos.z);
        SimpleItemContainer container = LIVE.get(key);
        if (container == null) {
            List<ItemStack> items = data.toItemStacks();
            int n = Math.max(9, ((items.size() + 8) / 9) * 9);
            container = new SimpleItemContainer((short) Math.min(72, n));
            container.addItemStacks(items);
            final SimpleItemContainer c = container;
            container.registerChangeEvent(ev -> syncFromContainer(manager, logger, world, pos, c));
            LIVE.put(key, container);
        }

        // Use the block that is actually in the world (the window validates against it).
        BlockType blockType = null;
        try {
            blockType = BlockStateUtil.getBlockType(world, pos.x, pos.y, pos.z);
        } catch (Exception ignored) {}
        if (blockType == null || blockType.getItem() == null) {
            blockType = BlockType.getAssetMap().getAsset(manager.getSettings().getGravestoneBlockId());
        }
        if (blockType == null || blockType.getItem() == null) {
            logger.atWarning().log("[Gravestones] No item-backed block type at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
            return;
        }

        ContainerBlockWindow window = new ContainerBlockWindow(pos.x, pos.y, pos.z, 0, blockType, container);
        boolean opened = player.getPageManager().setPageWithWindows(actor, store, Page.Bench, true, window);
        if (!opened) {
            logger.atWarning().log("[Gravestones] Could not open container window at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
            return;
        }
        final SimpleItemContainer c = container;
        window.registerCloseEvent(ev -> syncFromContainer(manager, logger, world, pos, c));
        logger.atInfo().log("[Gravestones] " + (playerRef != null ? playerRef.getUsername() : "?")
            + " opened gravestone GUI at (" + pos.x + ", " + pos.y + ", " + pos.z + ")");
    }

    /** Write the container contents back to the record; remove the gravestone once it is empty. */
    private static void syncFromContainer(GravestoneManager manager, HytaleLogger logger,
                                          World world, Vector3i pos, SimpleItemContainer container) {
        try {
            GravestoneData data = manager.getGravestoneAt(pos.x, pos.y, pos.z, world.getName());
            if (data == null) return; // already removed (broken / despawned / emptied)

            List<ItemStack> remaining = new ArrayList<>();
            for (short i = 0; i < container.getCapacity(); i++) {
                ItemStack st = container.getItemStack(i);
                if (st != null && !ItemStack.isEmpty(st)) remaining.add(st);
            }

            if (remaining.isEmpty()) {
                LIVE.remove(liveKey(pos.x, pos.y, pos.z));
                manager.removeGravestoneAtPosition(pos.x, pos.y, pos.z);
                world.execute(() -> manager.clearBlock(world, pos.x, pos.y, pos.z));
                logger.atInfo().log("[Gravestones] Gravestone at (" + pos.x + ", " + pos.y + ", " + pos.z + ") emptied via GUI");
                try {
                    IEventDispatcher<GravestoneCollectedEvent, GravestoneCollectedEvent> dispatcher =
                        HytaleServer.get().getEventBus().dispatchFor(GravestoneCollectedEvent.class);
                    if (dispatcher.hasListener()) {
                        dispatcher.dispatch(new GravestoneCollectedEvent(null, data.getPlayerId(),
                            pos.x, pos.y, pos.z, world.getName()));
                    }
                } catch (Exception ignored) {}
            } else {
                manager.updateItems(data, remaining);
            }
        } catch (Exception e) {
            logger.atWarning().log("[Gravestones] GUI sync error: " + e.getMessage());
        }
    }

    static void dropItems(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                          Ref<EntityStore> actor, List<ItemStack> items, Vector3i pos) {
        HeadRotation head = store.getComponent(actor, HeadRotation.getComponentType());
        Rotation3f rotation = head != null ? head.getRotation() : new Rotation3f();
        Vector3d dropPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, items, dropPos, rotation);
        commandBuffer.addEntities(drops, AddReason.SPAWN);
    }
}
