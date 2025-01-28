package org.mooneiko;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static KYCIFYBot bot;
    private static ExecutorService executorService;
    private static TelegramBotsApi botsApi;

    public static void main(String[] args) {
        // Создаем графическое окно
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kycify Bot Controller");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);
            frame.setResizable(false);

            // Основная панель
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BorderLayout());
            mainPanel.setBackground(new Color(40, 44, 52));

            // Верхняя панель с заголовком
            JLabel titleLabel = new JLabel("Kycify Bot Controller", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            mainPanel.add(titleLabel, BorderLayout.NORTH);

            // Центр с кнопками
            JPanel buttonPanel = new JPanel();
            buttonPanel.setBackground(new Color(40, 44, 52));
            buttonPanel.setLayout(new GridLayout(2, 1, 20, 20));

            JButton startButton = new JButton("Start Bot");
            JButton stopButton = new JButton("Stop Bot");
            stopButton.setEnabled(false);

            // Настройка кнопок
            customizeButton(startButton, new Color(0, 200, 83), Color.WHITE);
            customizeButton(stopButton, new Color(220, 53, 69), Color.WHITE);

            buttonPanel.add(startButton);
            buttonPanel.add(stopButton);
            mainPanel.add(buttonPanel, BorderLayout.CENTER);

            // Нижняя панель с информацией
            JLabel statusLabel = new JLabel("Status: Bot is stopped", SwingConstants.CENTER);
            statusLabel.setFont(new Font("Arial", Font.ITALIC, 14));
            statusLabel.setForeground(Color.LIGHT_GRAY);
            statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            mainPanel.add(statusLabel, BorderLayout.SOUTH);

            frame.getContentPane().add(mainPanel);

            // Логика для кнопки "Start Bot"
            startButton.addActionListener(e -> {
                try {
                    executorService = Executors.newSingleThreadExecutor();
                    bot = new KYCIFYBot();
                    executorService.submit(() -> {
                        try {
                            botsApi = new TelegramBotsApi(DefaultBotSession.class);
                            botsApi.registerBot(bot);
                            SwingUtilities.invokeLater(() -> statusLabel.setText("Status: Bot is running"));
                            System.out.println("Bot started!");
                        } catch (TelegramApiException ex) {
                            showErrorDialog(frame, "Error starting bot: " + ex.getMessage());
                        }
                    });
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                } catch (Exception ex) {
                    showErrorDialog(frame, "Error: " + ex.getMessage());
                }
            });

            // Логика для кнопки "Stop Bot"
            stopButton.addActionListener(e -> {
                if (executorService != null && !executorService.isShutdown()) {
                    try {
                        // Останавливаем потоки
                        executorService.shutdownNow();

                        // Если бот зарегистрирован, отключаем его обработку
                        if (bot != null) {
                            bot.onClosing(); // Вызываем метод закрытия для бота
                        }

                        SwingUtilities.invokeLater(() -> statusLabel.setText("Status: Bot is stopped")); // Обновляем статус
                        System.out.println("Bot stopped!");
                    } catch (Exception ex) {
                        showErrorDialog(frame, "Error stopping bot: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                }
            });


            frame.setVisible(true);
        });
    }

    // Метод для стилизации кнопок
    private static void customizeButton(JButton button, Color bgColor, Color fgColor) {
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
    }

    // Метод для показа диалогов ошибок
    private static void showErrorDialog(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
