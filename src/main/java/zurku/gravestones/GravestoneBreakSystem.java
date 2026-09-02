package zurku.gravestones;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.event.IEventDispatcher;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;
import zurku.gravestones.event.GravestoneBrokenEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Hytale 0.6.x: a gravestone block is being broken by an entity (player).
 *
 * Non-owners are blocked when owner protection is on. Otherwise the stored items are
 * dropped on the ground, the record is removed and the block is cleared by us
 * (the vanilla break is cancelled so the tombstone itself does not drop as an item).
 */
public class GravestoneBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final GravestoneManager manager;
    private final HytaleLogger logger;

    public GravestoneBreakSystem(GravestoneManager manager, HytaleLogger logger) {
        super(BreakBlockEvent.class);
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
                       @Nonnull BreakBlockEvent event) {
        try {
            if (event.isCancelled()) return;

            Vector3i pos = event.getTargetBlock();
            World world = store.getExternalData().getWorld();
            if (world == null) return;

            GravestoneData data = manager.getGravestoneAt(pos.x, pos.y, pos.z, world.getName());
            if (data == null) return; // ordinary block

            Ref<EntityStore> actor = archetypeChunk.getReferenceTo(index);
            PlayerRef playerRef = store.getComponent(actor, PlayerRef.getComponentType());
            UUIDComponent uuidComp = store.getComponent(actor, UUIDComponent.getComponentType());
            UUID accessor = playerRef != null ? playerRef.getUuid()
                          : (uuidComp != null ? uuidComp.getUuid() : null);

            // Always take over the break so the vanilla tombstone item never drops.
            event.setCancelled(true);

            if (!manager.canAccess(accessor, data)) {
                if (playerRef != null) {
                    playerRef.sendMessage(Message.raw("You cannot break someone else's gravestone."));
                }
                return;
            }

            List<ItemStack> items = data.toItemStacks();
            if (!items.isEmpty()) {
                GravestoneUseSystem.dropItems(store, commandBuffer, actor, items, pos);
            }

            manager.removeGravestoneAtPosition(pos.x, pos.y, pos.z);
            manager.clearBlock(world, pos.x, pos.y, pos.z);

            logger.atInfo().log("[Gravestones] Gravestone at (" + pos.x + ", " + pos.y + ", " + pos.z
                + ") broken, dropped " + items.size() + " item stack(s)");

            try {
                IEventDispatcher<GravestoneBrokenEvent, GravestoneBrokenEvent> dispatcher =
                    HytaleServer.get().getEventBus().dispatchFor(GravestoneBrokenEvent.class);
                if (dispatcher.hasListener()) {
                    dispatcher.dispatch(new GravestoneBrokenEvent(accessor, data.getPlayerId(),
                        pos.x, pos.y, pos.z, world.getName()));
                }
            } catch (Exception ignored) {}

        } catch (Exception e) {
            logger.atSevere().log("[Gravestones] Break handler error: " + e.getMessage());
        }
    }
}
