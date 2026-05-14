package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.StringRepresentable;

public enum BreakerOption implements IConfigOptionListEntry, StringRepresentable {
    HOLD("hold", "litematica-printer.config.generic.option.breakerOption.hold"),
    AUTO("auto", "litematica-printer.config.generic.option.breakerOption.auto");

    private final String value;
    private final String displayKey;

    BreakerOption(String value, String displayKey) {
        this.value = value;
        this.displayKey = displayKey;
    }

    @Override
    public String getStringValue() {
        return this.value;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.translate(this.displayKey);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        BreakerOption[] values = values();
        int index = this.ordinal();
        int nextIndex = forward ? index + 1 : index - 1;
        if (nextIndex >= values.length) {
            nextIndex = 0;
        } else if (nextIndex < 0) {
            nextIndex = values.length - 1;
        }
        return values[nextIndex];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (BreakerOption option : values()) {
            if (option.value.equalsIgnoreCase(value)) {
                return option;
            }
        }
        return HOLD;
    }

    @Override
    public String getSerializedName() {
        return this.value;
    }
}
