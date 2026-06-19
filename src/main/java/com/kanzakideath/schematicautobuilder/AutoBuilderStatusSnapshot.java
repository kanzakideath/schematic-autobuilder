package com.kanzakideath.schematicautobuilder;

public record AutoBuilderStatusSnapshot(
        String mode,
        String state,
        String action,
        double progress,
        int totalBlocks,
        int doneBlocks,
        int remainingBlocks,
        int failedBlocks,
        int unreachableBlocks,
        int missingMaterialTypes,
        int missingMaterialItems,
        int materialChestCount,
        String baritoneStatus,
        String target,
        String eta,
        long updatedAtMs
) {
    public static AutoBuilderStatusSnapshot empty() {
        return new AutoBuilderStatusSnapshot(
                "IDLE",
                "待機中",
                "待機中",
                0.0D,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "idle",
                "",
                "--:--",
                System.currentTimeMillis()
        );
    }
}
