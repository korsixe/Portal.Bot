package com.mipt.portal.service;

import com.mipt.portal.config.TelegramBotConfig;
import com.mipt.portal.entity.Announcement;
import com.mipt.portal.entity.User;
import com.mipt.portal.enums.AdStatus;
import com.mipt.portal.repository.AnnouncementRepository;
import com.mipt.portal.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

  private final TelegramBotConfig config;
  private final UserRepository userRepository;
  private final AnnouncementRepository announcementRepository;

  // Храним пользователей, которые ожидают ввода почты
  private final Map<Long, Boolean> waitingForEmail = new HashMap<>();

  public TelegramBot(TelegramBotConfig config,
      UserRepository userRepository,
      AnnouncementRepository announcementRepository) {
    super(config.getToken());
    this.config = config;
    this.userRepository = userRepository;
    this.announcementRepository = announcementRepository;
  }

  @Override
  public String getBotUsername() {
    return config.getName();
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      Long chatId = update.getMessage().getChatId();
      String messageText = update.getMessage().getText();

      if (messageText.startsWith("/")) {
        handleCommand(chatId, messageText);
      } else if (waitingForEmail.getOrDefault(chatId, false)) {
        handleEmailInput(chatId, messageText);
      } else {
        sendMessage(chatId, "❓ Используй команды:\n/start - начать\n/my_ads - мои объявления");
      }
    }
  }

  private void handleCommand(Long chatId, String command) {
    switch (command) {
      case "/start":
        startCommand(chatId);
        break;
      case "/my_ads":
        showMyAds(chatId);
        break;
      default:
        sendMessage(chatId, "❓ Неизвестная команда. Доступно: /start, /my_ads");
    }
  }

  private void startCommand(Long chatId) {
    Optional<User> existingUser = userRepository.findByTelegramChatId(chatId);

    if (existingUser.isPresent()) {
      User user = existingUser.get();
      sendMessage(chatId, "С возвращением, " + user.getName() + "!\nИспользуй /my_ads для просмотра объявлений.");
    } else {
      sendMessage(chatId, "Привет! Введи свою корпоративную почту Физтеха (@phystech.edu):");
      waitingForEmail.put(chatId, true);
    }
  }

  private void handleEmailInput(Long chatId, String email) {
    if (!email.endsWith("@phystech.edu")) {
      sendMessage(chatId, "Нужна почта @phystech.edu. Попробуй ещё раз:");
      return;
    }

    Optional<User> userOpt = userRepository.findByEmail(email);

    if (userOpt.isEmpty()) {
      sendMessage(chatId, "Пользователь с почтой " + email + " не найден.\nСначала зарегистрируйся на портале.");
      waitingForEmail.remove(chatId);
      return;
    }

    User user = userOpt.get();
    user.setTelegramChatId(chatId);
    userRepository.save(user);

    waitingForEmail.remove(chatId);
    sendMessage(chatId, "Привязка успешна! Привет, " + user.getName() + "!\nИспользуй /my_ads");
  }

  private void showMyAds(Long chatId) {
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);

    if (userOpt.isEmpty()) {
      sendMessage(chatId, "Аккаунт не привязан. Используй /start");
      return;
    }

    User user = userOpt.get();
    List<Announcement> userAds = announcementRepository.findByAuthorId(user.getId());

    List<Announcement> activeAds = userAds.stream()
        .filter(ad -> ad.getStatus() == AdStatus.ACTIVE)
        .collect(Collectors.toList());

    List<Announcement> draftAds = userAds.stream()
        .filter(ad -> ad.getStatus() == AdStatus.DRAFT)
        .collect(Collectors.toList());

    StringBuilder response = new StringBuilder("📋 *Твои объявления*\n\n");

    response.append("✅ *Активные:*\n");
    if (activeAds.isEmpty()) {
      response.append("Нет активных объявлений\n");
    } else {
      for (int i = 0; i < activeAds.size(); i++) {
        Announcement ad = activeAds.get(i);
        response.append(i + 1).append(". *").append(ad.getTitle()).append("*\n");
        response.append("   💰 ").append(ad.getPrice()).append(" ₽\n");
        response.append("   Обновлено: ").append(formatDate(ad.getUpdatedAt())).append("\n\n");
      }
    }

    response.append("📝 *Черновики:*\n");
    if (draftAds.isEmpty()) {
      response.append("Нет черновиков\n");
    } else {
      for (int i = 0; i < draftAds.size(); i++) {
        Announcement ad = draftAds.get(i);
        response.append(i + 1).append(". *").append(ad.getTitle()).append("*\n");
        response.append("   💰 ").append(ad.getPrice()).append(" ₽\n\n");
      }
    }

    sendMessage(chatId, response.toString());
  }

  // Метод для проверки старых объявлений (вызывается из планировщика)
  public void checkAndNotifyOldAnnouncements() {
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

    List<Announcement> oldActiveAds = announcementRepository.findByStatusAndUpdatedAtBefore(
        AdStatus.ACTIVE, thirtyDaysAgo);

    Map<Long, List<Announcement>> adsByUser = oldActiveAds.stream()
        .collect(Collectors.groupingBy(Announcement::getAuthorId));

    for (Map.Entry<Long, List<Announcement>> entry : adsByUser.entrySet()) {
      Long userId = entry.getKey();
      List<Announcement> userAds = entry.getValue();

      userRepository.findById(userId).ifPresent(user -> {
        if (user.getTelegramChatId() != null) {
          sendRenewalRequest(user.getTelegramChatId(), userAds);
        }
      });
    }
  }

  private void sendRenewalRequest(Long chatId, List<Announcement> ads) {
    StringBuilder message = new StringBuilder("⚠️ *Объявления требуют подтверждения!*\n\n");
    message.append("Эти объявления не обновлялись более 30 дней:\n\n");

    for (int i = 0; i < Math.min(ads.size(), 5); i++) {
      Announcement ad = ads.get(i);
      message.append(i + 1).append(". ").append(ad.getTitle())
          .append(" - ").append(ad.getPrice()).append(" ₽\n");
    }

    message.append("\nЧтобы подтвердить актуальность, обнови объявление на портале.");

    sendMessage(chatId, message.toString());
  }

  private void sendMessage(Long chatId, String text) {
    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText(text);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка отправки сообщения chatId: {}", chatId, e);
    }
  }

  private String formatDate(Instant instant) {
    if (instant == null) return "неизвестно";
    return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .format(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
  }
}