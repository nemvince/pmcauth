package lol.petrik.pmcauth.http;

public class PlayerDataModel {
  private final String mc_username;
  private final String ip;

  public PlayerDataModel(String mc_username, String ip) {
    this.mc_username = mc_username;
    this.ip = ip;
  }
}
