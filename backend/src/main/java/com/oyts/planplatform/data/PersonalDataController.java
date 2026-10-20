package com.oyts.planplatform.data;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/data")
class PersonalDataController {
    private final PersonalDataService service;
    private final ObjectMapper reader=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    PersonalDataController(PersonalDataService service){this.service=service;}
    private <T> ResponseEntity<T> privateReply(T data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
    @GetMapping("/backup") ResponseEntity<?> backup(@AuthenticationPrincipal Jwt jwt){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=plan-cabin-private-backup.json").body(service.backup(jwt.getSubject()));}
    @PostMapping("/preview") ResponseEntity<?> preview(HttpServletRequest request){return privateReply(service.preview(read(request)));}
    @PostMapping("/restore") ResponseEntity<?> restore(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        JsonNode body=read(request);Set<String> fields=new HashSet<>();body.fieldNames().forEachRemaining(fields::add);
        if(!body.isObject()||!fields.equals(Set.of("backup","checksum","operationKey","confirmed"))||!body.path("confirmed").isBoolean()||!body.path("checksum").isTextual()||!body.path("operationKey").isTextual())throw new IllegalArgumentException("请先预检文件并确认恢复为新副本。");
        return privateReply(service.restore(jwt.getSubject(),body.get("backup"),body.path("checksum").asText(),body.path("operationKey").asText(),body.path("confirmed").asBoolean()));
    }
    @GetMapping("/restores") ResponseEntity<?> history(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page){return privateReply(service.history(jwt.getSubject(),page));}
    @GetMapping("/restored-weekly-reviews") ResponseEntity<?> copies(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page){return privateReply(service.weeklyCopies(jwt.getSubject(),page));}
    @GetMapping("/restored-daily-reviews") ResponseEntity<?> dayCopies(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0")int page){return privateReply(service.dailyCopies(jwt.getSubject(),page));}
    @GetMapping("/export/{kind}") ResponseEntity<byte[]> readable(@AuthenticationPrincipal Jwt jwt,@PathVariable String kind){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).header("Content-Disposition","attachment; filename=plan-cabin-"+exportKind(kind)+".csv").body(("\ufeff"+service.readable(jwt.getSubject(),kind)).getBytes(StandardCharsets.UTF_8));
    }
    private String exportKind(String kind){if(!Set.of("routes","todos","weekly","daily").contains(kind))throw new IllegalArgumentException("导出类型不支持。");return kind;}
    private JsonNode read(HttpServletRequest request){
        try{byte[] bytes=request.getInputStream().readNBytes(PersonalDataService.MAX_BYTES+4097);if(bytes.length>PersonalDataService.MAX_BYTES+4096)throw new IllegalArgumentException("请求文件不能超过 5 MiB。");
            JsonNode value=reader.readTree(bytes);if(value==null)throw new IllegalArgumentException("请选择 JSON 备份文件。");return value;
        }catch(IOException failure){throw new IllegalArgumentException("JSON 文件无法读取或包含重复字段，请保留原文件检查。不会恢复任何数据。");}
    }
}
