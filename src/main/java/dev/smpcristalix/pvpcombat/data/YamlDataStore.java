package dev.smpcristalix.pvpcombat.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File; import java.io.IOException; import java.util.*;

/** UUID-хранилище прогресса и антифарм-cooldown'ов. */
public final class YamlDataStore {
    private final JavaPlugin plugin; private final File file;
    private final Map<UUID,PlayerProfile> profiles=new HashMap<>(); private final Map<String,Long> rewards=new HashMap<>();
    public YamlDataStore(JavaPlugin plugin){this.plugin=plugin;this.file=new File(plugin.getDataFolder(),"data.yml");}
    public void load(){profiles.clear();rewards.clear();var y=YamlConfiguration.loadConfiguration(file);var s=y.getConfigurationSection("players");if(s!=null)for(String k:s.getKeys(false)){try{UUID u=UUID.fromString(k);PlayerProfile p=new PlayerProfile();String b="players."+k+".";p.damageLevel(y.getInt(b+"damage"));p.healthStep(y.getInt(b+"health-step"));p.speedLevel(y.getInt(b+"speed"));p.satietyLevel(y.getInt(b+"satiety"));p.abilityLevel(y.getInt(b+"ability"));p.lastStatLossAt(y.getLong(b+"last-stat-loss"));profiles.put(u,p);}catch(IllegalArgumentException ignored){plugin.getLogger().warning("Bad UUID: "+k);}}var r=y.getConfigurationSection("reward-cooldowns");if(r!=null)for(String k:r.getKeys(false))rewards.put(k,r.getLong(k));}
    public PlayerProfile profile(UUID u){return profiles.computeIfAbsent(u,x->new PlayerProfile());}
    public long rewardCooldown(String k){return rewards.getOrDefault(k,0L);} public void rewardCooldown(String k,long v){rewards.put(k,v);}
    public void save(){var y=new YamlConfiguration();profiles.forEach((u,p)->{String b="players."+u+".";y.set(b+"damage",p.damageLevel());y.set(b+"health-step",p.healthStep());y.set(b+"speed",p.speedLevel());y.set(b+"satiety",p.satietyLevel());y.set(b+"ability",p.abilityLevel());y.set(b+"last-stat-loss",p.lastStatLossAt());});rewards.forEach((k,v)->y.set("reward-cooldowns."+k,v));try{if(!plugin.getDataFolder().exists())plugin.getDataFolder().mkdirs();y.save(file);}catch(IOException ex){plugin.getLogger().severe("Cannot save data.yml: "+ex.getMessage());}}
}
