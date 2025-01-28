package org.mooneiko;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KYCIFYBot extends TelegramLongPollingBot {
    private final Map<Long, String> userLanguages = new HashMap<>();
    private final Map<Long, List<String>> userCart = new HashMap<>();
    private final Map<Long, Integer> userOrdersCount = new HashMap<>();
    private final List<Long> ADMIN_IDS = List.of(395336816L, 1154426750L);

    @Override
    public void onUpdateReceived(Update update) {
        long chatId;
        if (update.hasMessage()) {
            if (update.getMessage().hasText()) {
                chatId = update.getMessage().getChatId();
                String messageText = update.getMessage().getText();

                if (messageText.equals("/start")) {
                    sendLanguageSelection(chatId);
                }
            } else if (update.getMessage().hasPhoto()) {
                chatId = update.getMessage().getChatId();
                String userName = update.getMessage().getFrom().getUserName();
                String photoId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();

                sendPhotoToAdmins(chatId, userName, photoId);
            }
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();
            handleCallbackQuery(callbackData, chatId);
        }
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
            case "uk" -> "Виберіть потрібну категорію нижче:";
            case "ru" -> "Выберите нужную категорию ниже:";
            default -> "Choose the desired category below:";
        };
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        // Рядок 1: Каталог і Мій профіль
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "\uD83D\uDCCB Каталог";
                    case "ru" -> "\uD83D\uDCCB Каталог";
                    default -> "\uD83D\uDCCB Catalog";
                },
                "catalog"));
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "\uD83D\uDC64 Мій профіль";
                    case "ru" -> "\uD83D\uDC64 Мой профиль";
                    default -> "\uD83D\uDC64 My Profile";
                },
                "profile"));

        // Рядок 2: Питання/Відповіді і Кошик
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "\u2753 Питання/Відповіді";
                    case "ru" -> "\u2753 Вопросы/Ответы";
                    default -> "\u2753 FAQ";
                },
                "faq"));
        row2.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "\uD83D\uDED2 Кошик";
                    case "ru" -> "\uD83D\uDED2 Корзина";
                    default -> "\uD83D\uDED2 Cart";
                },
                "cart"));

        // Рядок 3: Відгуки
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createInlineKeyboardUrlButton(
                switch (language) {
                    case "uk" -> "\uD83D\uDCAC Відгуки";
                    case "ru" -> "\uD83D\uDCAC Отзывы";
                    default -> "\uD83D\uDCAC Feedback";
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
            case "uk" -> "\uD83C\uDF10 Оберіть біржу для верифікації:";
            case "ru" -> "\uD83C\uDF10 Выберите биржу для верификации:";
            default -> "\uD83C\uDF10 Choose an exchange for verification:";
        };
        message.setText(text);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "Binance";
                    case "ru" -> "Binance";
                    default -> "Binance";
                }, "exchange_binance"));
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "Bybit";
                    case "ru" -> "Bybit";
                    default -> "Bybit";
                }, "exchange_bybit"));
        row1.add(createInlineKeyboardButton(
                switch (language) {
                    case "uk" -> "OKX";
                    case "ru" -> "OKX";
                    default -> "OKX";
                }, "exchange_okx"));

        keyboardButtons.add(row1);
        inlineKeyboardMarkup.setKeyboard(keyboardButtons);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendPaymentDetails(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String paymentDetails = switch (language) {
            case "uk" -> "\uD83D\uDCB3 Реквізити для оплати:\n\uD83E\uDE99 Оплата в криптовалюті:\n\uD83D\uDD39 - USDT (TRC20): Надішліть 11 USDT на адресу:\nTJrfNbaAnGKc1ApYrGgnH4yMNmb4FEfzBq\n\n⚠️ ℹ️ Після оплати надішліть скріншот у цей чат, щоб підтвердити оплату.";
            case "ru" -> "\uD83D\uDCB3 Реквизиты для оплаты:\n\uD83E\uDE99 Оплата в криптовалюте:\n\uD83D\uDD39 - USDT (TRC20): Отправьте 11 USDT на адрес:\nTJrfNbaAnGKc1ApYrGgnH4yMNmb4FEfzBq\n\n⚠️ ℹ️ После оплаты отправьте скриншот в этот чат, чтобы подтвердить оплату.";
            default -> "\uD83D\uDCB3 Payment details:\n\uD83E\uDE99 Cryptocurrency payment:\n\uD83D\uDD39 - USDT (TRC20): Send 11 USDT to the address:\nTJrfNbaAnGKc1ApYrGgnH4yMNmb4FEfzBq\n\n⚠️ ℹ️ After payment, send a screenshot in this chat to confirm payment.";
        };

        message.setText(paymentDetails);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPhotoToAdmins(long chatId, String userName, String photoId) {
        for (Long adminId : ADMIN_IDS) {
            sendPhotoToAdmin(chatId, userName, photoId, adminId);
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

    private void sendPhotoToAdmin(long chatId, String userName, String photoId, long adminId) {
        // Отримуємо список замовлень з кошика
        List<String> userCartItems = userCart.getOrDefault(chatId, new ArrayList<>());

        // Формуємо текст замовлення
        StringBuilder orderDetails = new StringBuilder();
        if (!userCartItems.isEmpty()) {
            orderDetails.append("Послуги та ціни:\n");
            for (String item : userCartItems) {
                orderDetails.append("- ").append(item).append("\n");
            }
        } else {
            orderDetails.append("Кошик порожній.");
        }

        // Повідомлення адміністратору
        String adminMessageText = String.format(
                "Користувач: %s\nID: %d\nнадіслав підтвердження оплати.\n\n%s",
                userName != null ? "@" + userName : "Немає логіну",
                chatId,
                orderDetails.toString()
        );

        try {
            // Відправляємо текстове повідомлення адміну
            SendMessage messageToAdmin = new SendMessage();
            messageToAdmin.setChatId(adminId);
            messageToAdmin.setText(adminMessageText);
            execute(messageToAdmin);

            // Відправляємо фото адміну
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
            case "country_bangladesh_Bybit" -> finalizeOrder(chatId, "Bybit", "🇧🇩 Бангладеш", "4.5$", language);
            case "country_indonesia_Bybit" -> finalizeOrder(chatId, "Bybit", "🇮🇩 Індонезія", "5.5$", language);
            case "country_philippines_Bybit" -> finalizeOrder(chatId, "Bybit", "🇵🇭 Філіпіни", "8$ (onfido)", language);
            case "country_pakistan_Bybit" -> finalizeOrder(chatId, "Bybit", "🇵🇰 Пакистан", "9$", language);
            case "country_egypt_Bybit" -> finalizeOrder(chatId, "Bybit", "🇪🇬 Єгипет", "10$ (onfido, data)", language);
            case "country_armenia_Bybit" -> finalizeOrder(chatId, "Bybit", "🇦🇲 Вірменія", "18$ (onfido, sumsub)", language);
            case "country_colombia_Bybit" -> finalizeOrder(chatId, "Bybit", "🇨🇴 Колумбія", "11$ (onfido)", language);
            case "main_menu" -> sendMainMenu(chatId, language);
            case "cart" -> showCart(chatId, language);
            case "pay_confirm" -> sendPaymentDetails(chatId, language);
            case "clear_cart" -> clearCart(chatId, language); // Додано обробку кнопки "Очистити кошик"
            case "faq" -> sendRules(chatId, language);

            default -> sendMessage(chatId, "Невідома дія.");
        }
    }

    private void sendRules(long chatId, String language) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        String rules = switch (language) {
            case "uk" -> """
                Правила користування сервісом «KYCIFY»

                Ласкаво просимо до нашого магазину 🛒
                Ми пропонуємо послуги верифікації акаунтів на біржах за доступними цінами.

                Ознайомтесь із нашими правилами, щоб уникнути непорозумінь.

                1️⃣ Асортимент послуг
                1.1 Зверніть увагу, що наразі в боті представлені ціни для біржи Bybit, категорії інших бірж в розробці.
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
                4. Невідповідність очікувань.
                5. Ризики платформи.

                4️⃣ Відповідальність сервісу

                Ми докладаємо максимум зусиль, щоб уникнути блокувань акаунтів, але звертаємо увагу на такі моменти:
                • У разі блокування акаунту протягом 3 годин після виконання – ми замінюємо його.
                • Якщо на платформі ByBit з’явилася плашка з баном у перші 30 хвилин – ми також робимо заміну.
                ❗️ Заміну або повернення після вказаного часу не здійснюємо.
                💡 Ми не несемо відповідальності за утримання коштів на біржі чи відновлення акаунтів, але завжди готові допомогти у розв’язанні подібних ситуацій.

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
                • Если на платформе ByBit появилась метка о блокировке в первые 30 минут – мы также делаем замену.
                ❗️ Замена или возврат после указанного времени не осуществляется.
                💡 Мы не несем ответственности за удержание средств на бирже или восстановление аккаунтов, но всегда готовы помочь в решении подобных ситуаций.

                5️⃣ Контактное лицо

                Все заявки обрабатывает единственный менеджер - @Admin
                ‼️ Обязательно проверяйте ник, чтобы избежать мошенничества!
                • Обратите внимание, что в определенные часы из-за большого объема работы могут возникать задержки с ответом в чате с менеджером (не дублируйте сообщения, это не ускорит процедуру ответа). Менеджер обрабатывает все заявки в порядке очереди!

                6️⃣ Процедура оформления заказа

                Для заказа услуг выполните следующие шаги:
                1. Ознакомьтесь и выберите нужный товар в боте.
                2. Напишите менеджеру @admin
                В формате:
                Protocol: HTTP/SOCKS5
                Server: *прокси
                Port: *порт
                Login: KYCIFY
                Password: 123456
                3. Отправьте ссылку на верификацию.

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

            case "en" -> """
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
                • If the ByBit platform shows a block notification within the first 30 minutes – we also provide a replacement.
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

            default -> "Rules are not available in your language.";
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
            case "uk" -> "\uD83D\uDC64 Ваш профіль:\n\uD83D\uDCB3 Замовлень: " + userOrdersCount.getOrDefault(chatId, 0);
            case "ru" -> "\uD83D\uDC64 Ваш профиль:\n\uD83D\uDCB3 Заказов: " + userOrdersCount.getOrDefault(chatId, 0);
            default -> "\uD83D\uDC64 Your profile:\n\uD83D\uDCB3 Orders: " + userOrdersCount.getOrDefault(chatId, 0);
        };
        message.setText(profileInfo);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineKeyboardButton("\uD83D\uDCCB Каталог", "catalog"));
        row1.add(createInlineKeyboardButton("\uD83D\uDED2 Кошик", "cart"));

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
            case "uk" -> "\uD83C\uDF10 Оберіть країну для верифікації на " + exchange + ":";
            case "ru" -> "\uD83C\uDF10 Выберите страну для верификации на " + exchange + ":";
            default -> "\uD83C\uDF10 Choose a country for verification on " + exchange + ":";
        };

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardButtons = new ArrayList<>();

        // Вибір країни залежно від біржі
        if (exchange.equals("Bybit")) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createInlineKeyboardButton("🇧🇩 Бангладеш - 4.5$", "country_bangladesh_" + exchange));
            row1.add(createInlineKeyboardButton("🇮🇩 Індонезія - 5.5$", "country_indonesia_" + exchange));

            List<InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(createInlineKeyboardButton("🇵🇭 Філіпіни - 8$ (onfido)", "country_philippines_" + exchange));
            row2.add(createInlineKeyboardButton("🇵🇰 Пакистан - 9$", "country_pakistan_" + exchange));

            List<InlineKeyboardButton> row3 = new ArrayList<>();
            row3.add(createInlineKeyboardButton("🇪🇬 Єгипет - 10$ (onfido, data)", "country_egypt_" + exchange));
            row3.add(createInlineKeyboardButton("🇦🇲 Вірменія - 18$ (onfido, sumsub)", "country_armenia_" + exchange));

            List<InlineKeyboardButton> row4 = new ArrayList<>();
            row4.add(createInlineKeyboardButton("🇨🇴 Колумбія - 11$ (onfido)", "country_colombia_" + exchange));

            keyboardButtons.add(row1);
            keyboardButtons.add(row2);
            keyboardButtons.add(row3);
            keyboardButtons.add(row4);

        } else {
            // Для інших бірж повідомлення про відсутність
            text = switch (language) {
                case "uk" -> "На біржі " + exchange + " поки немає доступних верифікацій.";
                case "ru" -> "На бирже " + exchange + " пока нет доступных верификаций.";
                default -> "No available verifications on " + exchange + " at the moment.";
            };

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(createInlineKeyboardButton("\uD83D\uDD19 Назад у меню", "main_menu"));

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
                case "uk" -> "Ваш кошик порожній.";
                case "ru" -> "Ваша корзина пуста.";
                default -> "Your cart is empty.";
            };
            sendMessage(chatId, emptyCartMessage);
        } else {
            // Заголовок кошика
            StringBuilder cartMessage = new StringBuilder(switch (language) {
                case "uk" -> "\uD83D\uDED2 Ваш кошик:";
                case "ru" -> "\uD83D\uDED2 Ваша корзина:";
                default -> "\uD83D\uDED2 Your cart:";
            });

            // Додаємо елементи до повідомлення
            for (String item : cart) {
                String translatedItem = translateCartItem(item, language); // Переклад кожного елемента
                cartMessage.append("\n").append(translatedItem);
            }

            // Додаємо кнопки оплати та очищення
            cartMessage.append("\n\n").append(switch (language) {
                case "uk" -> "✅ Оплатити замовлення або \uD83D\uDDD1 Очистити кошик.";
                case "ru" -> "✅ Оплатить заказ или \uD83D\uDDD1 Очистить корзину.";
                default -> "✅ Pay for the order or \uD83D\uDDD1 Clear the cart.";
            });

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(cartMessage.toString());

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createInlineKeyboardButton(
                    switch (language) {
                        case "uk" -> "✅ Оплатити";
                        case "ru" -> "✅ Оплатить";
                        default -> "✅ Pay";
                    },
                    "pay_confirm"));
            row1.add(createInlineKeyboardButton(
                    switch (language) {
                        case "uk" -> "\uD83D\uDDD1 Очистити кошик";
                        case "ru" -> "\uD83D\uDDD1 Очистить корзину";
                        default -> "\uD83D\uDDD1 Clear the cart";
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
                case "uk" -> item.replace("Бангладеш", "🇧🇩 Бангладеш ");
                case "ru" -> item.replace("Бангладеш", "🇧🇩 Бангладеш ");
                default -> item.replace("Бангладеш", "🇧🇩 Bangladesh ");
            };
        } else if (item.contains("Індонезія")) {
            return switch (language) {
                case "uk" -> item.replace("Індонезія", "🇮🇩 Індонезія ");
                case "ru" -> item.replace("Індонезія", "🇮🇩 Индонезия ");
                default -> item.replace("Індонезія", "🇮🇩 Indonesia ");
            };
        } else if (item.contains("Філіпіни")) {
            return switch (language) {
                case "uk" -> item.replace("Філіпіни", "🇵🇭 Філіпіни ");
                case "ru" -> item.replace("Філіпіни", "🇵🇭 Филиппины ");
                default -> item.replace("Філіпіни", "🇵🇭 Philippines ");
            };
        } else if (item.contains("Пакистан")) {
            return switch (language) {
                case "uk" -> item.replace("Пакистан", "🇵🇰 Пакистан ");
                case "ru" -> item.replace("Пакистан", "🇵🇰 Пакистан ");
                default -> item.replace("Пакистан", "🇵🇰 Pakistan ");
            };
        } else if (item.contains("Єгипет")) {
            return switch (language) {
                case "uk" -> item.replace("Єгипет", "🇪🇬 Єгипет ");
                case "ru" -> item.replace("Єгипет", "🇪🇬 Египет ");
                default -> item.replace("Єгипет", "🇪🇬 Egypt ");
            };
        } else if (item.contains("Вірменія")) {
            return switch (language) {
                case "uk" -> item.replace("Вірменія", "🇦🇲 Вірменія ");
                case "ru" -> item.replace("Вірменія", "🇦🇲 Армения ");
                default -> item.replace("Вірменія", "🇦🇲 Armenia ");
            };
        } else if (item.contains("Колумбія")) {
            return switch (language) {
                case "uk" -> item.replace("Колумбія", "🇨🇴 Колумбія ");
                case "ru" -> item.replace("Колумбія", "🇨🇴 Колумбия ");
                default -> item.replace("Колумбія", "🇨🇴 Colombia ");
            };
        }
        // Якщо країна не знайдена, повертаємо оригінальний текст
        return item;
    }




    private void finalizeOrder(long chatId, String exchange, String country, String price, String language) {
        List<String> cart = userCart.computeIfAbsent(chatId, k -> new ArrayList<>());
        String orderText = String.format("%s %s — %s", exchange, country, price);
        cart.add(orderText);
        userOrdersCount.put(chatId, userOrdersCount.getOrDefault(chatId, 0) + 1);

        String confirmationMessage = switch (language) {
            case "uk" -> String.format("✅ Ви обрали: %s %s за %s. Ця позиція додана у ваш кошик.", country, exchange, price);
            case "ru" -> String.format("✅ Вы выбрали: %s %s за %s. Эта позиция добавлена в вашу корзину.", country, exchange, price);
            default -> String.format("✅ You have chosen: %s %s for %s. This item has been added to your cart.", country, exchange, price);
        };

        sendMessage(chatId, confirmationMessage);
        sendMainMenu(chatId, language);
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
    private void clearCart(long chatId, String language) {
        // Видаляємо всі елементи з кошика
        userCart.remove(chatId);

        // Повідомлення користувачу
        String messageText = switch (language) {
            case "uk" -> "✅ Ваш кошик було успішно очищено.";
            case "ru" -> "✅ Ваша корзина была успешно очищена.";
            default -> "✅ Your cart has been successfully cleared.";
        };

        sendMessage(chatId, messageText);

        // Повертаємо користувача в головне меню
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
