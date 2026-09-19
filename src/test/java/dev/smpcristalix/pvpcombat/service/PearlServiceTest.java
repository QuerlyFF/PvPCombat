package dev.smpcristalix.pvpcombat.service;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import dev.smpcristalix.pvpcombat.config.PvPCombatSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PearlServiceTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadResetsRuntimeChargesWhenMaximumChanges() {
        var player = server.addPlayer();
        PearlService service = new PearlService(settings(3, 10));

        assertTrue(service.canThrow(player));
        service.recordThrow(player);
        service.recordThrow(player);
        assertEquals(1, service.remaining(player));

        service.reload(settings(2, 10));
        assertTrue(service.canThrow(player));
        assertEquals(2, service.remaining(player));
    }

    @Test
    void lastChargeStartsCustomCooldown() {
        var player = server.addPlayer();
        PearlService service = new PearlService(settings(2, 10));

        service.recordThrow(player);
        service.recordThrow(player);
        assertFalse(service.canThrow(player));
        assertEquals(0, service.remaining(player));
        assertTrue(service.cooldownSeconds(player) > 0L);
    }

    private PvPCombatSettings settings(int charges, int rechargeSeconds) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("pearls.charges", charges);
        config.set("pearls.recharge-seconds", rechargeSeconds);
        return new PvPCombatSettings(config);
    }
}
