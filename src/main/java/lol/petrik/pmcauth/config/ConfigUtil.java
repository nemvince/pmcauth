package lol.petrik.pmcauth.config;
import lol.petrik.pmcauth.PMCAuth;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ConfigUtil {
  public static File saveDefaultConfig(PMCAuth plugin, String configurationName) {
    return saveDefaultConfig(plugin.getClass().getClassLoader(), plugin.getDataDirectory().toFile(), configurationName);
  }

  public static File saveDefaultConfig(ClassLoader classLoader, File configurationFolder, String configurationName) {
    try {
      configurationFolder.mkdirs();
      File configurationFile = new File(configurationFolder, configurationName);
      if (!configurationFile.exists()) {
        try (InputStream inputStream = classLoader.getResourceAsStream(configurationName)) {
          if (inputStream == null)
            throw new IllegalArgumentException("Configuration with name " + configurationName + " not found in resources!");
          Files.copy(inputStream, configurationFile.toPath());
        }
      }
      return configurationFile;
    } catch(IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
