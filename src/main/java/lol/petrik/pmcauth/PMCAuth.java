package lol.petrik.pmcauth;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lol.petrik.pmcauth.auth.HTTPAuthResult;
import lol.petrik.pmcauth.auth.State;
import lol.petrik.pmcauth.auth.WSAuthResult;
import lol.petrik.pmcauth.chat.Formatter;
import lol.petrik.pmcauth.config.Config;
import lol.petrik.pmcauth.http.HTTPClient;
import lol.petrik.pmcauth.http.PlayerDataModel;
import lol.petrik.pmcauth.limbo.CustomCommandHandler;
import lol.petrik.pmcauth.limbo.CustomLimboConfig;
import lol.petrik.pmcauth.websocket.WSClient;
import lombok.Getter;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import ua.nanit.limbo.configuration.LimboConfig;
import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.CommandHandler;
import ua.nanit.limbo.server.LimboServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "pmcauth", name = "PMCAuth", version = BuildConstants.VERSION, authors = {"nemvince"})
public class PMCAuth {
  @Getter
  private static PMCAuth instance;
  private final LimboServer limbo;
  private final Config config;
  private final HTTPClient httpClient = new HTTPClient();

  @Getter
  private final ProxyServer server;

  @Getter
  private final String name;

  @Getter
  private final Path dataDirectory;
  @Getter
  private final Logger logger;
  @Getter
  private final ConcurrentHashMap<String, State> playerStates = new ConcurrentHashMap<>();
  private WSClient wsClient;

  @Inject
  public PMCAuth(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    instance = this;
    this.server = server;
    this.dataDirectory = dataDirectory;
    this.logger = logger;
    this.config = new Config(this);

    LimboConfig limboConfig = new CustomLimboConfig();
    CommandHandler<Command> limboCommandHandler = new CustomCommandHandler();

    this.limbo = new LimboServer(limboConfig, limboCommandHandler, getClass().getClassLoader());
    this.name = this.getClass().getAnnotation(Plugin.class).name();
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
    String remoteIp = player.getRemoteAddress().getAddress().getHostAddress();
    logger.info("Player {} is trying to connect.", player.getUsername());


    State state = playerStates.get(player.getUsername());
    if (state != null) {
      if (state.getIp() != null && !state.getIp().equals(remoteIp)) {
        logger.warn("Player {} is already authorized from a different IP.", player.getUsername());
        playerStates.remove(player.getUsername());
      } else if (state.getAuthorized()) {
        logger.info("Player {} is already authorized.", player.getUsername());
        if (server.getServer("paper").isEmpty()) {
          logger.error("Paper server is not present.");
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
          player.disconnect(Formatter.error("Szerverhiba!"));
        } else {
          event.setResult(ServerPreConnectEvent.ServerResult.allowed(server.getServer("paper").get()));
        }
        return;
      }
    } else {
      playerStates.put(player.getUsername(), new State(remoteIp));
    }

    if (server.getServer("limbo").isEmpty()) {
      logger.error("Limbo server is not present.");
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      player.disconnect(Formatter.error("Szerverhiba!"));
      return;
    }

    RegisteredServer limboServer = server.getServer("limbo").get();
    event.setResult(ServerPreConnectEvent.ServerResult.allowed(limboServer));
    logger.info("Player {} is now in limbo.", player.getUsername());

    Gson gson = new Gson();
    // send mc_username and IP to the server
    PlayerDataModel playerData = new PlayerDataModel(player.getUsername(), remoteIp);
    String json = gson.toJson(playerData);
    httpClient.post(config.getHttpUrl() + "/plugin/auth", json).thenAccept(response -> {
      String respBody = response.body();
      HTTPAuthResult result = gson.fromJson(respBody, HTTPAuthResult.class);
      player.sendMessage(Formatter.info("Kérlek a Discord szerveren küldd el a következő kódot: ").append(Formatter.copyToClipboard(result.code, result.code)));
      logger.info("Player {} has been sent a code: {}", player.getUsername(), result.code);
    });

    // start authorization
    new Thread(() -> handlePlayerAuthorization(player, event)).start();
  }

  public void handlePlayerAuthorization(Player player, ServerPreConnectEvent event) {
    State state = playerStates.get(player.getUsername());
    state.getLock().lock();
    try {
      if (!state.getCondition().await(600, TimeUnit.SECONDS)) { // Wait for 10 minutes
        logger.error("Timeout while waiting for player {} to be authorized", player.getUsername());
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.disconnect(Formatter.error("Authorization timeout!"));
        playerStates.remove(player.getUsername());
      } else {
        // check player status
        WSAuthResult status = playerStates.get(player.getUsername()).getResult();
        if (status == null) {
          logger.error("Error while waiting for player {} to be authorized", player.getUsername());
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
          player.disconnect(Formatter.error("Authorization error!"));
          playerStates.remove(player.getUsername());
          return;
        }

        if (Objects.equals(status.event, "failedAuthorization")) {
          logger.info("Player {} failed to authenticate: {}", player.getUsername(), status.reason);
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
          player.disconnect(Formatter.error(status.reason));
          playerStates.remove(player.getUsername());
        } else if (Objects.equals(status.event, "doneAuthorization")) {
          logger.info("Player {} successfully authenticated", player.getUsername());
          player.sendMessage(Formatter.info("Sikeresen azonosítottalak, jó játékot!"));
          playerStates.get(player.getUsername()).setAuthorized(true);
          if (server.getServer("paper").isEmpty()) {
            logger.error("Paper server is not present.");
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.disconnect(Formatter.error("Szerverhiba!"));
          }
          player.createConnectionRequest(server.getServer("paper").get()).fireAndForget();
        }
      }
    } catch (InterruptedException e) {
      logger.error("Error while waiting for player {} to be authorized: {}", player.getUsername(), e.getMessage());
    } finally {
      state.getLock().unlock();
    }
  }

  @Subscribe
  public void onPlayerDisconnect(DisconnectEvent event) {
    Player player = event.getPlayer();
    playerStates.remove(player.getUsername());
    logger.info("Player {} has been unauthorized.", player.getUsername());
  }

  public void onEnable() {
    ClientManager client = ClientManager.createClient();
    try {
      wsClient = new WSClient(this);
      logger.info("Connecting to websocket server: {}", config.getWsUrl());
      client.connectToServer(wsClient, new URI(config.getWsUrl()));
      wsClient.sendMessage("connect");
    } catch (Exception e) {
      logger.error("Error while connecting to websocket server: {}", e.getMessage());
      Util.die(server, this);
    }

    try {
      this.limbo.start();

      ServerInfo limboServerInfo = new ServerInfo("limbo", new InetSocketAddress("localhost", 60000));
      server.registerServer(limboServerInfo);
      logger.info("Limbo server started and registered");
    } catch (Exception e) {
      logger.error("Error while starting Limbo: {}", e.getMessage());
    }
    logger.info("{} enabled", this.name);
  }

  public void onDisable() {
    try {
      if (wsClient != null) {
        wsClient.sendMessage("disconnect");
        wsClient.onClose();
      }
    } catch (Exception e) {
      logger.error("Error while closing websocket connection: {}", e.getMessage());
    }

    try {
      this.limbo.stop();
    } catch (Exception e) {
      logger.error("Error while stopping Limbo: {}", e.getMessage());
    }
    logger.info("{} disabled", this.name);
  }
}