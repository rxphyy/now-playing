package com.example;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "nowplaying")
public class NowPlayingConfig implements ConfigData {
    public enum Side {
        LEFT,
        RIGHT
    }

    @ConfigEntry.Gui.Tooltip
    public Side sidePosition = Side.RIGHT;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int yPosition = 10;

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int backgroundOpacity = 56;

    @ConfigEntry.Gui.Tooltip
    public boolean showCoverArt = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showMediaTitle = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showArtistName = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showTimeline = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showPlayStatusIcon = true;
}