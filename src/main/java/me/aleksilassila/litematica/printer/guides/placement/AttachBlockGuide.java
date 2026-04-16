package me.aleksilassila.litematica.printer.guides.placement;

import me.aleksilassila.litematica.printer.SchematicBlockState;

public class AttachBlockGuide extends PropertySpecificGuesserGuide {
    public AttachBlockGuide(SchematicBlockState state) {
        super(state);
    }
    @Override
    public boolean getRequiresSupport() {
        return true;
    }
}
