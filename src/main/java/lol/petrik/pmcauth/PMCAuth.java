package lol.petrik.pmcauth;

import java.nio.file.Path;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

@Plugin(id = "pmcauth", name = "PMCAuth", version = BuildConstants.VERSION, authors = {"nemvince"})
public class PMCAuth {
  private static PMCAuth instance;

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final String name;
  private final HTTPClient httpClient = new HTTPClient();

  @Inject
  public PMCAuth(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    instance = this;

    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;

    this.name = this.getClass().getAnnotation(Plugin.class).name();

    this.onLoad();
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    this.onEnable();
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    this.onDisable();
  }

  @Subscribe
  public void onServerProxyPreConnectionEvent(ServerPreConnectEvent event) {
    Player player = event.getPlayer();

    event.setResult(ServerPreConnectEvent.ServerResult.denied());

    httpClient.get("http://10.0.0.100/test.txt" + player.getUsername()).thenAccept(response -> {
      Component responseComponent = Component.text(response);
      player.disconnect(responseComponent);
    });
  }

  public void onLoad() {
    System.out.println(this.name + " loaded.");

  }

  public void onEnable() {
    System.out.println(this.name + " enabled");
  }

  public void onDisable() {
    System.out.println(this.name + " disabled");
  }
}