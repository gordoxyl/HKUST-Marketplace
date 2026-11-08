package com.hkust.marketplace.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

/**
 * Saves the data as JSON, encrypted with {@link DataCipher}, in one file; deleted-account
 * snapshots go to encrypted {@code .txt} files in a folder next to it:
 * <pre>
 * marketplace-data/
 *   marketplace.dat           all data (encrypted JSON)
 *   marketplace-audit/G12.txt  data snapshot of audit log entry G12 (encrypted)
 * </pre>
 * Each save writes a temporary file first and then replaces the old file, so a crash while
 * saving cannot leave a half-written file. A save is skipped when nothing has changed.
 */
public class EncryptedJsonStorage implements Storage {

    private final Path dataFile;
    private final Path auditFolder;
    private final DataCipher cipher;
    private final ObjectMapper mapper = JsonMapping.newMapper();

    /** The JSON last saved or loaded, so unchanged data is not written again. */
    private byte[] lastSavedJson;

    /** Storage in the given file, using the built-in key. */
    public EncryptedJsonStorage(Path dataFile) {
        this(dataFile, DataCipher.defaultCipher());
    }

    public EncryptedJsonStorage(Path dataFile, DataCipher cipher) {
        this.dataFile = dataFile.toAbsolutePath();
        this.cipher = cipher;
        String baseName = this.dataFile.getFileName().toString().replaceFirst("\\.dat$", "");
        this.auditFolder = this.dataFile.resolveSibling(baseName + "-audit");
    }

    public Path getDataFile() {
        return dataFile;
    }

    public Path getAuditFolder() {
        return auditFolder;
    }

    @Override
    public synchronized Optional<StoreSnapshot> load() {
        if (!Files.exists(dataFile)) {
            return Optional.empty();
        }
        byte[] json = cipher.decrypt(readAll(dataFile));
        try {
            StoreSnapshot snapshot = mapper.readValue(json, StoreSnapshot.class);
            lastSavedJson = json;
            return Optional.of(snapshot);
        } catch (IOException e) {
            throw new StorageException("The save file could not be read: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void save(StoreSnapshot snapshot) {
        byte[] json;
        try {
            json = mapper.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException e) {
            throw new StorageException("The data could not be prepared for saving: " + e.getMessage(), e);
        }
        if (Arrays.equals(json, lastSavedJson)) {
            return; // nothing changed since the last save
        }
        writeEncrypted(dataFile, json);
        lastSavedJson = json;
    }

    @Override
    public synchronized String setAsideUnreadable() {
        if (!Files.exists(dataFile)) {
            return "there was no save file";
        }
        // The real time is fine here: it only names the file that is moved aside
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path aside = dataFile.resolveSibling(dataFile.getFileName() + ".unreadable-" + stamp);
        try {
            Files.move(dataFile, aside);
        } catch (IOException e) {
            throw new StorageException("Could not move the unreadable save file " + dataFile + ": " + e.getMessage(), e);
        }
        lastSavedJson = null;
        return aside.toString();
    }

    @Override
    public synchronized void saveAuditSnapshot(String logReference, String snapshot) {
        writeEncrypted(auditFile(logReference), snapshot.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized Optional<String> loadAuditSnapshot(String logReference) {
        Path file = auditFile(logReference);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(new String(cipher.decrypt(readAll(file)), StandardCharsets.UTF_8));
    }

    private Path auditFile(String logReference) {
        // Log references are DataStore ids such as "G12"; anything else could point outside the folder
        if (!logReference.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Not an audit log reference: " + logReference);
        }
        return auditFolder.resolve(logReference + ".txt");
    }

    private void writeEncrypted(Path target, byte[] plain) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, cipher.encrypt(plain));
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Could not save " + target + ": " + e.getMessage(), e);
        }
    }

    private static byte[] readAll(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new StorageException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
