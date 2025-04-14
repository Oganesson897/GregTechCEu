package me.oganesson.gregtech.common;

import gregtech.api.GTValues;

import net.minecraftforge.common.config.Config;

import me.oganesson.gregtech.ExtensionValues;

@Config(modid = GTValues.MODID, name = GTValues.MODID + '/' + ExtensionValues.MODID)
public class ConfigExtension {

    public static boolean enableDataFixer = false;
}
