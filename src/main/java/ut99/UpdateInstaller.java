package ut99;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

final class UpdateInstaller {
    private static final String LAUNCHER_NAME = "UT99 Browser.bat";
    private static final String UPDATER_NAME = "update-windows.ps1";

    private UpdateInstaller() {
    }

    static boolean isSupported() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            return false;
        }
        try {
            Path installRoot = installRoot();
            return Files.isDirectory(installRoot.resolve("runtime"))
                    && Files.isDirectory(installRoot.resolve("app"))
                    && Files.isRegularFile(installRoot.resolve(LAUNCHER_NAME))
                    && Files.isRegularFile(installRoot.resolve(UPDATER_NAME));
        } catch (IOException | URISyntaxException e) {
            return false;
        }
    }

    static void start(Path archive, long processId) throws IOException {
        final Path installRoot;
        try {
            installRoot = installRoot();
        } catch (URISyntaxException e) {
            throw new IOException("Could not locate the installed application.", e);
        }
        Path updater = installRoot.resolve(UPDATER_NAME);
        if (!Files.isRegularFile(updater)) {
            throw new IOException("The Windows update helper is missing from this installation.");
        }

        ProcessBuilder process = new ProcessBuilder("powershell.exe", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File",
                updater.toString(), "-ProcessId", String.valueOf(processId),
                "-Archive", archive.toAbsolutePath().toString(),
                "-InstallRoot", installRoot.toString());
        process.directory(Path.of(System.getProperty("java.io.tmpdir")).toFile());
        process.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        process.redirectError(ProcessBuilder.Redirect.DISCARD);
        process.start();
    }

    private static Path installRoot() throws IOException, URISyntaxException {
        Path classesDirectory = Path.of(
                UpdateInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return classesDirectory.toAbsolutePath().normalize().getParent();
    }
}
