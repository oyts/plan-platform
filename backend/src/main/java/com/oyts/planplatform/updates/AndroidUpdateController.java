package com.oyts.planplatform.updates;

import com.oyts.planplatform.auth.UserAccountRepository;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/client-updates/android")
public class AndroidUpdateController {
    private final AndroidUpdateService updates;
    private final UserAccountRepository users;
    public AndroidUpdateController(AndroidUpdateService updates, UserAccountRepository users) {
        this.updates = updates; this.users = users;
    }
    private void requireAccount(Jwt jwt) {
        if (jwt == null || !users.existsById(jwt.getSubject())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    @GetMapping("/latest")
    ResponseEntity<Map<String, Object>> latest(@AuthenticationPrincipal Jwt jwt) {
        requireAccount(jwt);
        var release = updates.latest().release();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .body(Map.of("version", release.version(), "versionCode", release.versionCode(),
                "minimumSupportedVersionCode", release.minimumSupportedVersionCode(), "sha256", release.sha256(),
                "size", release.size(), "notes", release.notes(), "packageName", release.packageName(),
                "url", updates.downloadUrl(release.versionCode()), "accountId", jwt.getSubject()));
    }
    @GetMapping("/releases/{versionCode}/apk")
    ResponseEntity<Resource> apk(@AuthenticationPrincipal Jwt jwt, @PathVariable int versionCode) {
        requireAccount(jwt);
        var artifact = updates.release(versionCode);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
            .contentLength(artifact.release().size()).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.PRAGMA, "no-cache")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PlanCabin-" + artifact.release().version() + "-release.apk\"")
            .body(new FileSystemResource(artifact.path()));
    }
}
