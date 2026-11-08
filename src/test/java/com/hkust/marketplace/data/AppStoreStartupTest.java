package com.hkust.marketplace.data;

import com.hkust.marketplace.testsupport.StoreProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug R3 (docs/regression-log.md).
 *
 * <p><b>Input:</b> a new JVM, started in an empty folder, makes a store with
 * {@code new DataStore(clock)} (as every test does) before anything calls
 * {@code DataStore.getInstance()}.
 * <br><b>Correct output:</b> nothing is written to that folder.
 * <br><b>Output before the fix:</b> {@code marketplace-data/marketplace.dat} (the demo data)
 * appeared in it; during test runs that folder is the project folder.
 *
 * <p>The input runs in a separate JVM ({@link StoreProbe}) because the bug happened only the
 * first time Java loaded the DataStore class, which in this test's JVM has already happened.
 */
class AppStoreStartupTest {

    @TempDir
    Path folder;

    private record ProbeRun(int exitCode, String output) { }

    /** Runs StoreProbe in a new JVM whose working folder is the empty temp folder. */
    private ProbeRun runProbe(String mode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                StoreProbe.class.getName(), mode)
                .directory(folder.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the probe JVM did not finish");
        return new ProbeRun(process.exitValue(), output);
    }

    @Test
    void makingAStore_doesNotTouchTheAppsSaveFolder() throws Exception {
        ProbeRun run = runProbe("new");

        assertEquals(0, run.exitCode(), run.output());
        assertTrue(run.output().contains("new store users=0"), run.output());
        assertFalse(Files.exists(folder.resolve("marketplace-data")),
                "making a store must not create the app's save folder");
    }

    @Test
    void appStore_createsAndSavesTheDemoDataOnTheFirstRun() throws Exception {
        // Control case: shows the check above can see the folder when the app's store really starts
        ProbeRun run = runProbe("app");

        assertEquals(0, run.exitCode(), run.output());
        assertTrue(run.output().contains("app store users=5"), run.output());
        assertTrue(Files.exists(folder.resolve("marketplace-data").resolve("marketplace.dat")));
    }
}
