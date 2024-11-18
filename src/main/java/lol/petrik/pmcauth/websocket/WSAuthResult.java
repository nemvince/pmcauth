package lol.petrik.pmcauth.websocket;

public class WSAuthResult {
  public final String event;
  public String mc_username;
  public final String reason;

  public WSAuthResult(String event, String reason) {
    this.event = event;
    this.reason = reason;
  }
}
