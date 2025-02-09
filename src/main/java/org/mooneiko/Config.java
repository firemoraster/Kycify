package org.mooneiko;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Не вдалося знайти файл конфігурації: config.properties");
            } else {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println("Не вдалося завантажити файл конфігурації: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}
