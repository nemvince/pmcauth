package lol.petrik.pmcauth;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lol.petrik.pmcauth.chat.Formatter;
import lol.petrik.pmcauth.http.HTTPAuthResult;
import lol.petrik.pmcauth.http.HTTPClient;
import lol.petrik.pmcauth.limbo.CustomCommandHandler;
import lol.petrik.pmcauth.limbo.CustomLimboConfig;
import lol.petrik.pmcauth.websocket.WSAuthResult;
import lol.petrik.pmcauth.websocket.WSClient;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import ua.nanit.limbo.configuration.LimboConfig;
import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.CommandHandler;
import ua.nanit.limbo.server.LimboServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Plugin(id = "pmcauth", name = "PMCAuth", version = BuildConstants.VERSION, authors = {"nemvince"})
public class PMCAuth {

  private final ProxyServer server;
  private final Logger logger;
  private final String name;
  private final ConcurrentHashMap<String, PlayerLock> waitingPlayers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, WSAuthResult> playerStatus = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> authorizedPlayers = new ConcurrentHashMap<>();
  private final HTTPClient httpClient = new HTTPClient();
  private final LimboServer limbo;
  private final PMCAuth instance;
  private WSClient wsClient;

  @Inject
  public PMCAuth(ProxyServer server, Logger logger) {
    this.instance = this;
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

  public ConcurrentHashMap<String, PlayerLock> getWaitingPlayers() {
    return waitingPlayers;
  }

  public ConcurrentHashMap<String, WSAuthResult> getPlayerStatus() {
    return playerStatus;
  }

  public Logger getLogger() {
    return logger;
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    this.onDisable();
  }

  @Subscribe
  public void onServerProxyPreConnectionEvent(ServerPreConnectEvent event) {
    Player player = event.getPlayer();
    logger.info("Player {} is trying to connect.", player.getUsername());

    if (authorizedPlayers.getOrDefault(player.getUsername(), false)) {
      logger.info("Player {} is already authorized.", player.getUsername());
      return;
    }

    if (server.getServer("limbo").isEmpty()) {
      logger.error("Limbo server is not present.");
      event.setResult(ServerPreConnectEvent.ServerResult.denied());
      return;
    }
    RegisteredServer limboServer = server.getServer("limbo").get();
    event.setResult(ServerPreConnectEvent.ServerResult.allowed(limboServer));
    logger.info("Player {} is now in limbo.", player.getUsername());

    // send http post
    Gson gson = new Gson();
    String json = gson.toJson(Collections.singletonMap("mc_username", player.getUsername()));
    httpClient.post("http://10.0.0.136:3000/plugin/auth", json).thenAccept(response -> {
      String respBody = response.body();
      HTTPAuthResult result = gson.fromJson(respBody, HTTPAuthResult.class);
      player.sendMessage(Formatter.info("Kérlek a Discord szerveren küldd el a következő kódot: " + result.code));
      logger.info("Player {} has been sent a code: {}", player.getUsername(), result.code);
    });

    // start authorization
    new Thread(() -> handlePlayerAuthorization(player, event)).start();
  }

  public void handlePlayerAuthorization(Player player, ServerPreConnectEvent event) {
    Lock lock = new ReentrantLock();
    Condition condition = lock.newCondition();
    waitingPlayers.put(player.getUsername(), new PlayerLock(lock, condition));
    lock.lock();
    try {
      if (!condition.await(600, TimeUnit.SECONDS)) { // Wait for 10 minutes
        logger.error("Timeout while waiting for player {} to be authorized", player.getUsername());
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.disconnect(Formatter.error("Authorization timeout!"));
      } else {
        // check player status
        WSAuthResult status = playerStatus.get(player.getUsername());
        if (Objects.equals(status.event, "failedAuthorization")) {
          logger.info("Player {} failed to authenticate: {}", player.getUsername(), status.reason);
          event.setResult(ServerPreConnectEvent.ServerResult.denied());
          player.disconnect(Formatter.error(status.reason));
        } else if (Objects.equals(status.event, "doneAuthorization")) {
          logger.info("Player {} successfully authenticated", player.getUsername());
          player.sendMessage(Formatter.info("Sikeresen azonosítottalak, jó játékot!"));
          authorizedPlayers.put(player.getUsername(), true);
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
      lock.unlock();
      waitingPlayers.remove(player.getUsername());
      playerStatus.remove(player.getUsername());
    }
  }

  @Subscribe
  public void onPlayerDisconnect(DisconnectEvent event) {
    Player player = event.getPlayer();
    authorizedPlayers.remove(player.getUsername());
    logger.info("Player {} has been unauthorized.", player.getUsername());
  }

  public void onLoad() {
    logger.info("{} loaded", this.name);
  }

  public void onEnable() {
    ClientManager client = ClientManager.createClient();
    try {
      wsClient = new WSClient(this);
      client.connectToServer(wsClient, new URI("ws://10.0.0.136:3001/ws"));
      wsClient.sendMessage("connect");
    } catch (Exception e) {
      logger.error("Error while connecting to websocket server: {}", e.getMessage());
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