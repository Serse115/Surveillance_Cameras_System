package com;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Gestore centralizzato della configurazione applicativa.
 * Carica le credenziali e gli indirizzi IP dal file config.properties.
 */
public class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.properties"; // Questo è un esempio, inserire il nome del properties in cui si hanno le credenziali davvero
    private static final Properties properties = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        File configFile = new File(CONFIG_FILE_NAME);

        // 1. Prova a leggere il file dalla cartella di lavoro corrente (fuori dal jar)
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                System.out.println("-> [CONFIG] Configurazione caricata da file esterno: " + configFile.getAbsolutePath());
                return;
            }
            catch (IOException e) {
                System.err.println("[ERRORE CONFIG] Impossibile leggere " + CONFIG_FILE_NAME + ": " + e.getMessage());
            }
        }

        // 2. Se non esiste esternamente, cerca nel classpath (src/main/resources)
        try (InputStream input = ConfigManager.class.getResourceAsStream("/" + CONFIG_FILE_NAME)) {
            if (input != null) {
                properties.load(input);
                System.out.println("-> [CONFIG] Configurazione caricata dai resources interni.");
            }
            else {
                System.out.println("-> [CONFIG WARNING] Nessun file config.properties trovato. Verranno usati valori vuoti o default.");
            }
        }
        catch (IOException e) {
            System.err.println("[ERRORE CONFIG] Errore lettura classpath: " + e.getMessage());
        }
    }

    /**
     * Recupera una proprietà come stringa.
     *
     * @param key Chiave della proprietà.
     * @param defaultValue Valore di fallback se la chiave non esiste.
     * @return Il valore trovato o defaultValue.
     */
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}