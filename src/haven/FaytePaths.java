package haven;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FaytePaths {
    public static File home() {
        String h = System.getProperty("fayte.home");
        return h != null ? new File(h) : new File(System.getProperty("user.home"), "Salem");
    }

    public static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        Files.write(tmp.toPath(), data);
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static File fayte() {
        return new File(home(), "fayte");
    }

    public static File entries() {
        return new File(fayte(), "entries");
    }

    public static File images() {
        return new File(entries(), "images");
    }

    public static File icons() {
        return new File(fayte(), "icons");
    }

    public static File wikiimages() {
        return new File(fayte(), "wiki_images");
    }

    public static String safename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
