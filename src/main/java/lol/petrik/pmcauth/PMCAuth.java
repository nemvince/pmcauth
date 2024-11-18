package lol.petrik.pmcauth;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lol.petrik.pmcauth.Chat.Formatter;
import lol.petrik.pmcauth.Limbo.CustomCommandHandler;
import lol.petrik.pmcauth.Limbo.CustomLimboConfig;
import lol.petrik.pmcauth.Websocket.WSClient;
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
    // { mc_username: "player.getUsername()" }
    String json = gson.toJson(Collections.singletonMap("mc_username", player.getUsername()));
    httpClient.post("http://10.0.0.136:3000/plugin/auth", json)
    player.sendMessage(Formatter.info("Szia, küldtem egy üzenetet Discordon, kérlek hagy jóvá a bélépést!"));

    // start authorization
    new Thread(() -> handlePlayerAuthorization(player, event, code)).start();
  }

  public void handlePlayerAuthorization(Player player, ServerPreConnectEvent event, String code) {
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
        } else {
          logger.info("Player {} successfully authenticated", player.getUsername());
          player.sendMessage(Formatter.info("Sikeresen azonosítottalak, most már beléphetsz!"));
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