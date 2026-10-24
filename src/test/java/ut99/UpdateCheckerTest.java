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
                        + "\"html_url\":\"https://github.com/omegazim666/UT99browser/releases/tag/B1.6\"}");
        require("B1.6".equals(release.version), "release version");
        require("github.com".equals(release.url.getHost()), "release URL host");
    }

    private static void rejectsInvalidReleaseData() {
        expectInvalid("{\"tag_name\":\"v1.6\",\"html_url\":\"https://github.com/owner/repo\"}");
        expectInvalid("{\"tag_name\":\"B1.6\",\"html_url\":\"https://example.com/release\"}");
        expectInvalid("{\"tag_name\":\"B1.6\"}");
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
