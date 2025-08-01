package com.example;

import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import com.example.NowPlayingConfig.Side;

public class CustomSideGuiProvider implements GuiProvider {

    @Override
    public List<AbstractConfigListEntry> get(String i13n, Field field, Object config, Object defaults, GuiRegistryAccess registry) {

        ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();

        try {
            return Collections.singletonList(
                    entryBuilder.startEnumSelector(
                                    Text.translatable(i13n),
                                    Side.class,
                                    (Side) field.get(config)
                            )
                            .setSaveConsumer(newValue -> {
                                try {
                                    field.set(config, newValue);
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            })
                            .setTooltip(Text.translatable(i13n + ".tooltip"))
                            .build()
            );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}