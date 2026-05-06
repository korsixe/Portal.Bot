package com.mipt.portal.service;

import com.mipt.portal.config.TelegramBotConfig;
import com.mipt.portal.dto.AnnouncementFilterDto;
import com.mipt.portal.entity.Announcement;
import com.mipt.portal.entity.Booking;
import com.mipt.portal.entity.User;
import com.mipt.portal.enums.AdStatus;
import com.mipt.portal.repository.AnnouncementRepository;
import com.mipt.portal.repository.BookingRepository;
import com.mipt.portal.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

  // Callback data
  private static final String CB_MENU         = "menu";
  private static final String CB_MY_ADS       = "my_ads";
  private static final String CB_BOOKINGS     = "bookings";
  private static final String CB_SEARCH       = "search";
  private static final String CB_UNLINK       = "unlink";
  private static final String CB_UNLINK_YES   = "unlink_confirm";
  private static final String CB_UNLINK_NO    = "unlink_cancel";
  private static final String CB_CONFIRM_AD   = "confirm_ad:";

  @Value("${portal.frontend.url}")
  private String frontendUrl;

  private final TelegramBotConfig config;
  private final UserRepository userRepository;
  private final AnnouncementRepository announcementRepository;
  private final BookingRepository bookingRepository;

  private final Map<Long, Boolean> waitingForEmail  = new HashMap<>();
  private final Map<Long, String>  pendingTgUsername = new HashMap<>();
  private final Map<Long, Boolean> waitingForSearch  = new HashMap<>();

  public TelegramBot(TelegramBotConfig config,
      DefaultBotOptions options,
      UserRepository userRepository,
      AnnouncementRepository announcementRepository,
      BookingRepository bookingRepository) {
    super(options, config.getToken());
    this.config = config;
    this.userRepository = userRepository;
    this.announcementRepository = announcementRepository;
    this.bookingRepository = bookingRepository;
  }

  @Override
  public String getBotUsername() {
    return config.getName();
  }

  // ─── Входящие события ────────────────────────────────────────────────────

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasCallbackQuery()) {
      handleCallback(update);
      return;
    }
    if (update.hasMessage() && update.getMessage().hasText()) {
      Long   chatId  = update.getMessage().getChatId();
      String text    = update.getMessage().getText();
      String tgUser  = update.getMessage().getFrom().getUserName();

      if (text.equals("/start")) {
        startCommand(chatId, tgUser);
      } else if (waitingForEmail.getOrDefault(chatId, false)) {
        handleEmailInput(chatId, text);
      } else if (waitingForSearch.getOrDefault(chatId, false)) {
        waitingForSearch.remove(chatId);
        performSearch(chatId, text);
      } else {
        sendMessage(chatId, "Используй /start или кнопки меню.");
      }
    }
  }

  // ─── /start ───────────────────────────────────────────────────────────────

  private void startCommand(Long chatId, String tgUsername) {
    Optional<User> existing = userRepository.findByTelegramChatId(chatId);

    if (existing.isPresent()) {
      User user = existing.get();
      if (tgUsername != null && !tgUsername.equals(user.getTelegramUsername())) {
        user.setTelegramUsername(tgUsername);
        userRepository.save(user);
      }
      sendMainMenu(chatId, "С возвращением, " + user.getName() + "!");
    } else {
      if (tgUsername != null) pendingTgUsername.put(chatId, tgUsername);
      sendMessage(chatId, "Привет! Введи корпоративную почту Физтеха (@phystech.edu):");
      waitingForEmail.put(chatId, true);
    }
  }

  // ─── Привязка email ───────────────────────────────────────────────────────

  private void handleEmailInput(Long chatId, String email) {
    if (!email.endsWith("@phystech.edu")) {
      sendMessage(chatId, "Нужна почта @phystech.edu. Попробуй ещё раз:");
      return;
    }
    Optional<User> userOpt = userRepository.findByEmail(email);
    if (userOpt.isEmpty()) {
      sendMessage(chatId, "Пользователь " + email + " не найден. Сначала зарегистрируйся на портале.");
      waitingForEmail.remove(chatId);
      pendingTgUsername.remove(chatId);
      return;
    }
    User user = userOpt.get();
    user.setTelegramChatId(chatId);
    String tgUsername = pendingTgUsername.remove(chatId);
    if (tgUsername != null) user.setTelegramUsername(tgUsername);
    userRepository.save(user);
    waitingForEmail.remove(chatId);
    sendMainMenu(chatId, "Привязка успешна! Привет, " + user.getName() + "!");
  }

  // ─── Главное меню (inline) ────────────────────────────────────────────────

  private void sendMainMenu(Long chatId, String header) {
    SendMessage msg = SendMessage.builder()
        .chatId(chatId.toString())
        .text(header + "\n\nЧто хочешь сделать?")
        .replyMarkup(buildMenuKeyboard())
        .build();
    try { execute(msg); } catch (TelegramApiException e) {
      log.error("sendMainMenu chatId={}", chatId, e);
    }
  }

  private InlineKeyboardMarkup buildMenuKeyboard() {
    return InlineKeyboardMarkup.builder()
        .keyboard(List.of(
            List.of(
                btn("📋 Мои объявления",  CB_MY_ADS),
                btn("🔒 Бронирования",    CB_BOOKINGS)
            ),
            List.of(
                btn("🔍 Поиск",           CB_SEARCH),
                btn("🔓 Отвязать аккаунт", CB_UNLINK)
            )
        ))
        .build();
  }

  // ─── Callbacks ────────────────────────────────────────────────────────────

  private void handleCallback(Update update) {
    String callbackId = update.getCallbackQuery().getId();
    String data       = update.getCallbackQuery().getData();
    Long   chatId     = update.getCallbackQuery().getMessage().getChatId();
    Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

    answerCallback(callbackId, "");

    if (data == null) return;

    switch (data) {
      case CB_MENU     -> editToMainMenu(chatId, messageId, "Главное меню");
      case CB_MY_ADS   -> showMyAds(chatId, messageId);
      case CB_BOOKINGS -> showMyBookings(chatId, messageId);
      case CB_SEARCH   -> {
        waitingForSearch.put(chatId, true);
        editText(chatId, messageId, "Введи поисковый запрос:", null);
      }
      case CB_UNLINK   -> showUnlinkConfirm(chatId, messageId);
      case CB_UNLINK_YES -> handleUnlinkConfirm(chatId, messageId);
      case CB_UNLINK_NO  -> editToMainMenu(chatId, messageId, "Отмена. Главное меню:");
      default -> {
        if (data.startsWith(CB_CONFIRM_AD)) {
          handleConfirmAd(chatId, messageId, data.substring(CB_CONFIRM_AD.length()));
        }
      }
    }
  }

  // ─── Мои объявления ───────────────────────────────────────────────────────

  private void showMyAds(Long chatId, Integer messageId) {
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty()) {
      editToMainMenu(chatId, messageId, "Аккаунт не привязан. Используй /start.");
      return;
    }
    User user = userOpt.get();
    List<Announcement> all = announcementRepository.findByAuthorId(user.getId());

    List<Announcement> active     = filter(all, AdStatus.ACTIVE);
    List<Announcement> moderation = filter(all, AdStatus.UNDER_MODERATION);
    List<Announcement> drafts     = filter(all, AdStatus.DRAFT);

    StringBuilder sb = new StringBuilder("📋 *Твои объявления*\n\n");

    sb.append("✅ *Активные:*\n");
    if (active.isEmpty()) {
      sb.append("Нет\n");
    } else {
      for (int i = 0; i < active.size(); i++) {
        Announcement ad = active.get(i);
        sb.append(i + 1).append(". *").append(esc(ad.getTitle())).append("*\n");
        sb.append("   💰 ").append(ad.getPrice()).append(" ₽  · ").append(formatDate(ad.getUpdatedAt())).append("\n\n");
      }
    }

    sb.append("⏳ *На модерации:*\n");
    if (moderation.isEmpty()) {
      sb.append("Нет\n");
    } else {
      for (int i = 0; i < moderation.size(); i++) {
        Announcement ad = moderation.get(i);
        sb.append(i + 1).append(". *").append(esc(ad.getTitle())).append("*\n");
        sb.append("   💰 ").append(ad.getPrice()).append(" ₽\n\n");
      }
    }

    sb.append("📝 *Черновики:*\n");
    if (drafts.isEmpty()) {
      sb.append("Нет\n");
    } else {
      for (int i = 0; i < drafts.size(); i++) {
        Announcement ad = drafts.get(i);
        sb.append(i + 1).append(". *").append(esc(ad.getTitle())).append("*\n");
        sb.append("   💰 ").append(ad.getPrice()).append(" ₽\n\n");
      }
    }

    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(
            List.of(btn("🔄 Обновить", CB_MY_ADS), btn("« Меню", CB_MENU))
        ))
        .build();

    editText(chatId, messageId, sb.toString(), keyboard, "Markdown");
  }

  // ─── Мои бронирования ────────────────────────────────────────────────────

  private void showMyBookings(Long chatId, Integer messageId) {
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty()) {
      editToMainMenu(chatId, messageId, "Аккаунт не привязан. Используй /start.");
      return;
    }
    User user = userOpt.get();
    List<Booking> active = bookingRepository.findAllByBuyerId(user.getId()).stream()
        .filter(b -> b.getCancelledAt() == null && b.getConfirmedAt() == null)
        .collect(Collectors.toList());

    if (active.isEmpty()) {
      editText(chatId, messageId, "У тебя нет активных бронирований.",
          InlineKeyboardMarkup.builder().keyboard(List.of(List.of(btn("« Меню", CB_MENU)))).build());
      return;
    }

    StringBuilder sb = new StringBuilder("🔒 *Мои бронирования*\n\n");
    Instant now = Instant.now();
    for (int i = 0; i < active.size(); i++) {
      Booking b = active.get(i);
      Optional<Announcement> adOpt = announcementRepository.findById(b.getAnnouncementId());
      String title = adOpt.map(a -> esc(a.getTitle())).orElse("Объявление удалено");
      String price = adOpt.map(a -> a.getPrice() + " ₽").orElse("—");

      long remaining = 24 * 60 - ChronoUnit.MINUTES.between(b.getCreatedAt(), now);
      String timeLeft = remaining <= 0 ? "истекает"
          : remaining < 60 ? "< " + remaining + " мин"
          : remaining / 60 + " ч " + (remaining % 60 > 0 ? remaining % 60 + " мин" : "");

      sb.append(i + 1).append(". *").append(title).append("*\n");
      sb.append("   💰 ").append(price).append("  ⏳ ").append(timeLeft).append("\n\n");
    }

    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(
            List.of(btn("🔄 Обновить", CB_BOOKINGS), btn("« Меню", CB_MENU))
        ))
        .build();

    editText(chatId, messageId, sb.toString(), keyboard, "Markdown");
  }

  // ─── Поиск ───────────────────────────────────────────────────────────────

  private void performSearch(Long chatId, String query) {
    AnnouncementFilterDto filter = new AnnouncementFilterDto();
    filter.setText(query);
    List<Announcement> results = announcementRepository.searchApproved(filter, "createdAt", "DESC");

    if (results.isEmpty()) {
      sendMessage(chatId, "По запросу «" + query + "» ничего не найдено.");
      return;
    }

    int shown = Math.min(results.size(), 5);
    StringBuilder sb = new StringBuilder("<b>🔍 " + escHtml(query) + "</b>");
    if (results.size() > shown)
      sb.append(" (показываю ").append(shown).append(" из ").append(results.size()).append(")");
    sb.append("\n\n");

    for (int i = 0; i < shown; i++) {
      Announcement ad = results.get(i);
      sb.append(i + 1).append(". <a href=\"").append(frontendUrl).append("/ad/").append(ad.getId()).append("\">")
          .append(escHtml(ad.getTitle())).append("</a>\n");
      sb.append("   💰 ").append(ad.getPrice()).append(" ₽");
      if (ad.getCondition() != null) sb.append(" · ").append(ad.getCondition().getDisplayName());
      sb.append("\n");
      if (ad.getCategory() != null)
        sb.append("   🏷 ").append(ad.getCategory().getDisplayName()).append("\n");
      if (ad.getLocation() != null && !ad.getLocation().isBlank())
        sb.append("   📍 ").append(escHtml(ad.getLocation())).append("\n");
      sb.append("\n");
    }

    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(
            List.of(btn("🔍 Новый поиск", CB_SEARCH), btn("« Меню", CB_MENU))
        ))
        .build();

    SendMessage msg = SendMessage.builder()
        .chatId(chatId.toString())
        .text(sb.toString())
        .parseMode("HTML")
        .replyMarkup(keyboard)
        .build();
    try { execute(msg); } catch (TelegramApiException e) {
      log.error("performSearch chatId={}", chatId, e);
    }
  }

  // ─── Отвязка аккаунта ────────────────────────────────────────────────────

  private void showUnlinkConfirm(Long chatId, Integer messageId) {
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty()) {
      editToMainMenu(chatId, messageId, "Аккаунт не привязан.");
      return;
    }
    User user = userOpt.get();
    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(List.of(
            btn("✅ Да, отвязать", CB_UNLINK_YES),
            btn("❌ Отмена",       CB_UNLINK_NO)
        )))
        .build();
    editText(chatId, messageId,
        "Отвязать аккаунт *" + esc(user.getEmail()) + "*?\nУведомления перестанут приходить.",
        keyboard, "Markdown");
  }

  private void handleUnlinkConfirm(Long chatId, Integer messageId) {
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty()) {
      editToMainMenu(chatId, messageId, "Аккаунт уже не привязан.");
      return;
    }
    User user = userOpt.get();
    String email = user.getEmail();
    user.setTelegramChatId(null);
    user.setTelegramUsername(null);
    userRepository.save(user);
    log.info("Аккаунт userId={} отвязан от chatId={}", user.getId(), chatId);

    SendMessage msg = SendMessage.builder()
        .chatId(chatId.toString())
        .text("Аккаунт *" + esc(email) + "* отвязан. Используй /start для повторной привязки.")
        .parseMode("Markdown")
        .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
        .build();
    try { execute(msg); } catch (TelegramApiException e) {
      log.error("handleUnlinkConfirm chatId={}", chatId, e);
    }
  }

  // ─── Подтверждение актуальности объявления ────────────────────────────────

  private void handleConfirmAd(Long chatId, Integer messageId, String adIdStr) {
    long adId;
    try { adId = Long.parseLong(adIdStr); }
    catch (NumberFormatException e) { return; }

    Optional<Announcement> adOpt = announcementRepository.findById(adId);
    if (adOpt.isEmpty()) {
      editText(chatId, messageId, "Объявление не найдено.", null);
      return;
    }
    Announcement ad = adOpt.get();
    Optional<User> userOpt = userRepository.findByTelegramChatId(chatId);
    if (userOpt.isEmpty() || !userOpt.get().getId().equals(ad.getAuthorId())) {
      editText(chatId, messageId, "Нет доступа.", null);
      return;
    }
    ad.setUpdatedAt(Instant.now());
    ad.setNotifiedAt(null);
    announcementRepository.save(ad);
    editText(chatId, messageId, "✅ Объявление «" + ad.getTitle() + "» подтверждено!", null);
    log.info("Объявление id={} подтверждено chatId={}", adId, chatId);
  }

  // ─── Планировщик: уведомления ────────────────────────────────────────────

  public void notifyNewBookings() {
    List<Booking> pending = bookingRepository.findByNotificationSentAtIsNull();
    for (Booking booking : pending) {
      announcementRepository.findById(booking.getAnnouncementId()).ifPresent(ad -> {
        Optional<User> sellerOpt = userRepository.findById(ad.getAuthorId());
        Optional<User> buyerOpt  = userRepository.findById(booking.getBuyerId());
        String sellerContact = sellerOpt.map(this::tgContact).orElse("продавец");
        String buyerContact  = buyerOpt.map(this::tgContact).orElse("покупатель");

        sellerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
            sendMessage(u.getTelegramChatId(),
                "🔒 Твой товар «" + ad.getTitle() + "» забронирован на 24 ч!\nПокупатель: " + buyerContact));
        buyerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
            sendMessage(u.getTelegramChatId(),
                "🔒 Ты забронировал «" + ad.getTitle() + "» за " + ad.getPrice() + " ₽ на 24 ч.\nПродавец: " + sellerContact));
      });
      booking.setNotificationSentAt(Instant.now());
      bookingRepository.save(booking);
      log.info("Уведомление о бронировании bookingId={}", booking.getId());
    }
  }

  public void notifyCancelledBookings() {
    List<Booking> cancelled = bookingRepository.findByCancelledAtIsNotNullAndCancelNotificationSentAtIsNull();
    for (Booking booking : cancelled) {
      Optional<Announcement> adOpt    = announcementRepository.findById(booking.getAnnouncementId());
      Optional<User>         sellerOpt = adOpt.flatMap(ad -> userRepository.findById(ad.getAuthorId()));
      Optional<User>         buyerOpt  = userRepository.findById(booking.getBuyerId());
      String adTitle       = adOpt.map(Announcement::getTitle).orElse("товар");
      String sellerContact = sellerOpt.map(this::tgContact).orElse("продавец");
      String buyerContact  = buyerOpt.map(this::tgContact).orElse("покупатель");

      sellerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
          sendMessage(u.getTelegramChatId(),
              "❌ Бронь на «" + adTitle + "» отменена.\nПокупатель: " + buyerContact + "\nТовар снова доступен."));
      buyerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
          sendMessage(u.getTelegramChatId(),
              "❌ Твоя бронь на «" + adTitle + "» отменена.\nПродавец: " + sellerContact));

      booking.setCancelNotificationSentAt(Instant.now());
      bookingRepository.save(booking);
      log.info("Уведомление об отмене брони bookingId={}", booking.getId());
    }
  }

  public void notifyConfirmedBookings() {
    List<Booking> confirmed = bookingRepository.findByConfirmedAtIsNotNullAndConfirmNotificationSentAtIsNull();
    for (Booking booking : confirmed) {
      announcementRepository.findById(booking.getAnnouncementId()).ifPresent(ad -> {
        Optional<User> sellerOpt = userRepository.findById(ad.getAuthorId());
        Optional<User> buyerOpt  = userRepository.findById(booking.getBuyerId());
        String sellerContact = sellerOpt.map(this::tgContact).orElse("продавец");
        String buyerContact  = buyerOpt.map(this::tgContact).orElse("покупатель");

        sellerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
            sendMessage(u.getTelegramChatId(),
                "🎉 Продажа «" + ad.getTitle() + "» за " + ad.getPrice() + " ₽ подтверждена!\nПокупатель: " + buyerContact));
        buyerOpt.filter(u -> u.getTelegramChatId() != null).ifPresent(u ->
            sendMessage(u.getTelegramChatId(),
                "🎉 Покупка «" + ad.getTitle() + "» за " + ad.getPrice() + " ₽ подтверждена!\nПродавец: " + sellerContact));
      });
      booking.setConfirmNotificationSentAt(Instant.now());
      bookingRepository.save(booking);
      log.info("Уведомление о подтверждении bookingId={}", booking.getId());
    }
  }

  public void checkAndNotifyOldAnnouncements() {
    archiveUnconfirmed();
    notifyStaleAds();
  }

  private void archiveUnconfirmed() {
    Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    List<Announcement> unconfirmed = announcementRepository.findByStatusAndNotifiedAtBefore(AdStatus.ACTIVE, oneDayAgo);
    Map<Long, List<Announcement>> byUser = unconfirmed.stream().collect(Collectors.groupingBy(Announcement::getAuthorId));

    for (Map.Entry<Long, List<Announcement>> entry : byUser.entrySet()) {
      entry.getValue().forEach(ad -> {
        ad.setStatus(AdStatus.ARCHIVED);
        ad.setNotifiedAt(null);
        announcementRepository.save(ad);
        log.info("Объявление id={} в архив", ad.getId());
      });
      userRepository.findById(entry.getKey())
          .filter(u -> u.getTelegramChatId() != null)
          .ifPresent(u -> sendArchivedNotification(u.getTelegramChatId(), entry.getValue()));
    }
  }

  private void notifyStaleAds() {
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    List<Announcement> stale = announcementRepository.findByStatusAndUpdatedAtBefore(AdStatus.ACTIVE, thirtyDaysAgo)
        .stream().filter(ad -> ad.getNotifiedAt() == null).collect(Collectors.toList());

    Map<Long, List<Announcement>> byUser = stale.stream().collect(Collectors.groupingBy(Announcement::getAuthorId));
    for (Map.Entry<Long, List<Announcement>> entry : byUser.entrySet()) {
      entry.getValue().forEach(ad -> { ad.setNotifiedAt(Instant.now()); announcementRepository.save(ad); });
      userRepository.findById(entry.getKey())
          .filter(u -> u.getTelegramChatId() != null)
          .ifPresent(u -> entry.getValue().forEach(ad -> sendRenewalNotification(u.getTelegramChatId(), ad)));
    }
  }

  private void sendArchivedNotification(Long chatId, List<Announcement> ads) {
    StringBuilder text = new StringBuilder("📦 Объявления переведены в архив:\n\n");
    ads.forEach(ad -> text.append("• ").append(ad.getTitle()).append(" — ").append(ad.getPrice()).append(" ₽\n"));
    text.append("\nВосстановить можно на портале.");
    sendMessage(chatId, text.toString());
  }

  private void sendRenewalNotification(Long chatId, Announcement ad) {
    String text = "⚠️ *Объявление уходит в архив\\!*\n\n"
        + escV2(ad.getTitle()) + "\n💰 " + ad.getPrice() + " ₽\n"
        + "🕐 Не обновлялось более 30 дней\n\nПодтверди актуальность\\.";

    InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
        .keyboard(List.of(List.of(btn("✅ Всё ещё актуально", CB_CONFIRM_AD + ad.getId()))))
        .build();

    SendMessage msg = SendMessage.builder()
        .chatId(chatId.toString())
        .text(text)
        .parseMode("MarkdownV2")
        .replyMarkup(keyboard)
        .build();
    try { execute(msg); } catch (TelegramApiException e) {
      log.error("sendRenewalNotification adId={} chatId={}", ad.getId(), chatId, e);
    }
  }

  // ─── Утилиты: отправка/редактирование ────────────────────────────────────

  private void editToMainMenu(Long chatId, Integer messageId, String header) {
    editText(chatId, messageId, header + "\n\nЧто хочешь сделать?", buildMenuKeyboard());
  }

  private void editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
    editText(chatId, messageId, text, keyboard, null);
  }

  private void editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard, String parseMode) {
    EditMessageText edit = EditMessageText.builder()
        .chatId(chatId.toString())
        .messageId(messageId)
        .text(text)
        .replyMarkup(keyboard)
        .build();
    if (parseMode != null) edit.setParseMode(parseMode);
    try { execute(edit); } catch (TelegramApiException e) {
      log.error("editText chatId={} messageId={}", chatId, messageId, e);
    }
  }

  private void sendMessage(Long chatId, String text) {
    SendMessage msg = SendMessage.builder().chatId(chatId.toString()).text(text).build();
    try { execute(msg); } catch (TelegramApiException e) {
      log.error("sendMessage chatId={}", chatId, e);
    }
  }

  private void answerCallback(String id, String text) {
    try {
      execute(AnswerCallbackQuery.builder().callbackQueryId(id).text(text).showAlert(false).build());
    } catch (TelegramApiException e) {
      log.error("answerCallback id={}", id, e);
    }
  }

  private static InlineKeyboardButton btn(String text, String data) {
    return InlineKeyboardButton.builder().text(text).callbackData(data).build();
  }

  // ─── Утилиты: текст ───────────────────────────────────────────────────────

  private String tgContact(User user) {
    String name = user.getTelegramUsername() != null ? "@" + user.getTelegramUsername() : user.getName();
    return user.getEmail() != null ? name + " (" + user.getEmail() + ")" : name;
  }

  private String formatDate(Instant instant) {
    if (instant == null) return "—";
    return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .format(java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()));
  }

  private List<Announcement> filter(List<Announcement> list, AdStatus status) {
    return list.stream().filter(a -> a.getStatus() == status).collect(Collectors.toList());
  }

  private String esc(String text) {
    if (text == null) return "";
    return text.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`");
  }

  private String escV2(String text) {
    if (text == null) return "";
    return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])", "\\\\$1");
  }

  private String escHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
