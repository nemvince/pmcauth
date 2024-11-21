package lol.petrik.pmcauth.config;

import lol.petrik.pmcauth.PMCAuth;
import lol.petrik.pmcauth.Util;
import lombok.Getter;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public class Config {
  private ConfigurationNode root;

  @Getter
  private final String wsUrl;

  @Getter
  private final String httpUrl;

  public Config(PMCAuth plugin) {
    try {
      root = YamlConfigurationLoader.builder()
          .defaultOptions(ConfigurationOptions.defaults())
          .file(ConfigUtil.saveDefaultConfig(plugin, "config.yml"))
          .build().load();

    } catch (ConfigurateException e) {
      plugin.getLogger().error("Failed to load config", e);
      Util.die(plugin.getServer(), plugin);
    }

    String backendHost = root.node("backend", "host").getString("localhost");
    String httpPort = root.node("backend", "http").getString("3000");
    String wsPort = root.node("backend", "ws").getString("3001");
    boolean useSSL = root.node("backend", "ssl").getBoolean(false);

    this.wsUrl = "ws" + (useSSL ? "s" : "") + "://" + backendHost + ":" + wsPort + "/ws";
    this.httpUrl = "http" + (useSSL ? "s" : "") + "://" + backendHost + ":" + httpPort;
  }
}
