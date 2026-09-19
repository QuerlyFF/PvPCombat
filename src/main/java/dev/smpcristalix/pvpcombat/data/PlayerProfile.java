package dev.smpcristalix.pvpcombat.data;
/** Храним дискретные ступени, чтобы баланс менялся конфигом без миграции данных. */
public final class PlayerProfile {
    private int damageLevel, healthStep, speedLevel, satietyLevel, abilityLevel; private long lastStatLossAt;
    public int damageLevel(){return damageLevel;} public void damageLevel(int v){damageLevel=v;}
    public int healthStep(){return healthStep;} public void healthStep(int v){healthStep=v;}
    public int speedLevel(){return speedLevel;} public void speedLevel(int v){speedLevel=v;}
    public int satietyLevel(){return satietyLevel;} public void satietyLevel(int v){satietyLevel=v;}
    public int abilityLevel(){return abilityLevel;} public void abilityLevel(int v){abilityLevel=v;}
    public long lastStatLossAt(){return lastStatLossAt;} public void lastStatLossAt(long v){lastStatLossAt=v;}
}
