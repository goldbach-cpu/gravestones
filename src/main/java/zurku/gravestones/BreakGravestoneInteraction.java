package zurku.gravestones;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

/**
 * HYTALE 0.6.1: Minimal Interaction for breaking gravestones.
 * Actual block breaking is handled by BreakBlockEvent listener in GravestonePlugin.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class BreakGravestoneInteraction extends SimpleBlockInteraction {

    private static GravestoneManager manager;

    public static final BuilderCodec<BreakGravestoneInteraction> CODEC = BuilderCodec.builder(
        BreakGravestoneInteraction.class,
        BreakGravestoneInteraction::new,
        SimpleBlockInteraction.CODEC
    ).build();

    public static final BreakGravestoneInteraction INSTANCE = new BreakGravestoneInteraction("Break_Gravestone");

    public static void setManager(GravestoneManager mgr) {
        manager = mgr;
    }

    public BreakGravestoneInteraction() {
        super();
    }

    public BreakGravestoneInteraction(String id) {
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
        // Block breaking happens server-side via BreakBlockEvent in GravestonePlugin
    }
}
