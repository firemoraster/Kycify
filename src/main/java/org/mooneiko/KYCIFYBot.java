package org.mooneiko;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KYCIFYBot extends TelegramLongPollingBot {
    private final Map<Long, String> userLanguages = new HashMap<>();
    private final Map<Long, List<String>> userCart = new HashMap<>();
    private final Map<Long, Integer> userOrdersCount = new HashMap<>();
    private final List<Long> ADMIN_IDS = List.of(395336816L, 7210588865L, 1154426750L);

    // Зберігаємо тимчасові дані про замовлення, поки користувач не введе кількість (якщо вона > 5)
    private final Map<Long, PendingOrder> pendingOrders = new HashMap<>();

    // Мапа для збереження конфігурації цін за біржами та країнами.
    // Ключ зовнішнього мапа — назва біржі (нижнім регістром),
    // внутрішня мапа: ключ — назва країни (нижнім регістром), значення — ціна.
    private final Map<String, Map<String, String>> priceConfig = new HashMap<>();

    // Клас для збереження даних замовлення перед підтвердженням кількості
    private static class PendingOrder {
        String exchange;
        String country;
        String price;
        String language;

        public PendingOrder(String exchange, String country, String price, String language) {
            this.exchange = exchange;
            this.country = country;
            this.price = price;
            this.language = language;
        }
    }

    // Конструктор – ініціалізація цін за замовчуванням
    public KYCIFYBot() {
        // Ініціалізація цін для біржі Bybit
        Map<String, String> bybitPrices = new HashMap<>();
        bybitPrices.put("bangladesh", "4.5$");
        bybitPrices.put("indonesia", "5.5$");
        bybitPrices.put("philippines", "8$ (onfido)");
        bybitPrices.put("pakistan", "9$");
        bybitPrices.put("egypt", "10$ (onfido, data)");
        bybitPrices.put("armenia", "18$ (onfido, sumsub)");
        bybitPrices.put("colombia", "11$ (onfido)");
        priceConfig.put("bybit", bybitPrices);
    }

    @Override
    public void onUpdateReceived(Update update) {
        long chatId = 0;
        // Обробка вхідних повідомлень
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText().trim();

                // Якщо повідомлення від адміністратора і це команда для зміни цін, обробляємо її
                if (messageText.startsWith("/setprice") && ADMIN_IDS.contains(update.getMessage().getFrom().getId())) {
                    handleSetPriceCommand(chatId, messageText);
                    return;
                }

                // Якщо у користувача є незавершене замовлення і повідомлення не є командою (не починається з "/")
                if (!messageText.startsWith("/") && pendingOrders.containsKey(chatId)) {
                    try {
                        int quantity = Integer.parseInt(messageText);
                        if (quantity <= 0) {
                            sendMessage(chatId, "Кількість має бути більше нуля. Будь ласка, введіть коректне число.");
                        } else {
                            finalizeOrder(chatId, quantity, pendingOrders.get(chatId));
                            pendingOrders.remove(chatId);
                        }
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Будь ласка, введіть число.");
                    }
                    return; // Якщо було оброблено введення кількості, більше нічого не робимо.
                }

                if (messageText.equals("/start")) {
                    sendLanguageSelection(chatId);
                }
                // Інші текстові команди можна обробляти тут...
            } else if (update.getMessage().hasPhoto()) {
                String userName = update.getMessage().getFrom().getUserName();
                String userText = update.getMessage().getCaption();
                String photoId = update.getMessage().getPhoto()
                        .get(update.getMessage().getPhoto().size() - 1).getFileId();
                sendPhotoToAdmins(chatId, userName, photoId, userText);
            }
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();

            // Обробка вибору кількості із інлайн-клавіатури (наприклад, "quantity_3")
            if (callbackData.startsWith("quantity_")) {
                String quantityStr = callbackData.substring("quantity_".length());
                try {
                    int quantity = Integer.parseInt(quantityStr);
                    PendingOrder pending = pendingOrders.get(chatId);
                    if (pending != null) {
                        finalizeOrder(chatId, quantity, pending);
                        pendingOrders.remove(chatId);
                    } else {
                        sendMessage(chatId, "❌ Виникла помилка. Спробуйте ще раз.");
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Невірне число.");
                }
                return;
            }
            // Якщо обрана кнопка для введення вручну кількості
            if (callbackData.equals("quantity_manual")) {
                String manualInputPrompt = switch (userLanguages.getOrDefault(chatId, "uk")) {
                    case "ru" -> "Пожалуйста, введите количество (число больше 5) в чате:";
                    case "en" -> "Please enter the quantity (number greater than 5) in chat:";
                    default -> "Будь ласка, введіть кількість (число більше за 5) у чаті:";
                };
                sendMessage(chatId, manualInputPrompt);
                return;
            }

            handleCallbackQuery(callbackData, chatId);
        }
    }

    // Метод обробки команди адміністратора для зміни ціни
    private void handleSetPriceCommand(long chatId, String messageText) {
        // Формат: /setprice <exchange> <country> <price>
        String[] parts = messageText.split("\\s+");
        if (parts.length < 4) {
            sendMessage(chatId, "Використання: /setprice <exchange> <country> <price>");
            return;
        }
        String exchange = parts[1].toLowerCase();
        String country = parts[2].toLowerCase();
        StringBuilder priceBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            priceBuilder.append(parts[i]);
            if (i < parts.length - 1) {
                priceBuilder.append(" ");
            }
        }
        String price = priceBuilder.toString();

        Map<String, String> exchangePrices = priceConfig.computeIfAbsent(exchange, k -> new HashMap<>());
        exchangePrices.put(country, price);
        sendMessage(chatId, String.format("Ціна оновлена: %s - %s = %s", exchange, country, price));
    }

    // Допоміжний метод для отримання ціни з конфігураційного мапа
    private String getPrice(String exchange, String country) {
        Map<String, String> exchangePrices = priceConfig.get(exchange.toLowerCase());
        if (exchangePrices != null && exchangePrices.containsKey(country.toLowerCase())) {
            return exchangePrices.get(country.toLowerCase());
        }
        return "N/A";
    }

    private void sendLanguageSelection(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83C\uDF10 Оберіть мову для комфортного користування ботом:");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton("\uD83C\uDDFA\uD83C\uDDE6 Українська", "ukrainian"));
        row1.add(createInlineKeyboardButton("\uD83C\uDDF7\uD83C\uDDFA Русский", "russian"));
        row1.add(createInlineKeyboardButton("\uD83C\uDDEC\uD83C\uDDE7 English", "english"));

        keyboardButtons.add(row1);
        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMainMenu(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String text = switch (language) {
            case "ru" -> "Выберите нужную категорию ниже:";
            case "en" -> "Choose the desired category below:";
            default -> "Виберіть потрібну категорію нижче:";
        };
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        // Рядок 1: Каталог і Мій профіль
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDCCB Каталог";
                    case "en" -> "\uD83D\uDCCB Catalog";
                    default -> "\uD83D\uDCCB Каталог";
                },
                "catalog"));
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDC64 Мой профиль";
                    case "en" -> "\uD83D\uDC64 My Profile";
                    default -> "\uD83D\uDC64 Мій профіль";
                },
                "profile"));

        // Рядок 2: Питання/Відповіді і Кошик
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\u2753 Вопросы/Ответы";
                    case "en" -> "\u2753 FAQ";
                    default -> "\u2753 Питання/Відповіді";
                },
                "faq"));
        row2.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDED2 Корзина";
                    case "en" -> "\uD83D\uDED2 Cart";
                    default -> "\uD83D\uDED2 Кошик";
                },
                "cart"));

        // Рядок 3: Відгуки / Feedback
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineKeyboardUrlButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDCAC Отзывы";
                    case "en" -> "\uD83D\uDCAC Feedback";
                    default -> "\uD83D\uDCAC Відгуки";
                },
                "https://t.me/your_feedback_chat"));

        keyboardButtons.add(row1);
        keyboardButtons.add(row2);
        keyboardButtons.add(row3);
        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для створення кнопки з посиланням
    private InlineKeyboardButton createInlineKeyboardUrlButton(String text, String url) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setUrl(url);
        return button;
    }

    private void sendCatalogMenu(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String text = switch (language) {
            case "ru" -> "\uD83C\uDF10 Выберите биржу для верификации:";
            case "en" -> "\uD83C\uDF10 Choose an exchange for verification:";
            default -> "\uD83C\uDF10 Оберіть біржу для верифікації:";
        };
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        // При виборі біржі, назви бірж залишаються незмінними
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton("Binance", "exchange_binance"));
        row1.add(createInlineKeyboardButton("Bybit", "exchange_bybit"));
        row1.add(createInlineKeyboardButton("OKX", "exchange_okx"));

        keyboardButtons.add(row1);
        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Відправляє користувачу фінальні дані для оплати.
     * Повідомлення містить:
     *  - Суму до оплати
     *  - Реквізити для оплати (Aptos і BEP-20)
     *  - Інструкції:
     *      1. Оплатіть зазначену суму.
     *      2. В одному повідомленні відправте:
     *         - Скріншот оплати (з усіма деталями транзакції);
     *         - Ваші ідентифікаційні дані (ID або username);
     *         - Заповнену форму з даними проксі:
     *              • **Protocol:** HTTP/SOCKS5
     *              • **Server:** *proxy
     *              • **Port:** *port
     *              • **Login:** KYCIFY
     *              • **Password:** 123456
     *         - Посилання на верифікацію.
     *
     * ❗️ **УВАГА:** Усі дані мають бути відправлені одним повідомленням!
     * Після цього надсилаються фото з реквізитами (кошельками).
     */
    private void sendPaymentDetails(long chatId, String language) {
        List<String> cart = userCart.get(chatId);
        double totalSum = 0;
        if (cart != null) {
            for (String item : cart) {
                Pattern totalPattern = Pattern.compile("Total:\\s*([0-9]+(\\.[0-9]+)?)\\$");
                Matcher matcher = totalPattern.matcher(item);
                if (matcher.find()) {
                    try {
                        double itemTotal = Double.parseDouble(matcher.group(1));
                        totalSum += itemTotal;
                    } catch (NumberFormatException e) {
                        // Пропускаємо помилки парсингу
                    }
                }
            }
        }
        String totalSumFormatted = String.format("%.2f", totalSum);

        String paymentDetailsText;
        switch (language) {
            case "ru":
                paymentDetailsText =
                        "💳 **Сумма к оплате:** " + totalSumFormatted + "$\n\n" +
                                "🔹 **Реквизиты для оплаты:**\n" +
                                "**Aptos:**\n" +
                                "```\n0x77a6b7eef25f310a2963fac60ab56b8b2e95b51405f2c120d58b60784b91acd5\n```\n" +
                                "**BEP-20:**\n" +
                                "```\n0xcde13392f65cc1d41536a9c84b2a362ef12f9060\n```\n\n" +
                                "🔹 **Инструкции для подтверждения оплаты:**\n" +
                                "1. Оплатите указанную сумму.\n" +
                                "2. В одном сообщении отправьте:\n" +
                                "   - Скриншот оплаты (со всеми деталями транзакции);\n" +
                                "   - Ваши идентификационные данные (ID или username);\n" +
                                "   - Заполненную форму с данными прокси:\n" +
                                "     • **Protocol:** HTTP/SOCKS5\n" +
                                "     • **Server:** *proxy\n" +
                                "     • **Port:** *port\n" +
                                "     • **Login:** KYCIFY\n" +
                                "     • **Password:** 123456\n" +
                                "  3. Ссылку на верификацию.\n\n" +
                                "❗️ **ВНИМАНИЕ:** Все данные должны быть отправлены одним сообщением!";
                break;
            case "en":
                paymentDetailsText =
                        "💳 **Total amount due:** " + totalSumFormatted + "$\n\n" +
                                "🔹 **Payment Details:**\n" +
                                "**Aptos:**\n" +
                                "```\n0x77a6b7eef25f310a2963fac60ab56b8b2e95b51405f2c120d58b60784b91acd5\n```\n" +
                                "**BEP-20:**\n" +
                                "```\n0xcde13392f65cc1d41536a9c84b2a362ef12f9060\n```\n\n" +
                                "🔹 **Instructions for Payment Confirmation:**\n" +
                                "1. Please pay the specified amount.\n" +
                                "2. In a single message, send:\n" +
                                "   - A screenshot of your payment (with all transaction details);\n" +
                                "   - Your identification details (ID or username);\n" +
                                "   - The completed proxy details form:\n" +
                                "     • **Protocol:** HTTP/SOCKS5\n" +
                                "     • **Server:** *proxy\n" +
                                "     • **Port:** *port\n" +
                                "     • **Login:** KYCIFY\n" +
                                "     • **Password:** 123456\n" +
                                "  3. The verification link.\n\n" +
                                "❗️ **IMPORTANT:** All information must be sent in one message!";
                break;
            default:
                paymentDetailsText =
                        "💳 **Сума до оплати:** " + totalSumFormatted + "$\n\n" +
                                "🔹 **Реквізити для оплати:**\n" +
                                "**Aptos:**\n" +
                                "```\n0x77a6b7eef25f310a2963fac60ab56b8b2e95b51405f2c120d58b60784b91acd5\n```\n" +
                                "**BEP-20:**\n" +
                                "```\n0xcde13392f65cc1d41536a9c84b2a362ef12f9060\n```\n\n" +
                                "🔹 **Інструкції для підтвердження оплати:**\n" +
                                "1. Оплатіть зазначену суму.\n" +
                                "2. В одному повідомленні відправте:\n" +
                                "   - Скріншот оплати (з усіма деталями транзакції);\n" +
                                "   - Ваші ідентифікаційні дані (ID або username);\n" +
                                "   - Заповнену форму з даними проксі:\n" +
                                "     • **Protocol:** HTTP/SOCKS5\n" +
                                "     • **Server:** *proxy\n" +
                                "     • **Port:** *port\n" +
                                "     • **Login:** KYCIFY\n" +
                                "     • **Password:** 123456\n" +
                                "  3. Посилання на верифікацію.\n\n" +
                                "❗️ **УВАГА:** Усі дані мають бути відправлені одним повідомленням!";
                break;
        }

        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(paymentDetailsText);
        msg.setParseMode("Markdown");

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Помилка при відправці деталей оплати.");
        }

        // Надсилання фото з платіжними реквізитами (кошельками)
        try {
            InputStream aptosStream = getClass().getClassLoader().getResourceAsStream("apt_binance_usdt.jpg");
            if (aptosStream != null) {
                SendPhoto aptosPhoto = new SendPhoto();
                aptosPhoto.setChatId(chatId);
                aptosPhoto.setPhoto(new InputFile(aptosStream, "apt_binance_usdt.jpg"));
                execute(aptosPhoto);
            } else {
                sendMessage(chatId, "Файл apt_binance_usdt.jpg не знайдено.");
            }

            InputStream bep20Stream = getClass().getClassLoader().getResourceAsStream("bsc_binance_usdt.jpg");
            if (bep20Stream != null) {
                SendPhoto bep20Photo = new SendPhoto();
                bep20Photo.setChatId(chatId);
                bep20Photo.setPhoto(new InputFile(bep20Stream, "bsc_binance_usdt.jpg"));
                execute(bep20Photo);
            } else {
                sendMessage(chatId, "Файл bsc_binance_usdt.jpg не знайдено.");
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "❌ Помилка при відправці зображень.");
        }
    }

    private void sendPhotoToAdmins(long chatId, String userName, String photoId, String userText) {
        for (Long adminId : ADMIN_IDS) {
            sendPhotoToAdmin(chatId, userName, photoId, adminId, userText);
        }

        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText("✅ Ваше підтвердження відправлено адміністратору.");
            execute(message);

            sendMainMenu(chatId, userLanguages.getOrDefault(chatId, "uk"));
        } catch (TelegramApiException e) {
            e.printStackTrace();

            SendMessage errorMessage = new SendMessage();
            errorMessage.setChatId(chatId);
            errorMessage.setText("❌ Сталася помилка. Спробуйте ще раз.");
            try {
                execute(errorMessage);
            } catch (TelegramApiException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendPhotoToAdmin(long chatId, String userName, String photoId, long adminId, String userText) {
        List<String> userCartItems = userCart.getOrDefault(chatId, new ArrayList<>());
        StringBuilder orderDetails = new StringBuilder();
        if (!userCartItems.isEmpty()) {
            orderDetails.append("Послуги та ціни:\n");
            for (String item : userCartItems) {
                orderDetails.append("- ").append(item).append("\n");
            }
        } else {
            orderDetails.append("Кошик порожній.");
        }

        String adminMessageText = String.format(
                "Користувач: %s\nID: %d\nнадіслав підтвердження оплати.\n\n%s\n\nДані, які ввів користувач:\n%s",
                userName != null ? "@" + userName : "Немає логіну",
                chatId,
                orderDetails.toString(),
                userText != null && !userText.isEmpty() ? userText : "❌ Дані не були заповнені."
        );

        try {
            // Надсилання тексту адміну
            SendMessage messageToAdmin = new SendMessage();
            messageToAdmin.setChatId(adminId);
            messageToAdmin.setText(adminMessageText);
            execute(messageToAdmin);

            // Надсилання фото адміну
            SendPhoto photo = new SendPhoto();
            photo.setChatId(adminId);
            photo.setPhoto(new InputFile(photoId));
            execute(photo);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleCallbackQuery(String callbackData, long chatId) {
        String language = userLanguages.getOrDefault(chatId, "uk");

        switch (callbackData) {
            case "ukrainian" -> {
                userLanguages.put(chatId, "uk");
                sendMainMenu(chatId, "uk");
            }
            case "russian" -> {
                userLanguages.put(chatId, "ru");
                sendMainMenu(chatId, "ru");
            }
            case "english" -> {
                userLanguages.put(chatId, "en");
                sendMainMenu(chatId, "en");
            }
            case "catalog" -> sendCatalogMenu(chatId, language);
            case "profile" -> sendProfile(chatId, language);
            case "exchange_binance" -> sendCountryMenu(chatId, "Binance", language);
            case "exchange_bybit" -> sendCountryMenu(chatId, "Bybit", language);
            case "exchange_okx" -> sendCountryMenu(chatId, "OKX", language);

            // Для біржі Bybit – викликаємо askQuantity із локалізованими назвами товарів та цінами з priceConfig
            case "country_bangladesh_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Бангладеш";
                        case "en" -> "Bangladesh";
                        default -> "Бангладеш";
                    },
                    getPrice("bybit", "bangladesh"), language);
            case "country_indonesia_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Индонезия";
                        case "en" -> "Indonesia";
                        default -> "Індонезія";
                    },
                    getPrice("bybit", "indonesia"), language);
            case "country_philippines_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Филиппины";
                        case "en" -> "Philippines";
                        default -> "Філіпіни";
                    },
                    getPrice("bybit", "philippines"), language);
            case "country_pakistan_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Пакистан";
                        case "en" -> "Pakistan";
                        default -> "Пакистан";
                    },
                    getPrice("bybit", "pakistan"), language);
            case "country_egypt_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Египет";
                        case "en" -> "Egypt";
                        default -> "Єгипет";
                    },
                    getPrice("bybit", "egypt"), language);
            case "country_armenia_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Армения";
                        case "en" -> "Armenia";
                        default -> "Вірменія";
                    },
                    getPrice("bybit", "armenia"), language);
            case "country_colombia_Bybit" -> askQuantity(chatId, "Bybit",
                    switch (language) {
                        case "ru" -> "Колумбия";
                        case "en" -> "Colombia";
                        default -> "Колумбія";
                    },
                    getPrice("bybit", "colombia"), language);

            case "main_menu" -> sendMainMenu(chatId, language);
            case "cart" -> showCart(chatId, language);
            case "pay_confirm" -> sendPaymentDetails(chatId, language);
            case "clear_cart" -> clearCart(chatId, language);
            case "faq" -> sendRules(chatId, language);

            default -> sendMessage(chatId, "Невідома дія.");
        }
    }

    /**
     * Метод запитує у користувача вказати кількість одиниць для обраної позиції.
     * Дані про замовлення зберігаються в pendingOrders, після чого надсилається повідомлення
     * з інлайн-клавіатурою: кнопки з числами 1–5 та кнопка для введення вручну (локалізована).
     */
    private void askQuantity(long chatId, String exchange, String country, String price, String language) {
        pendingOrders.put(chatId, new PendingOrder(exchange, country, price, language));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        String text = switch (language) {
            case "ru" -> "Укажите количество:";
            case "en" -> "Specify the quantity:";
            default -> "Вкажіть кількість:";
        };
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        // Рядок з кнопками для вибору кількості від 1 до 5
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            row1.add(createInlineKeyboardButton(String.valueOf(i), "quantity_" + i));
        }
        // Кнопка для введення вручну – текст залежить від мови
        String manualButtonText = switch (language) {
            case "ru" -> "Если количество больше 5 – введите вручную в чате";
            case "en" -> "If quantity is more than 5 – enter manually in chat";
            default -> "Якщо кількість більше 5 – введіть вручну в чат";
        };
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineKeyboardButton(manualButtonText, "quantity_manual"));

        keyboard.add(row1);
        keyboard.add(row2);
        inlineKeyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Фіналізація замовлення з вказаною кількістю.
     * Обчислюється загальна сума (кількість * одинична ціна) та позиція додається в кошик.
     */
    private void finalizeOrder(long chatId, int quantity, PendingOrder pendingOrder) {
        double unitPrice = extractPrice(pendingOrder.price);
        double totalPrice = unitPrice * quantity;

        String orderText = String.format("%s %s — %s x %d (Total: %.2f$)",
                pendingOrder.exchange,
                pendingOrder.country,
                pendingOrder.price,
                quantity,
                totalPrice);

        List<String> cart = userCart.computeIfAbsent(chatId, k -> new ArrayList<>());
        cart.add(orderText);
        userOrdersCount.put(chatId, userOrdersCount.getOrDefault(chatId, 0) + 1);

        String confirmationMessage = switch (pendingOrder.language) {
            case "ru" -> String.format("✅ Вы выбрали: %s %s за %s x %d. Общая сумма: %.2f$. Позиция добавлена в корзину.",
                    pendingOrder.country, pendingOrder.exchange, pendingOrder.price, quantity, totalPrice);
            case "en" -> String.format("✅ You have chosen: %s %s for %s x %d. Total: %.2f$. This item has been added to your cart.",
                    pendingOrder.country, pendingOrder.exchange, pendingOrder.price, quantity, totalPrice);
            default -> String.format("✅ Ви обрали: %s %s за %s x %d. Загальна сума: %.2f$. Позиція додана до кошику.",
                    pendingOrder.country, pendingOrder.exchange, pendingOrder.price, quantity, totalPrice);
        };

        sendMessage(chatId, confirmationMessage);
        sendMainMenu(chatId, pendingOrder.language);
    }

    /**
     * Допоміжний метод для вилучення числового значення ціни з рядка.
     * Наприклад, з "4.5$ (onfido)" вилучає 4.5.
     */
    private double extractPrice(String price) {
        Pattern pattern = Pattern.compile("([0-9]+(\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(price);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private InlineKeyboardButton createInlineKeyboardButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendRules(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String rules = switch (language) {
            case "ru" -> """
                Правила пользования сервисом «KYCIFY»

                Добро пожаловать в наш магазин 🛒
                Мы предлагаем услуги верификации аккаунтов на биржах по доступным ценам.

                Ознакомьтесь с нашими правилами, чтобы избежать недоразумений.

                1️⃣ Ассортимент услуг
                1.1 Обратите внимание, что в боте представлены цены только для биржи Bybit, категории других бирж находятся в разработке.
                1.2 Вы можете заказать верификацию на других биржах, написав менеджеру.

                2️⃣ Сроки выполнения
                Время выполнения заказа зависит от объема работы и страны верификации:
                • Минимальное время – 15 минут.
                • Максимальное время – 12 часов.
                🌍 Наиболее быстро выполняются заказы для стран: Филиппины, Азия и страны Африки.

                3️⃣ Условия возврата средств
                ✅ Возврат средств возможен в следующих случаях:
                1. Верификация еще не начата в указанный срок;
                2. Аккаунт не находится в процессе проверки;
                3. Неправильные данные клиента;
                4. Отмена заказа до начала верификации;
                5. Полный отказ платформы от верификации.

                ❌ Возврат средств невозможен в следующих случаях:
                1. Завершение верификации.
                2. Задержка и технические сбои на стороне платформы.
                3. Технические сбои клиента.
                4. Несоответствие ожиданиям.
                5. Риски платформы.

                4️⃣ Ответственность сервиса

                Мы прикладываем максимум усилий, чтобы избежать блокировок аккаунтов, но обращаем внимание на такие моменты:
                • В случае блокировки аккаунта в течение 3 часов после выполнения – мы заменим его.
                • Если на платформе Bybit появилась метка о блокировке в первые 30 минут – мы также делаем замену.
                ❗️ Замена или возврат после указанного времени не осуществляется.
                💡 Мы не несем ответственности за удержание средств на бирже или восстановление аккаунтов, но всегда готовы помочь в решении подобных ситуаций.

                5️⃣ Контактное лицо

                Все заявки обрабатывает единственный менеджер - @Admin
                ‼️ Обязательно проверяйте ник, чтобы избежать мошенничества!
                • Обратите внимание, что в определенные часы из-за большого объема работы могут возникать задержки с ответом в чат с менеджером (не дублируйте сообщения, это не ускорит процедуру ответа). Менеджер обрабатывает все заявки в порядке очереди!

                6️⃣ Процедура оформления заказа

                Для заказа услуг выполните следующие шаги:
                1. Ознакомьтесь и выберите нужный товар в боте.
                2. Напишите менеджеру @admin
                В формате:
                Protocol: HTTP/SOCKS5
                Server: *proxy
                Port: *port
                Login: KYCIFY
                Password: 123456
                3. Отправьте ссылку на верифікацию.

                7️⃣ Способы оплаты

                📌 После обсуждения деталей оплачивайте заказ любым удобным способом:
                • Binance Pay.
                • Сеть BEP20/APTOS.

                📷 Обязательно отправьте скриншот оплаты менеджеру вместе с логином и паролем аккаунта или прокси/ссылкой в зависимости от выбранной вами услуги.
                При переводе между сетями учитывайте комиссию (комиссия с вашей стороны).

                8️⃣ Минимальный заказ

                Минимальное количество для покупки – 1 единица.

                Спасибо за выбор нашего сервиса! Хорошего вам дня и успешных покупок! ❤️
                Сервис оставляет за собой право изменять те или иные правила с учетом безопасности обеих сторон.
                """;
            case "en" -> rules = """
                Rules for using the KYCIFY service

                Welcome to our shop 🛒
                We offer account verification services for exchanges at affordable prices.

                Please read our rules to avoid misunderstandings.

                1️⃣ Range of services
                1.1 Currently, the bot only displays prices for the Bybit exchange. Other exchange categories are under development.
                1.2 You can order verification for other exchanges by contacting the manager.

                2️⃣ Execution terms
                The execution time depends on the workload and the verification country:
                • Minimum time – 15 minutes.
                • Maximum time – 12 hours.
                🌍 The fastest orders are completed for the following countries: Philippines, Asia, and African countries.

                3️⃣ Refund policy

                ✅ Refunds are possible in the following cases:
                1. Verification has not yet started within the specified time;
                2. The account is not in the verification process;
                3. Incorrect client data;
                4. Order cancellation before the start of verification;
                5. The platform's complete refusal to verify.

                ❌ Refunds are not possible in the following cases:
                1. Completion of verification.
                2. Delays and technical issues on the platform side.
                3. Client's technical issues.
                4. Mismatch with expectations.
                5. Platform risks.

                4️⃣ Service liability

                We make every effort to avoid account blocks, but please note the following:
                • In case of account blocking within 3 hours after completion – we will replace it.
                • If the Bybit platform shows a block notification within the first 30 minutes – we also provide a replacement.
                ❗️ Replacement or refund is not provided after the specified time.
                💡 We are not responsible for holding funds on the exchange or recovering accounts, but we are always ready to assist in resolving such situations.

                5️⃣ Contact person

                All requests are processed by a single manager - @Admin
                ‼️ Always check the username to avoid fraud!
                • Please note that at certain times, due to high workloads, there may be delays in responses in the chat with the manager (do not duplicate messages, this will not speed up the response process). The manager processes all requests in order of arrival!

                6️⃣ Order procedure

                To order services, follow these steps:
                1. Review and select the necessary item in the bot.
                2. Write to the manager @admin
                In the format:
                Protocol: HTTP/SOCKS5
                Server: *proxy
                Port: *port
                Login: KYCIFY
                Password: 123456
                3. Send the verification link.

                7️⃣ Payment methods

                📌 After discussing the details, pay for the order in any convenient way:
                • Binance Pay.
                • BEP20/APTOS Network.

                📷 Be sure to send a payment screenshot to the manager along with the account login and password or proxy/link, depending on the service you ordered.
                When transferring between networks, take into account the commission (the commission is on your side).

                8️⃣ Minimum order

                The minimum quantity for purchase is 1 unit.

                Thank you for choosing our service! Have a great day and successful shopping! ❤️
                The service reserves the right to change certain rules considering the safety of both parties.
                """;
            default -> rules = """
                Правила користування сервісом «KYCIFY»

                Ласкаво просимо до нашого магазину 🛒
                Ми пропонуємо послуги верифікації акаунтів на біржах за доступними цінами.

                Ознайомтесь із нашими правилами, щоб уникнути непорозумінь.

                1️⃣ Асортимент послуг
                1.1 Зверніть увагу, що наразі в боті представлені ціни для біржі Bybit, категорії інших бірж в розробці.
                1.2 Але Ви вже можете замовляти верифікацію на будь-яких інших відомих криптовалютних біржах просто написавши менеджеру про це.

                2️⃣ Терміни виконання
                Час виконання замовлення залежить від обсягу роботи та країни верифікації:
                • Мінімальний час – 15 хвилин.
                • Максимальний час – 12 годин.
                🌍 Найшвидше виконуються замовлення для країн: Філіпіни, Азія та Африканські країни.

                3️⃣ Умови повернення коштів

                ✅ Повернення коштів можливе у таких випадках:
                1. Верифікацію ще не розпочато за вказаний час;
                2. Обліковий запис не перебуває в процесі перевірки;
                3. Неправильні дані клієнта;
                4. Скасування замовлення до початку верифікації;
                5. Повна відмова платформи від верифікації.

                ❌ Повернення коштів не можливе у таких випадках:
                1. Завершення верифікації.
                2. Затримка та технічні збої на боці платформи.
                3. Технічні збої клієнта.
                4. Невідповідність очікуванням.
                5. Ризики платформи.

                4️⃣ Відповідальність сервісу

                Ми докладаємо максимум зусиль, щоб уникнути блокувань акаунтів, але звертаємо увагу на такі моменти:
                • У разі блокування акаунту протягом 3 годин після виконання – ми замінюємо його.
                • Якщо на платформі Bybit з’явилася плашка з баном у перші 30 хвилин – ми також робимо заміну.
                ❗️ Заміну або повернення після вказаного часу не здійснюємо.
                💡 Ми не несемо відповідальності за утримання коштів на біржі чи відновлення акаунтів, але завжди готові допомогти у вирішенні подібних ситуацій.

                5️⃣ Контактна особа

                Всі заявки обробляє єдиний менеджер - @Admin
                ‼️ Обов’язково перевіряйте нік, щоб уникнути шахрайства!
                • Зверніть увагу, що в певні години, через великий обсяг роботи можуть відбуватися затримки з відповіддю в чаті з менеджером (не дублюйте повідомлення, це не прискорить процедуру відповіді) менеджер оброблює всі заявки в порядку черги!

                6️⃣ Процедура оформлення замовлення

                Для замовлення послуг виконайте такі кроки:
                1. Ознайомтесь і оберіть необхідний товар у боті.
                2. Напишіть менеджеру @admin
                У форматі:
                Protocol: HTTP/SOCKS5
                Server: *проксі
                Port: *порт
                Login: KYCIFY
                Password: 123456
                3. Скинути посилання на верифікацію.

                7️⃣ Способи оплати

                📌 Після обговорення деталей оплачуйте замовлення будь-яким зручним способом:
                • Binance Pay.
                • Мережа BEP20/APTOS.

                📷 Обов’язково надішліть скріншот оплати менеджеру разом із логіном та паролем акаунту або проксі/посиланням в залежності від замовленої Вами верифікації.
                При переводі між мережами враховуйте комісію (комісія з вашої сторони).

                8️⃣ Мінімальне замовлення

                Мінімальна кількість для покупки – 1 одиниця.

                Дякуємо за вибір нашого сервісу! Гарного вам дня та успішних покупок! ❤️
                Сервіс має право залишати за собою зміну тих чи інших правил з урахуванням безпеки для обох сторін.
                """;
        };

        message.setText(rules);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendProfile(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String profileInfo = switch (language) {
            case "ru" -> "\uD83D\uDC64 Ваш профиль:\n\uD83D\uDCB3 Заказов: " + userOrdersCount.getOrDefault(chatId, 0);
            case "en" -> "\uD83D\uDC64 Your profile:\n\uD83D\uDCB3 Orders: " + userOrdersCount.getOrDefault(chatId, 0);
            default -> "\uD83D\uDC64 Ваш профіль:\n\uD83D\uDCB3 Замовлень: " + userOrdersCount.getOrDefault(chatId, 0);
        };
        message.setText(profileInfo);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDCCB Каталог";
                    case "en" -> "\uD83D\uDCCB Catalog";
                    default -> "\uD83D\uDCCB Каталог";
                }, "catalog"));
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "ru" -> "\uD83D\uDC64 Мой профиль";
                    case "en" -> "\uD83D\uDC64 My Profile";
                    default -> "\uD83D\uDC64 Мій профіль";
                }, "cart"));  // Замінено на "cart" чи "Кошик" – за потреби

        keyboardButtons.add(row1);
        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendCountryMenu(long chatId, String exchange, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String text = switch (language) {
            case "ru" -> "\uD83C\uDF10 Выберите страну для верификации на " + exchange + ":";
            case "en" -> "\uD83C\uDF10 Choose a country for verification on " + exchange + ":";
            default -> "\uD83C\uDF10 Оберіть країну для верифікації на " + exchange + ":";
        };

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        if (exchange.equals("Bybit")) {
            // Для кожного варіанту визначаємо текст кнопки залежно від мови
            String bangladeshText = switch (language) {
                case "ru" -> "🇧🇩 Бангладеш - " + getPrice("bybit", "bangladesh");
                case "en" -> "🇧🇩 Bangladesh - " + getPrice("bybit", "bangladesh");
                default -> "🇧🇩 Бангладеш - " + getPrice("bybit", "bangladesh");
            };
            String indonesiaText = switch (language) {
                case "ru" -> "🇮🇩 Индонезия - " + getPrice("bybit", "indonesia");
                case "en" -> "🇮🇩 Indonesia - " + getPrice("bybit", "indonesia");
                default -> "🇮🇩 Індонезія - " + getPrice("bybit", "indonesia");
            };
            String philippinesText = switch (language) {
                case "ru" -> "🇵🇭 Филиппины - " + getPrice("bybit", "philippines");
                case "en" -> "🇵🇭 Philippines - " + getPrice("bybit", "philippines");
                default -> "🇵🇭 Філіпіни - " + getPrice("bybit", "philippines");
            };
            String pakistanText = switch (language) {
                case "ru" -> "🇵🇰 Пакистан - " + getPrice("bybit", "pakistan");
                case "en" -> "🇵🇰 Pakistan - " + getPrice("bybit", "pakistan");
                default -> "🇵🇰 Пакистан - " + getPrice("bybit", "pakistan");
            };
            String egyptText = switch (language) {
                case "ru" -> "🇪🇬 Египет - " + getPrice("bybit", "egypt");
                case "en" -> "🇪🇬 Egypt - " + getPrice("bybit", "egypt");
                default -> "🇪🇬 Єгипет - " + getPrice("bybit", "egypt");
            };
            String armeniaText = switch (language) {
                case "ru" -> "🇦🇲 Армения - " + getPrice("bybit", "armenia");
                case "en" -> "🇦🇲 Armenia - " + getPrice("bybit", "armenia");
                default -> "🇦🇲 Вірменія - " + getPrice("bybit", "armenia");
            };
            String colombiaText = switch (language) {
                case "ru" -> "🇨🇴 Колумбия - " + getPrice("bybit", "colombia");
                case "en" -> "🇨🇴 Colombia - " + getPrice("bybit", "colombia");
                default -> "🇨🇴 Колумбія - " + getPrice("bybit", "colombia");
            };

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createInlineKeyboardButton(bangladeshText, "country_bangladesh_Bybit"));
            row1.add(createInlineKeyboardButton(indonesiaText, "country_indonesia_Bybit"));

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(createInlineKeyboardButton(philippinesText, "country_philippines_Bybit"));
            row2.add(createInlineKeyboardButton(pakistanText, "country_pakistan_Bybit"));

            List<InlineKeyboardButton> row3 = new ArrayList<>();
            row3.add(createInlineKeyboardButton(egyptText, "country_egypt_Bybit"));
            row3.add(createInlineKeyboardButton(armeniaText, "country_armenia_Bybit"));

            List<InlineKeyboardButton> row4 = new ArrayList<>();
            row4.add(createInlineKeyboardButton(colombiaText, "country_colombia_Bybit"));

            keyboardButtons.add(row1);
            keyboardButtons.add(row2);
            keyboardButtons.add(row3);
            keyboardButtons.add(row4);
        } else {
            text = switch (language) {
                case "ru" -> "На бирже " + exchange + " пока нет доступных верификаций.";
                case "en" -> "No available verifications on " + exchange + " at the moment.";
                default -> "На біржі " + exchange + " поки немає доступних верифікацій.";
            };

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineKeyboardButton("\uD83D\uDD19 ", "main_menu"));
            keyboardButtons.add(row);
        }

        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setText(text);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showCart(long chatId, String language) {
        List<String> cart = userCart.get(chatId);

        if (cart == null || cart.isEmpty()) {
            String emptyCartMessage = switch (language) {
                case "ru" -> "Ваша корзина пуста.";
                case "en" -> "Your cart is empty.";
                default -> "Ваш кошик порожній.";
            };
            sendMessage(chatId, emptyCartMessage);
        } else {
            StringBuilder cartMessage = new StringBuilder(switch (language) {
                case "ru" -> "\uD83D\uDED2 Ваша корзина:";
                case "en" -> "\uD83D\uDED2 Your cart:";
                default -> "\uD83D\uDED2 Ваш кошик:";
            });

            for (String item : cart) {
                String translatedItem = translateCartItem(item, language);
                cartMessage.append("\n").append(translatedItem);
            }

            cartMessage.append("\n\n").append(switch (language) {
                case "ru" -> "✅ Оплатить заказ или \uD83D\uDDD1 Очистить корзину.";
                case "en" -> "✅ Pay for the order or \uD83D\uDDD1 Clear the cart.";
                default -> "✅ Оплатити замовлення або \uD83D\uDDD1 Очистити кошик.";
            });

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(cartMessage.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createInlineKeyboardButton(
                    switch (language) {
                        case "ru" -> "✅ Оплатить";
                        case "en" -> "✅ Pay";
                        default -> "✅ Оплатити";
                    },
                    "pay_confirm"));
            row1.add(createInlineKeyboardButton(
                    switch (language) {
                        case "ru" -> "\uD83D\uDDD1 Очистить корзину";
                        case "en" -> "\uD83D\uDDD1 Clear the cart";
                        default -> "\uD83D\uDDD1 Очистити кошик";
                    },
                    "clear_cart"));
            buttons.add(row1);
            markup.setKeyboard(buttons);
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private String translateCartItem(String item, String language) {
        if (item.contains("Бангладеш")) {
            return switch (language) {
                case "ru" -> item.replace("Бангладеш", "🇧🇩 Бангладеш ");
                case "en" -> item.replace("Бангладеш", "🇧🇩 Bangladesh ");
                default -> item.replace("Бангладеш", "🇧🇩 Бангладеш ");
            };
        } else if (item.contains("Індонезія") || item.contains("Индонезия")) {
            return switch (language) {
                case "ru" -> item.replaceAll("Индонезия|Індонезія", "🇮🇩 Индонезия ");
                case "en" -> item.replaceAll("Індонезія", "🇮🇩 Indonesia ");
                default -> item.replaceAll("Индонезия", "🇮🇩 Індонезія ");
            };
        } else if (item.contains("Філіпіни") || item.contains("Филиппины")) {
            return switch (language) {
                case "ru" -> item.replaceAll("Филиппины|Філіпіни", "🇵🇭 Филиппины ");
                case "en" -> item.replaceAll("Філіпіни", "🇵🇭 Philippines ");
                default -> item.replaceAll("Филиппины", "🇵🇭 Філіпіни ");
            };
        } else if (item.contains("Пакистан")) {
            return switch (language) {
                case "ru" -> item.replace("Пакистан", "🇵🇰 Пакистан ");
                case "en" -> item.replace("Пакистан", "🇵🇰 Pakistan ");
                default -> item.replace("Пакистан", "🇵🇰 Пакистан ");
            };
        } else if (item.contains("Єгипет") || item.contains("Египет")) {
            return switch (language) {
                case "ru" -> item.replaceAll("Египет|Єгипет", "🇪🇬 Египет ");
                case "en" -> item.replaceAll("Єгипет", "🇪🇬 Egypt ");
                default -> item.replaceAll("Египет", "🇪🇬 Єгипет ");
            };
        } else if (item.contains("Вірменія") || item.contains("Армения")) {
            return switch (language) {
                case "ru" -> item.replaceAll("Армения|Вірменія", "🇦🇲 Армения ");
                case "en" -> item.replaceAll("Вірменія", "🇦🇲 Armenia ");
                default -> item.replaceAll("Армения", "🇦🇲 Вірменія ");
            };
        } else if (item.contains("Колумбія") || item.contains("Колумбия")) {
            return switch (language) {
                case "ru" -> item.replaceAll("Колумбия|Колумбія", "🇨🇴 Колумбия ");
                case "en" -> item.replaceAll("Колумбія", "🇨🇴 Colombia ");
                default -> item.replaceAll("Колумбия", "🇨🇴 Колумбія ");
            };
        }
        return item;
    }

    private void clearCart(long chatId, String language) {
        userCart.remove(chatId);
        String messageText = switch (language) {
            case "ru" -> "✅ Ваша корзина была успешно очищена.";
            case "en" -> "✅ Your cart has been successfully cleared.";
            default -> "✅ Ваш кошик було успішно очищено.";
        };
        sendMessage(chatId, messageText);
        sendMainMenu(chatId, language);
    }

    @Override
    public String getBotUsername() {
        return Config.get("BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        return Config.get("BOT_TOKEN");
    }
}
