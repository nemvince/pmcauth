package lol.petrik.pmcauth.limbo;

import com.google.gson.Gson;
import ua.nanit.limbo.configuration.LimboConfig;
import ua.nanit.limbo.server.data.BossBar;
import ua.nanit.limbo.server.data.InfoForwarding;
import ua.nanit.limbo.server.data.InfoForwarding.Type;
import ua.nanit.limbo.server.data.PingData;
import ua.nanit.limbo.server.data.Title;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

class CustomInfoForwarding {
  private Type type;
  private byte[] secretKey;

  public Type getType() {
    return type;
  }

  public void setType(String input) {
    type = Type.valueOf(input);
  }

  public void setSecretKey(byte[] input) {
    secretKey = input;
  }
}

public class CustomLimboConfig implements LimboConfig {
  private static PingData pingData;
  private static InfoForwarding infoForwarding;

  public CustomLimboConfig() {
    pingData = new PingData();
    pingData.setDescription("petrikmc");
    pingData.setVersion("PetrikMC");
    pingData.setProtocol(-1);

    CustomInfoForwarding customInfoForwarding = new CustomInfoForwarding();
    customInfoForwarding.setType("MODERN");
    customInfoForwarding.setSecretKey("EKBZyTyKWQeG".getBytes());
    Gson gson = new Gson();
    String gsonForward = gson.toJson(customInfoForwarding);
    infoForwarding = gson.fromJson(gsonForward, InfoForwarding.class);
  }

  @Override
  public int getDebugLevel() {
    return 1;
  }

  @Override
  public boolean isUseBrandName() {
    return true;
  }

  @Override
  public boolean isUseJoinMessage() {
    return false;
  }

  @Override
  public boolean isUseBossBar() {
    return false;
  }

  @Override
  public boolean isUseTitle() {
    return false;
  }

  @Override
  public boolean isUsePlayerList() {
    return false;
  }

  @Override
  public boolean isUseHeaderAndFooter() {
    return false;
  }

  @Override
  public String getBrandName() {
    return "PetrikMC";
  }

  @Override
  public String getJoinMessage() {
    return "";
  }

  @Override
  public BossBar getBossBar() {
    return null;
  }

  @Override
  public Title getTitle() {
    return null;
  }

  @Override
  public String getPlayerListUsername() {
    return "";
  }

  @Override
  public String getPlayerListHeader() {
    return "";
  }

  @Override
  public String getPlayerListFooter() {
    return "";
  }

  @Override
  public boolean isUseEpoll() {
    return false;
  }

  @Override
  public int getBossGroupSize() {
    return 0;
  }

  @Override
  public int getWorkerGroupSize() {
    return 0;
  }

  @Override
  public double getInterval() {
    return 0;
  }

  @Override
  public double getMaxPacketRate() {
    return 0;
  }

  @Override
  public SocketAddress getAddress() {
    return new InetSocketAddress("localhost", 60000);
  }

  @Override
  public int getMaxPlayers() {
    return 1000;
  }

  @Override
  public PingData getPingData() {
    return pingData;
  }

  @Override
  public String getDimensionType() {
    return "the_end";
  }

  @Override
  public int getGameMode() {
    return 0;
  }

  @Override
  public InfoForwarding getInfoForwarding() {
    return infoForwarding;
  }

  @Override
  public long getReadTimeout() {
    return 0;
  }
}