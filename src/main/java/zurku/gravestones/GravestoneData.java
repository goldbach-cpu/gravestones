package zurku.gravestones;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GravestoneData {

    private UUID playerId;
    private int x;
    private int y;
    private int z;
    private String worldName;
    private long createdTime;

    // 0.6.x: items are no longer kept inside a block container (ItemContainerBlock was
    // removed from the plugin API). They are persisted here, in gravestones.json.
    private List<StoredItem> items = new ArrayList<>();

    public GravestoneData() {}

    public GravestoneData(UUID playerId, int x, int y, int z, String worldName) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.worldName = worldName;
        this.createdTime = System.currentTimeMillis();
    }

    public UUID getPlayerId() { return playerId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getWorldName() { return worldName; }
    public long getCreatedTime() { return createdTime; }

    public String getLocationKey() {
        return worldName + ":" + x + "," + y + "," + z;
    }

    // ---- item storage ------------------------------------------------------

    public void setItemStacks(List<ItemStack> stacks) {
        items = new ArrayList<>();
        if (stacks == null) return;
        for (ItemStack s : stacks) {
            if (s != null && !ItemStack.isEmpty(s)) {
                items.add(StoredItem.from(s));
            }
        }
    }

    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    public List<ItemStack> toItemStacks() {
        List<ItemStack> out = new ArrayList<>();
        if (items == null) return out;
        for (StoredItem si : items) {
            ItemStack s = si.toItemStack();
            if (s != null) out.add(s);
        }
        return out;
    }

    /** Plain data holder so Gson can serialize item stacks without touching Hytale internals. */
    public static class StoredItem {
        public String id;
        public int quantity;
        public double durability;
        public double maxDurability;
        public int qualityIndex;
        public String metadata; // BSON document as JSON, may be null

        public static StoredItem from(ItemStack s) {
            StoredItem si = new StoredItem();
            si.id = s.getItemId();
            si.quantity = s.getQuantity();
            si.durability = s.getDurability();
            si.maxDurability = s.getMaxDurability();
            si.qualityIndex = s.getQualityIndex();
            BsonDocument meta = s.getMetadata();
            si.metadata = meta != null ? meta.toJson() : null;
            return si;
        }

        public ItemStack toItemStack() {
            if (id == null || quantity <= 0) return null;
            BsonDocument meta = null;
            if (metadata != null && !metadata.isEmpty()) {
                try {
                    meta = BsonDocument.parse(metadata);
                } catch (Exception ignored) {
                    meta = null;
                }
            }
            try {
                return new ItemStack(id, quantity, durability, maxDurability, qualityIndex, meta);
            } catch (Exception e) {
                // item id may no longer exist after a content update
                return null;
            }
        }
    }
}
