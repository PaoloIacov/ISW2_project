package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesManager {

    private static final String PROPERTIES_FILE_NAME = "info.properties";

    private final Map<String, String> properties = new HashMap<>();

    // 🔒 Singleton instanziato in modo thread-safe e lazy
    private static class Holder {
        private static final PropertiesManager INSTANCE = new PropertiesManager();
    }

    public static PropertiesManager getInstance() {
        return Holder.INSTANCE;
    }

    // 🚫 Costruttore privato
    private PropertiesManager() {
        loadProperties();
    }

    private void loadProperties() {
        Properties props = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME)) {
            if (inputStream != null) {
                props.load(inputStream);
                for (String key : props.stringPropertyNames()) {
                    properties.put(key, props.getProperty(key));
                }
            } else {
                System.err.println("Unable to find " + PROPERTIES_FILE_NAME);
            }
        } catch (IOException e) {
            System.err.println("Error loading properties file: " + e.getMessage());
        }
    }

    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

    // 🔍 (Opzionale) per debugging
    public void printAll() {
        properties.forEach((key, value) -> System.out.println(key + " = " + value));
    }
}

