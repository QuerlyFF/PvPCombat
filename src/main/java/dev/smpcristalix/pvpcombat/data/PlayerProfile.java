package dev.smpcristalix.pvpcombat.data;

/**
 * Сохранённый прогресс игрока.
 *
 * <p>Храним именно дискретные ступени, а не итоговые проценты. Благодаря этому
 * баланс можно менять в config.yml без миграции каждого профиля.</p>
 */
public final class PlayerProfile {
    private int damageLevel;
    private int healthStep;
    private int speedLevel;
    private int satietyLevel;
    private int abilityLevel;
    private long lastStatLossAt;

    public int damageLevel() {
        return damageLevel;
    }

    public void damageLevel(int value) {
        damageLevel = value;
    }

    /** Отрицательные значения — штрафная зона здоровья ниже ванильных 10 сердец. */
    public int healthStep() {
        return healthStep;
    }

    public void healthStep(int value) {
        healthStep = value;
    }

    public int speedLevel() {
        return speedLevel;
    }

    public void speedLevel(int value) {
        speedLevel = value;
    }

    public int satietyLevel() {
        return satietyLevel;
    }

    public void satietyLevel(int value) {
        satietyLevel = value;
    }

    public int abilityLevel() {
        return abilityLevel;
    }

    public void abilityLevel(int value) {
        abilityLevel = value;
    }

    public long lastStatLossAt() {
        return lastStatLossAt;
    }

    public void lastStatLossAt(long value) {
        lastStatLossAt = value;
    }
}
