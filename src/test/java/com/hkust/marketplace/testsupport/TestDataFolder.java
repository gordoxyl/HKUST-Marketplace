package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Gives every test run an empty data folder of its own, so that the tests which open the app's
 * screens use the app's store ({@code DataStore.getInstance()}) without reading or changing the
 * data saved in the project folder (regression log R3). In that folder the app's store starts
 * with the demo data, as on a first run.
 *
 * <p>JUnit calls this before the first test of a run, in IntelliJ and in Maven alike, because
 * {@code src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener}
 * names it. The folder is in the computer's temporary folder and is deleted when the run ends.
 */
public class TestDataFolder implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        useNewFolder();
    }

    /**
     * Points the app's store at a new temporary folder, once per run: IntelliJ opens one session
     * to find the tests and another to run them, and both must use the same folder.
     */
    private static synchronized void useNewFolder() {
        if (System.getProperty(DataStore.DATA_FOLDER_PROPERTY) != null) {
            return;
        }
        try {
            Path folder = Files.createTempDirectory("marketplace-test-data-");
            System.setProperty(DataStore.DATA_FOLDER_PROPERTY, folder.toString());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> delete(folder)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not make a data folder for the tests", e);
        }
    }

    /** Deletes the folder and what the run saved in it, files first. */
    private static void delete(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Only a temporary folder is left behind, for the system to clear
        }
    }
}
