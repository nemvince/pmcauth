package lol.petrik.pmcauth;

public class WSAuthResult {
  public String event;
  public String mc_username;
  public String reason;

  public WSAuthResult(String event, String reason) {
    this.event = event;
    this.reason = reason;
  }
}
