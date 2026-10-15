package com.oyts.planplatform.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class AiSecretReader {
    private AiSecretReader() {}
    /** Called only by a future real provider, never by health or capability endpoints. */
    public static String read(AiProperties config) {
        if (!config.enabled()) throw new AiFailure(AiFailure.Code.DISABLED);
        try {
            Path path = Path.of(config.apiKeyFile());
            if (!path.isAbsolute() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) < 8 || Files.size(path) > 4096)
                throw new AiFailure(AiFailure.Code.INVALID_CONFIG);
            byte[] bytes;
            // Bound actual reads as well as the preflight size (file can change).
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(4097); }
            try {
                String key = new String(bytes, StandardCharsets.UTF_8).strip();
                if (bytes.length > 4096 || !key.matches("[A-Za-z0-9_-]{8,4096}")) throw new AiFailure(AiFailure.Code.INVALID_CONFIG);
                return key;
            } finally { java.util.Arrays.fill(bytes, (byte) 0); }
        } catch (IOException | InvalidPathException failure) { throw new AiFailure(AiFailure.Code.INVALID_CONFIG); }
    }
}
