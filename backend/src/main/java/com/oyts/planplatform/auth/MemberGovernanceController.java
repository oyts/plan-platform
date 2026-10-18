package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.*;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/governance") @PreAuthorize("hasRole('ADMIN')")
class MemberGovernanceController {
    record Change(@NotNull Long version,boolean enabled,@NotBlank @Size(max=500) String reason){}
    record Member(String id,String email,String displayName,String role,boolean enabled,long version){}
    private final JdbcTemplate db;private final EntityManager em;private final RefreshTokenRepository refresh;private final boolean enabled;
    MemberGovernanceController(JdbcTemplate db,EntityManager em,RefreshTokenRepository refresh,@Value("${app.governance.enabled:false}")boolean enabled){this.db=db;this.em=em;this.refresh=refresh;this.enabled=enabled;}
    @GetMapping("/config") Object config(){return Map.of("enabled",enabled,"mode","NEXT_ONLINE_REQUEST","revokesRefreshSessions",true,"oldTokensReviveOnRestore",false,"offlineIdentityHours",24);}
    @GetMapping("/members") @Transactional(readOnly=true) PageResponse<Member> members(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String search){
        bounds(page,size);if(search.length()>190)throw new IllegalArgumentException("搜索过长。");String query="%"+search.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" where lower(email) like ? escape '!' or lower(display_name) like ? escape '!'";
        long total=db.queryForObject("select count(*) from users"+where,Long.class,query,query);var content=db.query("select id,email,display_name,role,enabled,version from users"+where+" order by created_at desc,id limit ? offset ?",(rs,n)->new Member(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getLong(6)),query,query,size,(long)page*size);
        return new PageResponse<>(content,page,size,total,(int)((total+size-1)/size));
    }
    @PutMapping("/members/{id}") @Transactional Member change(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Change request){
        if(!enabled)throw new ConflictException("成员治理尚未启用，请先由维护者确认停用与令牌策略。");
        UserAccount actor=em.find(UserAccount.class,jwt.getSubject());if(actor==null||!actor.isEnabled()||!actor.getRole().equals("ADMIN"))throw new ForbiddenException("当前管理员身份不可用。");
        UserAccount user=em.find(UserAccount.class,id,LockModeType.PESSIMISTIC_WRITE);if(user==null)throw new NotFoundException("成员不存在。");
        if(user.getId().equals(actor.getId())||!user.getRole().equals("USER"))throw new ConflictException("不能在此停用自己或其他管理员。");
        if(user.getVersion()!=request.version())throw new ConflictException("成员状态已变化，请重新读取。未执行停用或恢复。");
        if(user.isEnabled()!=request.enabled()){
            user.setMemberEnabled(request.enabled());refresh.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(id,Instant.now()).forEach(RefreshToken::revoke);
            db.update("insert into member_governance_audit(id,actor_id,target_id,action,reason,created_at) values (?,?,?,?,?,?)",UUID.randomUUID().toString(),actor.getId(),id,request.enabled()?"RESTORE":"DISABLE",request.reason().trim(),Timestamp.from(Instant.now()));em.flush();
        }
        return new Member(user.getId(),user.getEmail(),user.getDisplayName(),user.getRole(),user.isEnabled(),user.getVersion());
    }
    @GetMapping("/audit") @Transactional(readOnly=true) PageResponse<Map<String,Object>> audit(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){bounds(page,size);long total=db.queryForObject("select count(*) from member_governance_audit",Long.class);var content=db.queryForList("select id,actor_id,target_id,action,reason,created_at from member_governance_audit order by created_at desc,id limit ? offset ?",size,(long)page*size);return new PageResponse<>(content,page,size,total,(int)((total+size-1)/size));}
    private void bounds(int page,int size){if(page<0||page>100000||size<1||size>100)throw new IllegalArgumentException("分页参数无效。");}
}
