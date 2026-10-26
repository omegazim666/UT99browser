package ut99;

import java.io.IOException;

public final class UpdateCheckerTest {
    public static void main(String[] args) throws Exception {
        detectsNewerVersions();
        parsesLatestRelease();
        rejectsInvalidReleaseData();
        System.out.println("Update checker tests passed.");
    }

    private static void detectsNewerVersions() throws IOException {
        require(UpdateChecker.isNewerVersion("b1.5", "B1.6"), "newer minor version");
        require(UpdateChecker.isNewerVersion("B1.9", "B2.0"), "newer major version");
        require(!UpdateChecker.isNewerVersion("B1.5", "B1.5"), "same version");
        require(!UpdateChecker.isNewerVersion("B2.0", "B1.9"), "older release");
    }

    private static void parsesLatestRelease() throws IOException {
        UpdateChecker.Release release = UpdateChecker.parseRelease(
                "{\"tag_name\":\"B1.6\","
                        + "\"assets\":[{\"name\":\"UT99_Browser-B1.6.zip\","
                        + "\"browser_download_url\":\"https://github.com/omegazim666/UT99browser/"
                        + "releases/download/B1.6/UT99_Browser-B1.6.zip\","
                        + "\"digest\":\"sha256:187b5ddf73107f50a1c37ae8f4b0ff5f6cf922602b70d359636aef9d3b18b253\","
                        + "\"size\":1234}]}");
        require("B1.6".equals(release.version), "release version");
        require("github.com".equals(release.downloadUrl.getHost()), "release URL host");
        require(release.assetSize == 1234, "release asset size");
    }

    private static void rejectsInvalidReleaseData() {
        expectInvalid("{\"tag_name\":\"v1.6\",\"assets\":[]}");
        expectInvalid("{\"tag_name\":\"B1.6\",\"assets\":[{\"name\":\"UT99_Browser-B1.6.zip\","
                + "\"browser_download_url\":\"https://example.com/release\","
                + "\"digest\":\"sha256:187b5ddf73107f50a1c37ae8f4b0ff5f6cf922602b70d359636aef9d3b18b253\","
                + "\"size\":1234}]}");
        expectInvalid("{\"tag_name\":\"B1.6\",\"assets\":[]}");
        expectInvalid("not json");
    }

    private static void expectInvalid(String json) {
        try {
            UpdateChecker.parseRelease(json);
            throw new AssertionError("Accepted invalid release data: " + json);
        } catch (IOException expected) {
            // Expected invalid release data.
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
