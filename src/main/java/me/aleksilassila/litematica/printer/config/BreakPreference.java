package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.StringRepresentable;

public enum BreakPreference implements IConfigOptionListEntry, StringRepresentable {
    DEFAULT("default", "litematica-printer.config.generic.option.breakPreference.default"),
    FORTUNE("fortune", "litematica-printer.config.generic.option.breakPreference.fortune"),
    SILK_TOUCH("silkTouch", "litematica-printer.config.generic.option.breakPreference.silkTouch");

    private final String value;
    private final String displayKey;

    BreakPreference(String value, String displayKey) {
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
        BreakPreference[] values = values();
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
        for (BreakPreference preference : values()) {
            if (preference.value.equalsIgnoreCase(value)) {
                return preference;
            }
        }
        return DEFAULT;
    }

    @Override
    public String getSerializedName() {
        return this.value;
    }
}
