package org.lightning323.backupAutomation;

import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Command(
        name = "backup_tool",
        mixinStandardHelpOptions = true,
        description = "Backup worlds like a responsible adult 🧃",
        version = "backup 1.0"
)
public class BackupTool implements Callable<Integer> {

    @Option(names = "-y", description = "Just say yes to all prompts")
    boolean justSayYes;

    @Option(
            names = "-n",
            paramLabel = "NAME",
            description = "Custom backup name",
            arity = "1..*"  // Accept 1 or more tokens
    )
    List<String> customBackupNameParts;

    @Option(
            names = "-e",
            split = ",",
            arity = "1..*",
            paramLabel = "ENTRIES",
            description = "Entries to backup (comma-separated)"
    )
    List<String> entriesToBackup = new ArrayList<>();

    @Option(names = "-a", description = "Backup all entries")
    boolean backupAll;


    public static Path getSafeBackupPath(File backupPath, BackupEntry entry, String date, String customBackupName) {
        // Build the raw name
        String rawName = date;
        if (customBackupName != null && !customBackupName.isEmpty()) {
            rawName += " - " + customBackupName;
        }
        // Sanitize: remove invalid characters
        String safeName = rawName.replaceAll("[\\\\/:*?\"<>|]", "_");
        // Construct full path
        return Paths.get(backupPath.getAbsolutePath(), entry.dirName, safeName + ".zip");
    }


    @Override
    public Integer call() throws Exception {

        if (settings.backupEntries.isEmpty()) {
            throw new ParameterException(new CommandLine(this), "No entries found in settings.json");
        }

        // -a overrides everything
        if (backupAll) {
            entriesToBackup = settings.backupEntries.stream()
                    .map(e -> e.dirName.toLowerCase())
                    .toList();
        }

        if (entriesToBackup.isEmpty()) {
            throw new ParameterException(new CommandLine(this), "No entries specified");
        } else {
            System.out.println("Entries to backup: " + entriesToBackup);
        }
        String customBackupName = null;
        if (customBackupNameParts != null) {
            customBackupName = String.join(" ", customBackupNameParts);
            if (customBackupName != null && !customBackupName.isBlank()) {
                System.out.println("Custom backup name: " + customBackupName);
            }
        }

        // Validate entries
        var validNames = settings.backupEntries.stream()
                .map(e -> e.dirName.toLowerCase())
                .toList();

        for (String entry : entriesToBackup) {
            if (!validNames.contains(entry.toLowerCase())) {
                throw new ParameterException(
                        new CommandLine(this),
                        "Entry not found: " + entry + "\nValid entries: " + validNames
                );
            }
        }

        File backupPath = findBackupPath(justSayYes);
        if (backupPath == null) return 1;
        else {
            System.out.println("\nThis is the backup path available:\n" + backupPath);
            if (!confirmation("Do you want to continue?")) return 0;
        }

        String date = getFormattedDateTime();

        for (BackupEntry entry : settings.backupEntries) {
            entry.evaluate();

            if (!entriesToBackup.contains(entry.dirName.toLowerCase())) {
                continue;
            }

            Path destinationZip = getSafeBackupPath(backupPath, entry, date, customBackupName);
            Files.createDirectories(destinationZip.getParent());
            backupDirectory(entry, destinationZip);

            System.out.println("\nUpdating " + entry.dirName + " latest.zip");
            Path latest = Paths.get(
                    backupPath.getAbsolutePath(),
                    entry.dirName + " latest.zip"
            );
            Files.copy(destinationZip, latest, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Done\n\n");
        }

        return 0;
    }

    private boolean confirmation(String message) {
        System.out.println(message.trim() + " (y/n)");
        if (!justSayYes) {
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            if (!input.equalsIgnoreCase("y")) {
                System.err.println("Backup process cancelled");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BackupTool()).execute(args);
        System.exit(exitCode);
    }


    static BackupSettings settings = BackupSettings.load();
    static final int BAR_LENGTH = 30;

    public static String getFormattedDateTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh-mm-ss-a");
        LocalDateTime now = LocalDateTime.now();
        return now.format(formatter);
    }

    /**
     * Displays a single-line progress bar in the console.
     * The bar updates on the same line using the carriage return character '\r'.
     *
     * @param current   The current progress value.
     * @param total     The total value the progress is measured against.
     * @param barLength The desired length of the progress bar in characters.
     */
    public static void printProgressBar(int current, int total, int barLength) {
        // Calculate the progress percentage
        double progress = (double) current / total;
        int filledLength = (int) (barLength * progress);
        int emptyLength = barLength - filledLength;

        // Build the progress bar string
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < filledLength; i++) {
            bar.append("=");
        }
        for (int i = 0; i < emptyLength; i++) {
            bar.append(" ");
        }
        bar.append("]");

        // Format the percentage
        String percentage = String.format("%.1f%%", progress * 100);

        // Print the progress bar, percentage, and current/total,
        // using '\r' to return the cursor to the beginning of the line.
        // We use System.out.print instead of println to avoid new lines.
        System.out.print("\r" + bar.toString() + " " + percentage + " (" + current + "/" + total + ")");

        // If progress is complete, print a new line to ensure the next output
        // doesn't overwrite the final progress bar.
        if (current == total) {
            System.out.println();
        }
    }

    public static void backupDirectory(BackupEntry entry, Path zipPath) throws IOException {
        System.out.println("\nBacking up \"" + entry.dirName + "\" to " + zipPath);
        Path sourceFolderPath = Paths.get(entry.dirPath);
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final AtomicInteger progress = new AtomicInteger(0);
        final AtomicLong totalFiles = new AtomicLong(0);
        final AtomicLong totalBytes = new AtomicLong(0);

        try (var stream = Files.walk(sourceFolderPath)) {
            for (Path p : (Iterable<Path>) stream::iterator) {  // convert Stream to Iterable for for-each
                if (Files.isRegularFile(p)) {
                    totalFiles.incrementAndGet();
                    try {
                        totalBytes.addAndGet(Files.size(p));
                    } catch (IOException e) {
                        // skip unreadable files
                    }
                }
            }
        }
        System.out.printf("Total files: %d, total size: %.2f GB%n", totalFiles.get(), totalBytes.get() / (1024.0 * 1024.0 * 1024.0));

        try (FileOutputStream fos = new FileOutputStream(zipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos);
             Stream<Path> paths = Files.walk(sourceFolderPath)) {

            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    pool.submit(() -> {
                        try {
                            ZipEntry entryZip = new ZipEntry(sourceFolderPath.relativize(path).toString().replace("\\", "/"));
                            synchronized (zos) { // synchronize access to ZipOutputStream
                                zos.putNextEntry(entryZip);
                                Files.copy(path, zos); // copy file content directly
                                zos.closeEntry();
                            }

                            printProgressBar(progress.incrementAndGet(), (int) totalFiles.get(), BAR_LENGTH);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).get(); // wait for completion
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

        } finally {
            pool.shutdown();
        }
    }


    private static boolean allow(BackupEntry entry, Path path, List<String> skipped) {
        if (entry.skipPathRegex == null) return true;
        for (String skipFile : entry.skipPathRegex) {
            Pattern pattern = Pattern.compile(skipFile);
            if (
                    pattern.matcher(path.toString()).matches()
                            || pattern.matcher(path.toString().replace("/", "\\")).matches()
            ) {
                skipped.add(Path.of(entry.dirPath).relativize(path).toString());
                return false;
            }
        }
        return true;
    }


    private static File findBackupPath(boolean justSayYes) {
        if (settings.backupSaveDirs == null || settings.backupSaveDirs.length == 0 || settings.backupSaveDirs[0].isBlank()) {
            throw new RuntimeException("Please set completeBackupsDir in settings.json");
        }
        File backupPath = null;
        for (int i = 0; i < settings.backupSaveDirs.length; i++) {
            backupPath = new File(settings.backupSaveDirs[i]);
            boolean dirMade = backupPath.mkdirs();
            System.out.print("Backup path: " + backupPath);
            if (!Files.exists(backupPath.toPath()) || !Files.isWritable(backupPath.toPath())) {
                System.out.println("\t\t (Not writable)");
                backupPath = null;
            } else {
                System.out.println("\t\t (Is writable)");
                break;
            }
        }
        if (backupPath == null) {
            System.err.println("No backup path is writable");
        }
        return backupPath;
    }
}
