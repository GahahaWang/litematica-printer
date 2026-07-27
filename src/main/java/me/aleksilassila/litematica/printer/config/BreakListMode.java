package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.util.StringRepresentable;

public enum BreakListMode implements IConfigOptionListEntry, StringRepresentable {
    NONE("none", "litematica-printer.config.printer.option.breakListMode.none"),
    BLACKLIST("blacklist", "litematica-printer.config.printer.option.breakListMode.blacklist"),
    WHITELIST("whitelist", "litematica-printer.config.printer.option.breakListMode.whitelist");

    private final String value;
    private final String displayKey;

    BreakListMode(String value, String displayKey) {
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
        BreakListMode[] values = values();
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
        for (BreakListMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return this.value;
    }
}
