package ut99;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.File;
import java.nio.file.Path;

public final class AboutImageAssetsTest {
    public static void main(String[] args) throws Exception {
        Image gameLogo = readImage("src\\img\\Giblogo-mirror.gif");
        Image discordIcon = readImage("src\\img\\discord.png");

        require(gameLogo.getWidth(null) == 550 && gameLogo.getHeight(null) == 162,
                "game logo dimensions");
        require(gameLogo.getWidth(null) / 2 == 275 && gameLogo.getHeight(null) / 2 == 81,
                "displayed game logo dimensions");
        require(discordIcon.getWidth(null) == 3000 && discordIcon.getHeight(null) == 3000,
                "Discord icon dimensions");
        System.out.println("About image asset tests passed.");
    }

    private static Image readImage(String path) throws Exception {
        File file = Path.of(path).toFile();
        Image image = ImageIO.read(file);
        require(image != null, "image can be read: " + path);
        return image;
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
