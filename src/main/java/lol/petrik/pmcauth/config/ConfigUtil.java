package lol.petrik.pmcauth.config;
import lol.petrik.pmcauth.PMCAuth;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ConfigUtil {
  public static File saveDefaultConfig(PMCAuth plugin, String configurationName) {
    return saveDefaultConfig(plugin.getClass().getClassLoader(), plugin.getDataDirectory().toFile(), configurationName, plugin.getLogger());
  }

  public static File saveDefaultConfig(ClassLoader classLoader, File configurationFolder, String configurationName, Logger logger) {
    try {
      if (!configurationFolder.exists() && !configurationFolder.mkdirs()) {
        throw new IOException("Failed to create configuration folder: " + configurationFolder);
      }
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
      logger.error("Failed to save default configuration file: {}", configurationName, e);
      return null;
    }
  }
}
