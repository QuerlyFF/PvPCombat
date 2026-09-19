package dev.smpcristalix.pvpcombat;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import dev.smpcristalix.pvpcombat.api.PvPCombatApi;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSmokeTest {
    private ServerMock server;
    private PvPCombatPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PvPCombatPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginLoadsWithoutGrimAndPublishesApi() {
        assertTrue(plugin.isEnabled());
        PvPCombatApi api = Bukkit.getServicesManager().load(PvPCombatApi.class);
        assertNotNull(api);
    }

    @Test
    void shardApiUsesPdcAndConsumesExactAmount() {
        var player = server.addPlayer();
        PvPCombatApi api = Bukkit.getServicesManager().load(PvPCombatApi.class);
        assertNotNull(api);

        var shard = api.createShard(1);
        assertEquals(Material.AMETHYST_SHARD, shard.getType());
        assertTrue(api.isShard(shard));

        api.giveShards(player, 3);
        assertEquals(3, api.countShards(player));
        assertTrue(api.consumeShards(player, 2));
        assertEquals(1, api.countShards(player));
    }
}
