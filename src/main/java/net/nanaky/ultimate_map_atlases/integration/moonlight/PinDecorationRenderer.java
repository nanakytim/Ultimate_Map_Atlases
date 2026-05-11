package net.nanaky.ultimate_map_atlases.integration.moonlight;

import net.minecraft.resources.Identifier;

public class PinDecorationRenderer extends AtlasOnlyDecorationRenderer<PinDecoration> {

    public PinDecorationRenderer(Identifier texture) {
        super(texture);
    }

    @Override
    protected boolean hasOutline(PinDecoration decoration) {
        return decoration.isFocused();
    }

}
