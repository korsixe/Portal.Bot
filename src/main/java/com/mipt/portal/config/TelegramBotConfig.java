package com.mipt.portal.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.DefaultBotOptions;

@Data
@Configuration
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotConfig {
  private String name;
  private String token;

  @Bean
  public DefaultBotOptions botOptions(
      @Value("${telegram.proxy.host:}") String proxyHost,
      @Value("${telegram.proxy.port:0}") int proxyPort) {
    DefaultBotOptions options = new DefaultBotOptions();
    if (!proxyHost.isBlank()) {
      options.setProxyHost(proxyHost);
      options.setProxyPort(proxyPort);
      options.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
    }
    return options;
  }
}
