package dev.smpcristalix.pvpcombat.integration;

/** Минимальный контракт optional-интеграции без внешних API в сигнатуре. */
public interface IntegrationBridge {
    boolean registerIfAvailable();

    void unregister();
}
