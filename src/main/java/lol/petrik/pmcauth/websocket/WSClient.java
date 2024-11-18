package lol.petrik.pmcauth.websocket;

import com.google.gson.Gson;
import jakarta.websocket.*;
import lol.petrik.pmcauth.PMCAuth;
import lol.petrik.pmcauth.PlayerLock;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

@ClientEndpoint
public class WSClient {
  private final PMCAuth pmcAuth;
  private final Logger logger;
  private Session session;

  public WSClient(PMCAuth pmcAuth) {
    this.pmcAuth = pmcAuth;
    this.logger = pmcAuth.getLogger();
  }

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
    logger.info("Connection opened");
  }

  @OnClose
  public void onClose() {
    logger.warn("Connection closed");
  }

  @OnMessage
  public void onMessage(String message) {
    Gson gson = new Gson();
    WSAuthResult obj = gson.fromJson(message, WSAuthResult.class);

    logger.info("Received message: {}: {}", obj.mc_username, obj.event);

    pmcAuth.getPlayerStatus().put(obj.mc_username, obj);
    PlayerLock playerLock = pmcAuth.getWaitingPlayers().get(obj.mc_username);
    if (playerLock != null) {
      Lock lock = playerLock.lock();
      Condition condition = playerLock.condition();
      lock.lock();
      try {
        condition.signal();
      } finally {
        lock.unlock();
      }
    }
  }

  public void sendMessage(Object message) throws IOException {
    if (session != null && session.isOpen()) {
      Gson gson = new Gson();
      session.getBasicRemote().sendText(gson.toJson(message));
    }
  }
}