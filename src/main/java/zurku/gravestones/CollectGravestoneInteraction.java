package zurku.gravestones;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * HYTALE 0.6.1: Minimal Interaction class for collecting gravestones.
 * Items are retrieved from GravestoneManager JSON when player right-clicks.
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
    public void simulateInteractWithBlock(InteractionType type, InteractionContext context,
                                         ItemStack held, World world, Vector3i pos) {
        // Client-side preview - nothing to do
    }

    @Override
    public void interactWithBlock(World world, CommandBuffer<EntityStore> buffer,
                                 InteractionType type, InteractionContext context,
                                 ItemStack held, Vector3i pos, CooldownHandler cooldown) {
        // Normally GravestoneUseSystem handles (and cancels) the UseBlockEvent.Pre before this
        // interaction runs. This is the fallback path if the event was not intercepted.
        try {
            if (manager == null || world == null || pos == null) return;
            GravestoneData data = manager.getGravestoneAt(pos.x, pos.y, pos.z, world.getName());
            if (data == null) return;
            Ref<EntityStore> actor = context.getEntity();
            if (actor == null) return;
            GravestoneUseSystem.collect(manager, manager.getLogger(), world.getEntityStore().getStore(),
                buffer, actor, world, pos, data);
        } catch (Exception e) {
            if (manager != null) manager.getLogger().atWarning().log("[Gravestones] Collect interaction error: " + e.getMessage());
        }
    }
}
