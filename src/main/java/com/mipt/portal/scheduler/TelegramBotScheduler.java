package com.mipt.portal.scheduler;

import com.mipt.portal.service.TelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class TelegramBotScheduler {

  @Autowired
  private TelegramBot telegramBot;

  @Scheduled(cron = "0 0 23 * * *")
  public void checkOldAnnouncements() {
    log.info("Проверка объявлений старше 30 дней...");
    telegramBot.checkAndNotifyOldAnnouncements();
  }

  @Scheduled(fixedDelay = 60_000)
  public void checkBookingEvents() {
    telegramBot.notifyNewBookings();
    telegramBot.notifyCancelledBookings();
    telegramBot.notifyConfirmedBookings();
  }
}
