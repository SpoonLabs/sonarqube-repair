package sorald.sonar;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class JavaInputFile implements ClientInputFile {
    private final Path path;

    JavaInputFile(Path path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path.toString();
    }

    @Override
    public boolean isTest() {
        return false;
    }

    @Override
    public Charset getCharset() {
        return StandardCharsets.UTF_8;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <G> G getClientObject() {
        return (G) path;
    }

    @Override
    public InputStream inputStream() throws IOException {
        return new FileInputStream(path.toFile());
    }

    @Override
    public String contents() throws IOException {
        return Files.readString(path);
    }

    @Override
    public String relativePath() {
        return path.toString();
    }

    @Override
    public URI uri() {
        return path.toUri();
    }

    @Override
    public Language language() {
        return Language.JAVA;
    }
}
