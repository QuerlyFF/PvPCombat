package dev.smpcristalix.pvpcombat.api;

/** Неизменяемый снимок прогресса, безопасный для передачи другим плагинам. */
public record PlayerStatsSnapshot(
        int damageLevel,
        double healthHearts,
        int speedLevel,
        int satietyLevel,
        int abilityLevel
) {}
