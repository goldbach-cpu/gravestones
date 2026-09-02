package zurku.gravestones;

/**
 * HYTALE 0.6.1 PORT: GravestoneBlockState is now just a data holder.
 * The 0.5.x complex BlockState interfaces (ItemContainerBlockState, DestroyableBlockState, etc.)
 * no longer exist in 0.6.1. Instead, we use:
 * - GravestoneManager to persist gravestone data (JSON)
 * - Interactions to handle item access (GUI/Instant)
 * - BreakBlockEvent to handle block destruction
 * 
 * This class is kept for backwards compatibility / data structure only.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class GravestoneBlockState {

    protected java.util.UUID ownerUUID;
    protected String ownerName;
    protected long deathTime;

    public GravestoneBlockState() {
        this.deathTime = System.currentTimeMillis();
    }

    public void setOwner(java.util.UUID uuid, String name) {
        this.ownerUUID = uuid;
        this.ownerName = name;
    }

    public java.util.UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getDeathTime() {
        return deathTime;
    }
}
