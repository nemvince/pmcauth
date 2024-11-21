package lol.petrik.pmcauth;

import com.velocitypowered.api.proxy.ProxyServer;
import lol.petrik.pmcauth.chat.Formatter;

public class Util {
  public static void die(ProxyServer server, PMCAuth instance) {
    instance.getLogger().error("Goodbye, cruel world!");
    // Shut down server since we're responsible for authentication
    server.shutdown(Formatter.error("Server is shutting down due to an error in PMCAuth"));
  }
}
