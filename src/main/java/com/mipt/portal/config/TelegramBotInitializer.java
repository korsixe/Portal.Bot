package com.mipt.portal.config;

import com.mipt.portal.service.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
public class TelegramBotInitializer {

  @Autowired
  private TelegramBot telegramBot;

  @EventListener(ContextRefreshedEvent.class)
  public void init() {
    try {
      TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
      botsApi.registerBot(telegramBot);
      log.info("✅ Telegram бот успешно зарегистрирован!");
    } catch (TelegramApiException e) {
      log.error("❌ Ошибка регистрации Telegram бота: {}", e.getMessage());
    }
  }
}
