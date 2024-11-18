package lol.petrik.pmcauth;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lol.petrik.pmcauth.Chat.Formatter;
import lol.petrik.pmcauth.Limbo.CustomCommandHandler;
import lol.petrik.pmcauth.Limbo.CustomLimboConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import ua.nanit.limbo.configuration.LimboConfig;
import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.CommandHandler;
import ua.nanit.limbo.server.LimboServer;

@Plugin(id = "pmcauth", name = "PMCAuth", version = BuildConstants.VERSION, authors = {"nemvince"})
public class PMCAuth {

  private final ProxyServer server;
  private final Logger logger;
  private final String name;

  private final HTTPClient httpClient = new HTTPClient();

  private final LimboServer limbo;

  @Inject
  public PMCAuth(ProxyServer server, Logger logger) {
    this.server = server;
    this.logger = logger;

    LimboConfig limboConfig = new CustomLimboConfig();
    CommandHandler<Command> limboCommandHandler = new CustomCommandHandler();

    this.limbo = new LimboServer(limboConfig, limboCommandHandler, getClass().getClassLoader());

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
    logger.info("Player {} is trying to connect.", player.getUsername());

    // put player in limbo
    RegisteredServer limboServer = server.getServer("limbo").get();
    event.setResult(ServerPreConnectEvent.ServerResult.allowed(limboServer));

    logger.info("Player {} is now in limbo.", player.getUsername());
    // send player chat message

    player.sendMessage(Formatter.info("Szia, küldtem egy üzenetet Discordon, kérlek hagy jóvá a bélépést!"));
  }

  public void onLoad() {
    logger.info("{} loaded", this.name);
  }

  public void onEnable() {
    try {
      this.limbo.start();

      ServerInfo limboServerInfo = new ServerInfo("limbo", new InetSocketAddress("localhost", 60000));
      server.registerServer(limboServerInfo);
      logger.info("Limbo server started and registered");
    } catch (Exception e) {
      logger.error("Error while starting Limbo: {}", e.getMessage());
      e.printStackTrace();
    }
    logger.info("{} enabled", this.name);
  }

  public void onDisable() {
    try {
      this.limbo.stop();
    } catch (Exception e) {
      logger.error("Error while stopping Limbo: {}", e.getMessage());
      e.printStackTrace();
    }
    logger.info("{} disabled", this.name);
  }
}