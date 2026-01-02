package org.lightning323.backupAutomation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class BackupSettings {

    public ArrayList<BackupEntry> backupEntries = new ArrayList<>();
    public String[] backupSaveDirs = new String[]{""};

    public BackupSettings() {
        BackupEntry b = new BackupEntry("dir", "",
                new String[]{
                        ".*.lock$",
                        ".*/icon.png$"
                });
        backupEntries.add(b);
    }

    // === Internal stuff ===
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_PATH = Paths.get(System.getProperty("user.dir"), "backup-settings.json");


    public static void save(BackupSettings settings) {
        try (Writer writer = Files.newBufferedWriter(SETTINGS_PATH)) {
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }

    public static BackupSettings load() {
        if (Files.exists(SETTINGS_PATH)) {
            try (Reader reader = Files.newBufferedReader(SETTINGS_PATH)) {
                return GSON.fromJson(reader, (Type) BackupSettings.class);

            } catch (IOException e) {
                System.err.println("Error loading settings: " + e.getMessage());
            }
        } else {
            save(new BackupSettings()); // Save defaults if no file
        }
        return new BackupSettings();
    }

}
