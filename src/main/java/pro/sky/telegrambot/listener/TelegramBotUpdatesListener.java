package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.entity.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private static final Pattern pattern = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");
    private final NotificationTaskRepository repository;
    @Autowired
    private TelegramBot telegramBot;

    public TelegramBotUpdatesListener(NotificationTaskRepository repository, TelegramBot telegramBot) {
        this.repository = repository;
        this.telegramBot = telegramBot;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);
            for (Update update1 : updates) {
                String messageText = update1.message().text();
                long chatId = update1.message().chat().id();
                String name = update1.message().chat().firstName();
                SendMessage message = new SendMessage(chatId, "Hello, " + name + ", glad to welcome you");
                if (messageText.equals("/start")) {
                    SendResponse response = telegramBot.execute(message);
                } else {
                    if (this.processNotificationMessage(chatId, messageText)) {
                        this.telegramBot.execute(new SendMessage(chatId, "Напоминалка создана"));
                    } else {
                        this.telegramBot.execute(new SendMessage(chatId, "Неверный формат сообщения"));
                    }
                }
            }

        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    public boolean processNotificationMessage(Long chatId, String message) {
        Matcher messageMatcher = pattern.matcher(message);
        if (!messageMatcher.matches()) {
            return false;
        }
        String stringDate = messageMatcher.group(1);
        String notificationText = messageMatcher.group(3);
        try {
            LocalDateTime notificationDate = LocalDateTime.parse(stringDate, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            NotificationTask notificationTask = new NotificationTask();
            notificationTask.setMessage(notificationText);
            notificationTask.setLocalDateTime(notificationDate);
            notificationTask.setNotificationChatId(chatId);
            repository.save(notificationTask);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void sendNotification() {
        List<NotificationTask> taskToNotify =
                this.repository.findByLocalDateTimeEquals(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        taskToNotify.forEach(task -> {
            this.telegramBot.execute(new SendMessage(task.getNotificationChatId(), task.getMessage()));
        });
        this.repository.deleteAll(taskToNotify);
    }

}