package org.lightning323.backupAutomation;

public class BackupEntry {
    public String dirName;
    public String dirPath;
    public String[] skipPathRegex;

    public BackupEntry(String dirName, String dirPath, String[] skipFiles) {
        this.dirName = dirName;
        this.dirPath = dirPath;
        this.skipPathRegex = skipFiles;
    }

    public void evaluate() {
        if (dirPath == null || dirPath.isBlank()) {
            throw new RuntimeException("Please set completeWorldDir in settings.json");
        }
        if (dirName == null || dirName.isBlank()) {
            throw new RuntimeException("Please set worldName in settings.json");
        }
    }
}
