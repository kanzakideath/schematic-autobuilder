package com.kanzakideath.schematicautobuilder;

import net.fabricmc.api.ClientModInitializer;

public final class SchematicAutoBuilderMod implements ClientModInitializer {

    public static final String MOD_ID = "schematicautobuilder";

    @Override
    public void onInitializeClient() {
        AutoBuilderConfig.load();
        AutoUpdater.start();
    }
}

