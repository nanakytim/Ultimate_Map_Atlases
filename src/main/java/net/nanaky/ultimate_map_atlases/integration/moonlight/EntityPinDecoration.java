package net.nanaky.ultimate_map_atlases.integration.moonlight;

import net.nanaky.moonlight.api.map.CustomMapDecoration;
import net.nanaky.moonlight.api.map.type.MapDecorationType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;

public class EntityPinDecoration extends CustomMapDecoration {
    private final Entity entity;

    public EntityPinDecoration(MapDecorationType<?, ?> type, byte x, byte y, Entity entity) {
        super(type, x, y, (byte) 0, null);
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }

    public EntityPinDecoration(MapDecorationType<?, ?> type, FriendlyByteBuf buffer) {
        super(type, buffer);
        this.entity = null;
    }

    @Override
    public byte getX() {
        return super.getX();
    }
}
