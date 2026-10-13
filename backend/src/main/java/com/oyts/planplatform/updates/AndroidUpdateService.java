package com.oyts.planplatform.updates;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Private, administrator-staged APKs. No static/public download directory. */
@Service
public class AndroidUpdateService {
    public static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    public record Release(String version, int versionCode, int minimumSupportedVersionCode,
                          String sha256, long size, String notes, String packageName) {}
    public record Artifact(Release release, Path path) {}
    private final boolean enabled;
    private final String directory, publicUrl, packageName;
    private final ObjectMapper mapper;
    public AndroidUpdateService(ObjectMapper mapper,
            @Value("${app.android-updates.enabled:false}") boolean enabled,
            @Value("${app.android-updates.directory:}") String directory,
            @Value("${app.public-url:}") String publicUrl,
            @Value("${app.android-updates.package-name:cn.zijun701.plan.app}") String packageName) {
        this.mapper = mapper; this.enabled = enabled; this.directory = directory;
        this.publicUrl = publicUrl; this.packageName = packageName;
    }
    public Artifact latest() { return read(null); }
    public Artifact release(int code) {
        if (code < 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return read(code);
    }
    public String downloadUrl(int code) {
        try {
            URI uri = URI.create(publicUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw unavailable();
            return publicUrl.replaceAll("/$", "") + "/api/client-updates/android/releases/" + code + "/apk";
        } catch (IllegalArgumentException error) { throw unavailable(); }
    }
    private Artifact read(Integer requested) {
        if (!enabled || directory.isBlank()) throw unavailable();
        try {
            Path root = Path.of(directory).toRealPath();
            Path metadata = requested == null ? root.resolve("current.json")
                : root.resolve("releases").resolve(requested.toString()).resolve("release.json");
            if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS) && requested != null)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            Path actualMetadata = safeFile(root, metadata);
            if (Files.size(actualMetadata) > 65536) throw unavailable();
            Release data = mapper.readValue(Files.readAllBytes(actualMetadata), Release.class);
            if (data.version() == null || !data.version().matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
                    || data.versionCode() < 1 || (requested != null && requested != data.versionCode())
                    || data.minimumSupportedVersionCode() < 1 || data.minimumSupportedVersionCode() > data.versionCode()
                    || data.sha256() == null || !data.sha256().matches("[a-f0-9]{64}")
                    || data.size() < 1 || data.size() > MAX_APK_BYTES || !packageName.equals(data.packageName())
                    || data.notes() == null || data.notes().length() > 4000) throw unavailable();
            Path apk = safeFile(root, root.resolve("releases").resolve(Integer.toString(data.versionCode()))
                .resolve("PlanCabin-" + data.version() + "-release.apk"));
            if (Files.size(apk) != data.size()) throw unavailable();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(apk)) {
                byte[] buffer = new byte[32768]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(data.sha256())) throw unavailable();
            return new Artifact(data, apk);
        } catch (ResponseStatusException error) { throw error; }
        catch (Exception error) { throw unavailable(); } // Never disclose disk paths or metadata.
    }
    private static Path safeFile(Path root, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) throw unavailable();
        for (Path parent = normalized.getParent(); !parent.equals(root); parent = parent.getParent())
            if (Files.isSymbolicLink(parent)) throw unavailable();
        Path actual = normalized.toRealPath();
        if (!actual.startsWith(root)) throw unavailable();
        return actual;
    }
    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "受邀更新源尚未就绪，请联系管理员。");
    }
}
