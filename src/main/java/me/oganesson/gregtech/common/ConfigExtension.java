package me.oganesson.gregtech.common;

import gregtech.api.GTValues;

import me.oganesson.gregtech.ExtensionValues;

import net.minecraftforge.common.config.Config;

@Config(modid = GTValues.MODID, name = GTValues.MODID + '/' + ExtensionValues.MODID)
public class ConfigExtension {

    public static boolean enableDataFixer = false;

}
