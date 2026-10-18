package com.oyts.planplatform.operations;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.common.*;
import jakarta.validation.constraints.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
class OperationsService {
    record Policy(@Min(2) @Max(100) int keepNewest,@Min(7) @Max(3650) int retentionDays,@Min(6) @Max(168) int backupMaxHours,@Min(5) @Max(80) int diskMinPercent,@Min(0) long version){}
    record Backup(String name,Instant completedAt,long bytes,String sha256,boolean localVerified){}
    record Check(String code,String message,boolean ok){}
    record Cleanup(String name,boolean candidate,String reason){}
    record Proof(String backupName,Instant verifiedAt,boolean verified){}
    record Status(boolean enabled,boolean mailAlertsEnabled,boolean dataAvailable,Instant observedAt,Policy policy,List<Check> checks,List<Backup> backups,List<Cleanup> cleanupPreview,Proof oss,Proof restore,long diskFreeBytes,long diskTotalBytes){}
    private final JdbcTemplate db;private final ObjectMapper mapper;private final boolean enabled;private final String statusFile;
    OperationsService(JdbcTemplate db,ObjectMapper mapper,@Value("${app.operations.enabled:false}")boolean enabled,@Value("${app.operations.status-file:}")String statusFile){this.db=db;this.mapper=mapper;this.enabled=enabled;this.statusFile=statusFile;}
    Policy policy(){return db.queryForObject("select keep_newest,retention_days,backup_max_hours,disk_min_percent,version from operations_policy where id=1",(rs,n)->new Policy(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getLong(5)));}
    Status status(){
        var policy=policy();if(!enabled||statusFile.isBlank())return unavailable(policy,"运维采集尚未启用或未配置；未知不代表备份成功。");
        try{
            Path file=Path.of(statusFile);if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>65536)return unavailable(policy,"运维状态文件不可用，未读取其他文件。");
            JsonNode root=mapper.readTree(Files.readString(file));Instant observed=instant(root,"observedAt");
            if(root.path("schemaVersion").asInt()!=1||observed==null||observed.isAfter(Instant.now().plusSeconds(300))||observed.isBefore(Instant.now().minus(Duration.ofHours(24))))return unavailable(policy,"运维采集已过期或格式无效，请检查采集任务。");
            if(!root.path("backups").isArray()||root.path("backups").size()>200)throw new IllegalArgumentException();
            List<Backup> backups=new ArrayList<>();for(JsonNode row:root.path("backups")){
                String name=row.path("name").asText();Instant completed=instant(row,"completedAt");String sha=row.path("sha256").asText("");long bytes=row.path("bytes").asLong(-1);
                if(!name.matches("(?:plan_platform|plan_acceptance_[0-9a-f_]+)-mysql-[0-9]{8}-[0-9]{6}\\.sql\\.gz")||completed==null||completed.isAfter(observed.plusSeconds(300))||bytes<0||(!sha.isEmpty()&&!sha.matches("[a-f0-9]{64}")))throw new IllegalArgumentException();
                backups.add(new Backup(name,completed,bytes,sha,row.path("localVerified").asBoolean(false)&&!sha.isEmpty()));
            }
            backups.sort(Comparator.comparing(Backup::completedAt).reversed().thenComparing(Backup::name));
            var verified=backups.stream().filter(Backup::localVerified).toList();Backup latest=verified.isEmpty()?null:verified.getFirst();List<Check> checks=new ArrayList<>();
            checks.add(new Check("BACKUP_STALE",latest==null?"没有已校验的本地备份。":latest.completedAt().isBefore(Instant.now().minus(Duration.ofHours(policy.backupMaxHours())))?"最近已校验备份超过设定时限。":"最近本地备份在设定时限内。",latest!=null&&!latest.completedAt().isBefore(Instant.now().minus(Duration.ofHours(policy.backupMaxHours())))));
            JsonNode oss=root.path("oss"),drill=root.path("drill");Instant ossTime=instant(oss,"verifiedAt"),drillTime=instant(drill,"completedAt");String ossSha=oss.path("sha256").asText(),drillSha=drill.path("sha256").asText();
            boolean ossVerified=oss.path("verified").asBoolean(false)&&ossTime!=null&&!ossTime.isAfter(observed.plusSeconds(300))&&verified.stream().anyMatch(b->b.sha256().equals(ossSha));
            boolean restored=drill.path("matched").asBoolean(false)&&drillTime!=null&&!drillTime.isAfter(observed.plusSeconds(300))&&verified.stream().anyMatch(b->b.sha256().equals(drillSha));
            checks.add(new Check("OSS_UNVERIFIED",ossVerified&&latest!=null&&latest.sha256().equals(ossSha)?"最新本地备份与 OSS 下载副本的 SHA-256 已核对。":"最新备份尚无对应 OSS 下载校验证据；上传成功不冒充恢复校验。",ossVerified&&latest!=null&&latest.sha256().equals(ossSha)));
            boolean recentRestore=restored&&!drillTime.isBefore(Instant.now().minus(Duration.ofDays(30)));
            checks.add(new Check("RESTORE_UNVERIFIED",recentRestore?"保留的本地备份有最近 30 天的独立恢复全表校验证据。":"没有最近 30 天可对应到保留备份的恢复演练证据。",recentRestore));
            long total=root.path("disk").path("totalBytes").asLong(-1),free=root.path("disk").path("freeBytes").asLong(-1);boolean diskValid=total>0&&free>=0&&free<=total;
            checks.add(new Check("DISK_LOW",diskValid?"主机备份目录所在磁盘可用比例 "+String.format(Locale.ROOT,"%.1f",free*100.0/total)+"%。":"磁盘状态未知。",diskValid&&free*100.0/total>=policy.diskMinPercent()));
            boolean catalog=root.path("catalogComplete").asBoolean(false);checks.add(new Check("CATALOG_INCOMPLETE",catalog?"备份目录清单在采集上限内。":"目录清单不完整，所有清理建议锁定。",catalog));
            for(String code:List.of("BACKUP_FAILED","OSS_FAILED","RESTORE_FAILED"))if(root.path("failures").isArray()&&java.util.stream.StreamSupport.stream(root.path("failures").spliterator(),false).anyMatch(n->n.asText().equals(code)))checks.add(new Check(code,"最近一次受管操作报告失败，请查看受限服务器日志。",false));
            Set<String> protectedNames=new HashSet<>();backups.stream().limit(policy.keepNewest()).forEach(b->protectedNames.add(b.name()));verified.stream().limit(2).forEach(b->protectedNames.add(b.name()));
            List<Cleanup> preview=new ArrayList<>();for(Backup backup:backups){String reason;
                if(!catalog||!recentRestore||!ossVerified)reason="缺少完整清单、近期恢复或 OSS 校验证据，保留。";
                else if(protectedNames.contains(backup.name())||backup.sha256().equals(drillSha)||backup.sha256().equals(ossSha))reason="最近备份或已证明的恢复点，保留。";
                else if(!backup.localVerified())reason="未校验文件不纳入自动清理建议，保留。";
                else if(!backup.completedAt().isBefore(Instant.now().minus(Duration.ofDays(policy.retentionDays()))))reason="仍在保留天数内，保留。";
                else reason="超过保留天数且不属于受保护恢复点；仅建议，尚未删除。";
                preview.add(new Cleanup(backup.name(),reason.startsWith("超过保留天数"),reason));
            }
            return new Status(true,false,true,observed,policy,List.copyOf(checks),List.copyOf(backups),List.copyOf(preview),new Proof(verified.stream().filter(b->b.sha256().equals(ossSha)).map(Backup::name).findFirst().orElse(null),ossTime,ossVerified),new Proof(verified.stream().filter(b->b.sha256().equals(drillSha)).map(Backup::name).findFirst().orElse(null),drillTime,restored),diskValid?free:-1,diskValid?total:-1);
        }catch(Exception invalid){return unavailable(policy,"运维状态无法安全读取，备份与清理状态均按未知处理。");}
    }
    @Transactional Policy updatePolicy(String actor,Policy request){
        var before=policy();int changed=db.update("update operations_policy set keep_newest=?,retention_days=?,backup_max_hours=?,disk_min_percent=?,version=version+1 where id=1 and version=?",request.keepNewest(),request.retentionDays(),request.backupMaxHours(),request.diskMinPercent(),request.version());
        if(changed!=1)throw new ConflictException("运维规则已被修改，请重新读取；未执行清理。");
        try{db.update("insert into operations_policy_audit(id,actor_id,previous_version,new_version,before_json,after_json,created_at) values (?,?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,before.version(),before.version()+1,mapper.writeValueAsString(before),mapper.writeValueAsString(policy()),Timestamp.from(Instant.now()));}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("规则审计保存失败，变更已回滚。",error);}return policy();
    }
    @Transactional List<Map<String,Object>> evaluate(){
        if(!enabled)throw new ConflictException("运维尚未启用。不会发送告警邮件或进行清理。");db.queryForList("select id from operations_policy where id=1 for update");var status=status();Instant now=Instant.now();Set<String> active=new HashSet<>();
        for(Check check:status.checks())if(!check.ok()){
            active.add(check.code());var rows=db.queryForList("select active from operations_alerts where code=?",check.code());
            if(rows.isEmpty())db.update("insert into operations_alerts(code,incident_id,active,first_seen,last_seen) values (?,?,true,?,?)",check.code(),UUID.randomUUID().toString(),Timestamp.from(now),Timestamp.from(now));
            else if(db.queryForObject("select active from operations_alerts where code=?",Boolean.class,check.code()))db.update("update operations_alerts set last_seen=? where code=?",Timestamp.from(now),check.code());
            else db.update("update operations_alerts set incident_id=?,active=true,first_seen=?,last_seen=?,resolved_at=null where code=?",UUID.randomUUID().toString(),Timestamp.from(now),Timestamp.from(now),check.code());
        }
        for(var row:db.queryForList("select code from operations_alerts where active=true")){String code=(String)row.get("code");if(!active.contains(code))db.update("update operations_alerts set active=false,resolved_at=?,last_seen=? where code=?",Timestamp.from(now),Timestamp.from(now),code);}
        return alerts();
    }
    List<Map<String,Object>> alerts(){return db.queryForList("select code,incident_id,active,first_seen,last_seen,resolved_at from operations_alerts order by active desc,last_seen desc,code");}
    com.oyts.planplatform.plan.PlanDtos.PageResponse<Map<String,Object>> policyAudit(int page){if(page<0||page>100000)throw new IllegalArgumentException("页码无效。");long count=db.queryForObject("select count(*) from operations_policy_audit",Long.class);return new com.oyts.planplatform.plan.PlanDtos.PageResponse<>(db.queryForList("select id,actor_id,previous_version,new_version,before_json,after_json,created_at from operations_policy_audit order by created_at desc,id limit 10 offset ?",(long)page*10),page,10,count,(int)((count+9)/10));}
    private Status unavailable(Policy policy,String message){return new Status(enabled,false,false,null,policy,List.of(new Check("OPS_DATA_UNAVAILABLE",message,false)),List.of(),List.of(),new Proof(null,null,false),new Proof(null,null,false),-1,-1);}
    private Instant instant(JsonNode node,String key){try{return Instant.parse(node.path(key).asText());}catch(Exception error){return null;}}
}
