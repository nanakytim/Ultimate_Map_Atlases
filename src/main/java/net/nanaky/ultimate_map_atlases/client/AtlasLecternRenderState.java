package net.nanaky.ultimate_map_atlases.client;

import net.minecraft.client.resources.model.sprite.SpriteId;

public interface AtlasLecternRenderState {
    boolean mapatlases$hasAtlas();

    void mapatlases$setHasAtlas(boolean hasAtlas);

    SpriteId mapatlases$getTexture();

    void mapatlases$setTexture(SpriteId texture);
}
