package com.mipt.portal.service;

import com.mipt.portal.config.TelegramBotConfig;
import com.mipt.portal.entity.Announcement;
import com.mipt.portal.entity.Booking;
import com.mipt.portal.entity.User;
import com.mipt.portal.enums.AdStatus;
import com.mipt.portal.repository.AnnouncementRepository;
import com.mipt.portal.repository.BookingRepository;
import com.mipt.portal.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

  private static final String CALLBACK_CONFIRM_AD = "confirm_ad:";

  private final TelegramBotConfig config;
  private final UserRepository userRepository;
  private final AnnouncementRepository announcementRepository;
  private final BookingRepository bookingRepository;

  // Храним пользователей, которые ожидают ввода почты
  private final Map<Long, Boolean> waitingForEmail = new HashMap<>();

  public TelegramBot(TelegramBotConfig config,
      UserRepository userRepository,
      AnnouncementRepository announcementRepository,
      BookingRepository bookingRepository) {
    super(config.getToken());
    this.config = config;
    this.userRepository = userRepository;
    this.announcementRepository = announcementRepository;
    this.bookingRepository = bookingRepository;
  }

  @Override
  public String getBotUsername() {
    return config.getName();
  }

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasCallbackQuery()) {
      handleCallbackQuery(update);
      return;
    }

    if (update.hasMessage() && update.getMessage().hasText()) {
      Long chatId = update.getMessage().getChatId();
      String messageText = update.getMessage().getText();

      if (messageText.startsWith("/")) {
        handleCommand(chatId, messageText);
      } else if (waitingForEmail.getOrDefault(chatId, false)) {
        handleEmailInput(chatId, messageText);
      } else {
        sendMessage(chatId, "❓ Используй команды:\n/start — начать\n/my_ads — мои объявления");
      }
    }
  }

  private void handleCommand(Long chatId, String command) {
    switch (command) {
      case "/start" -> startCommand(chatId);
      case "/my_ads" -> showMyAds(chatId);
      default -> sendMessage(chatId, "❓ Неизвестная команда. Доступно: /start, /my_ads");
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
        response.append(i + 1).append(". *").append(escapeMarkdown(ad.getTitle())).append("*\n");
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
        response.append(i + 1).append(". *").append(escapeMarkdown(ad.getTitle())).append("*\n");
        response.append("   💰 ").append(ad.getPrice()).append(" ₽\n\n");
      }
    }

    sendMarkdownMessage(chatId, response.toString());
  }

  // Вызывается из планировщика — уведомляет о новых бронированиях
  public void notifyNewBookings() {
    List<Booking> pending = bookingRepository.findByNotificationSentAtIsNull();
    for (Booking booking : pending) {
      announcementRepository.findById(booking.getAnnouncementId()).ifPresent(ad -> {
        userRepository.findById(ad.getAuthorId()).ifPresent(seller -> {
          if (seller.getTelegramChatId() != null) {
            sendMessage(seller.getTelegramChatId(),
                "Твой товар «" + ad.getTitle() + "» забронирован на 24 часа!\n" +
                "Покупатель свяжется с тобой для выкупа.");
          }
        });
        userRepository.findById(booking.getBuyerId()).ifPresent(buyer -> {
          if (buyer.getTelegramChatId() != null) {
            sendMessage(buyer.getTelegramChatId(),
                "Ты забронировал «" + ad.getTitle() + "» за " + ad.getPrice() + " ₽.\n" +
                "У тебя есть 24 часа для выкупа — свяжись с продавцом!");
          }
        });
      });
      booking.setNotificationSentAt(Instant.now());
      bookingRepository.save(booking);
      log.info("Уведомление о бронировании отправлено для bookingId={}", booking.getId());
    }
  }

  // Вызывается из планировщика — проверяет объявления, не обновлявшиеся 30 дней
  public void checkAndNotifyOldAnnouncements() {
    archiveUnconfirmed();
    notifyStaleAds();
  }

  // Шаг 1: архивируем объявления, по которым уведомление отправлено, но подтверждение не пришло
  private void archiveUnconfirmed() {
    Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);

    List<Announcement> unconfirmed = announcementRepository.findByStatusAndNotifiedAtBefore(
        AdStatus.ACTIVE, oneDayAgo);

    Map<Long, List<Announcement>> adsByUser = unconfirmed.stream()
        .collect(Collectors.groupingBy(Announcement::getAuthorId));

    for (Map.Entry<Long, List<Announcement>> entry : adsByUser.entrySet()) {
      List<Announcement> ads = entry.getValue();

      for (Announcement ad : ads) {
        ad.setStatus(AdStatus.ARCHIVED);
        ad.setNotifiedAt(null);
        announcementRepository.save(ad);
        log.info("Объявление id={} переведено в архив (не подтверждено)", ad.getId());
      }

      userRepository.findById(entry.getKey()).ifPresent(user -> {
        if (user.getTelegramChatId() != null) {
          sendArchivedNotification(user.getTelegramChatId(), ads);
        }
      });
    }
  }

  // Шаг 2: отправляем уведомления по объявлениям, которые не обновлялись 30 дней и ещё не уведомлены
  private void notifyStaleAds() {
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

    List<Announcement> staleAds = announcementRepository.findByStatusAndUpdatedAtBefore(
        AdStatus.ACTIVE, thirtyDaysAgo).stream()
        .filter(ad -> ad.getNotifiedAt() == null)
        .collect(Collectors.toList());

    Map<Long, List<Announcement>> adsByUser = staleAds.stream()
        .collect(Collectors.groupingBy(Announcement::getAuthorId));

    for (Map.Entry<Long, List<Announcement>> entry : adsByUser.entrySet()) {
      Long userId = entry.getKey();
      List<Announcement> userAds = entry.getValue();

      // Проставляем время уведомления
      for (Announcement ad : userAds) {
        ad.setNotifiedAt(Instant.now());
        announcementRepository.save(ad);
      }

      userRepository.findById(userId).ifPresent(user -> {
        if (user.getTelegramChatId() != null) {
          sendRenewalRequest(user.getTelegramChatId(), userAds);
        }
      });
    }
  }

  private void sendArchivedNotification(Long chatId, List<Announcement> ads) {
    StringBuilder text = new StringBuilder("📦 Объявления переведены в архив:\n\n");
    for (Announcement ad : ads) {
      text.append("• ").append(ad.getTitle()).append(" — ").append(ad.getPrice()).append(" ₽\n");
    }
    text.append("\nЕсли хочешь восстановить — сделай это на портале.");
    sendMessage(chatId, text.toString());
  }

  private void sendRenewalRequest(Long chatId, List<Announcement> ads) {
    for (Announcement ad : ads) {
      sendSingleRenewalNotification(chatId, ad);
    }
  }

  private void sendSingleRenewalNotification(Long chatId, Announcement ad) {
    String text = "⚠️ *Объявление уходит в архив\\!*\n\n" +
        escapeMarkdownV2(ad.getTitle()) + "\n" +
        "💰 " + ad.getPrice() + " ₽\n" +
        "🕐 Не обновлялось более 30 дней\n\n" +
        "Подтверди, что оно ещё актуально, иначе уйдёт в архив\\.";

    InlineKeyboardButton button = InlineKeyboardButton.builder()
        .text("✅ Всё ещё актуально")
        .callbackData(CALLBACK_CONFIRM_AD + ad.getId())
        .build();

    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(List.of(button)))
        .build();

    SendMessage message = SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        .parseMode("MarkdownV2")
        .replyMarkup(keyboard)
        .build();

    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка отправки уведомления для объявления id={} chatId={}", ad.getId(), chatId, e);
    }
  }

  private void handleCallbackQuery(Update update) {
    String callbackId = update.getCallbackQuery().getId();
    String data = update.getCallbackQuery().getData();
    Long chatId = update.getCallbackQuery().getMessage().getChatId();
    Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

    if (data != null && data.startsWith(CALLBACK_CONFIRM_AD)) {
      String adIdStr = data.substring(CALLBACK_CONFIRM_AD.length());
      handleConfirmAd(callbackId, chatId, messageId, adIdStr);
    }
  }

  private void handleConfirmAd(String callbackId, Long chatId, Integer messageId, String adIdStr) {
    long adId;
    try {
      adId = Long.parseLong(adIdStr);
    } catch (NumberFormatException e) {
      answerCallback(callbackId, "Ошибка: некорректный ID объявления");
      return;
    }

    Optional<Announcement> adOpt = announcementRepository.findById(adId);
    if (adOpt.isEmpty()) {
      answerCallback(callbackId, "Объявление не найдено");
      return;
    }

    Announcement ad = adOpt.get();

    // Убедимся, что кнопку нажал именно владелец объявления
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty() || !userOpt.get().getId().equals(ad.getAuthorId())) {
      answerCallback(callbackId, "Нет доступа к этому объявлению");
      return;
    }

    ad.setUpdatedAt(Instant.now());
    ad.setNotifiedAt(null);
    announcementRepository.save(ad);

    // Убираем кнопку из сообщения, чтобы не подтверждали дважды
    removeButtonFromMessage(chatId, messageId, adId);

    answerCallback(callbackId, "✅ Объявление «" + truncate(ad.getTitle(), 40) + "» подтверждено!");
    log.info("Объявление id={} подтверждено пользователем chatId={}", adId, chatId);
  }

  private void answerCallback(String callbackId, String text) {
    AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
        .callbackQueryId(callbackId)
        .text(text)
        .showAlert(false)
        .build();
    try {
      execute(answer);
    } catch (TelegramApiException e) {
      log.error("Ошибка ответа на callback: {}", callbackId, e);
    }
  }

  // Убирает все кнопки из сообщения после подтверждения
  private void removeButtonFromMessage(Long chatId, Integer messageId, Long confirmedAdId) {
    try {
      EditMessageReplyMarkup edit = EditMessageReplyMarkup.builder()
          .chatId(chatId.toString())
          .messageId(messageId)
          .replyMarkup(InlineKeyboardMarkup.builder().keyboard(Collections.emptyList()).build())
          .build();
      execute(edit);
    } catch (TelegramApiException e) {
      log.warn("Не удалось обновить клавиатуру сообщения id={}: {}", confirmedAdId, e.getMessage());
    }
  }

  private void sendMessage(Long chatId, String text) {
    SendMessage message = SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        .build();
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка отправки сообщения chatId: {}", chatId, e);
    }
  }

  private void sendMarkdownMessage(Long chatId, String text) {
    SendMessage message = SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        .parseMode("Markdown")
        .build();
    try {
      execute(message);
    } catch (TelegramApiException e) {
      log.error("Ошибка отправки markdown-сообщения chatId: {}", chatId, e);
    }
  }

  private String formatDate(Instant instant) {
    if (instant == null) return "неизвестно";
    return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .format(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
  }

  // Экранирование для Markdown v1
  private String escapeMarkdown(String text) {
    if (text == null) return "";
    return text.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`");
  }

  // Экранирование для MarkdownV2
  private String escapeMarkdownV2(String text) {
    if (text == null) return "";
    return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])", "\\\\$1");
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
  }
}
