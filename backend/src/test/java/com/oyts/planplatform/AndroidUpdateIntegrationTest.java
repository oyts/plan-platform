package com.oyts.planplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.android-updates.enabled=true", "app.public-url=https://plan.example.test"})
@AutoConfigureMockMvc
class AndroidUpdateIntegrationTest {
    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("plan-invited-update-test-").toRealPath(); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    static final String LATEST = "/api/client-updates/android/latest", APK = "/api/client-updates/android/releases/10/apk";
    static final byte[] BYTES = "Synthetic API fixture; not an Android installation package".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.android-updates.directory", () -> ROOT.toString()); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;
    private Map<String, Object> metadata(String version, String hash) {
        return Map.of("version", version, "versionCode", 10, "minimumSupportedVersionCode", 9,
            "sha256", hash, "size", BYTES.length, "notes", "合成 API 验收数据", "packageName", "cn.zijun701.plan.app");
    }
    private String hash() throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BYTES)); }
    @BeforeEach void fixture() throws Exception {
        Files.createDirectories(ROOT.resolve("releases/10"));
        Files.write(ROOT.resolve("releases/10/PlanCabin-0.1.6-release.apk"), BYTES);
        byte[] data = mapper.writeValueAsBytes(metadata("0.1.6", hash()));
        Files.write(ROOT.resolve("current.json"), data); Files.write(ROOT.resolve("releases/10/release.json"), data);
    }
    private String token() throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", "update-" + UUID.randomUUID() + "@example.test", "password", "UpdateAcceptance123!", "displayName", "更新接口验收"));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    @Test void anonymousAndInvalidBearerCannotReadManifestOrApk() throws Exception {
        mvc.perform(get(LATEST)).andExpect(status().isUnauthorized());
        mvc.perform(get(APK)).andExpect(status().isUnauthorized());
        mvc.perform(get(LATEST).header("Authorization", "Bearer invalid.token.value")).andExpect(status().isUnauthorized());
        mvc.perform(get(APK).header("Authorization", "Bearer invalid.token.value")).andExpect(status().isUnauthorized());
    }
    @Test void manifestIsAccountBoundAndNeverCachedPublicly() throws Exception {
        String token = token(), id = decoder.decode(token).getSubject();
        mvc.perform(get(LATEST).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.accountId").value(id)).andExpect(jsonPath("$.version").value("0.1.6"))
            .andExpect(jsonPath("$.minimumSupportedVersionCode").value(9))
            .andExpect(jsonPath("$.url").value("https://plan.example.test" + APK))
            .andExpect(jsonPath("$.sha256").value(hash()));
    }
    @Test void ordinaryAccountsCanDownloadExactBytesButUnknownVersionsFail() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(get(APK).header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(content().contentType("application/vnd.android.package-archive")).andExpect(content().bytes(BYTES));
        mvc.perform(get("/api/client-updates/android/releases/999/apk").header("Authorization", "Bearer " + token())).andExpect(status().isNotFound());
    }
    @Test void corruptedFileOrMalformedMetadataNeverGetsDelivered() throws Exception {
        String token = token();
        Files.write(ROOT.resolve("releases/10/PlanCabin-0.1.6-release.apk"), new byte[BYTES.length]);
        mvc.perform(get(LATEST).header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
        mvc.perform(get(APK).header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
        fixture(); Files.write(ROOT.resolve("current.json"), mapper.writeValueAsBytes(metadata("../outside", hash())));
        mvc.perform(get(LATEST).header("Authorization", "Bearer " + token)).andExpect(status().isServiceUnavailable());
    }
    @Test void disabledChannelDoesNotExposeArtifacts() {
        var disabled = new com.oyts.planplatform.updates.AndroidUpdateService(mapper, false, ROOT.toString(), "https://plan.example.test", "cn.zijun701.plan.app");
        assertThrows(org.springframework.web.server.ResponseStatusException.class, disabled::latest);
    }
    @AfterAll static void cleanup() throws Exception {
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), ROOT.getParent());
        assertTrue(ROOT.getFileName().toString().startsWith("plan-invited-update-test-"));
        try (var paths = Files.walk(ROOT)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
}
