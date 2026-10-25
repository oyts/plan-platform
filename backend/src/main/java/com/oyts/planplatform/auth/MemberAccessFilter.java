package com.oyts.planplatform.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Persistent generation protects previously governed accounts even if the administrative switch is later off. */
public class MemberAccessFilter extends OncePerRequestFilter {
    private final JdbcTemplate db;private final boolean enabled;
    public MemberAccessFilter(JdbcTemplate db,boolean enabled){this.db=db;this.enabled=enabled;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication instanceof JwtAuthenticationToken token&&request.getRequestURI().startsWith("/api/")){
            boolean allowed,adminAllowed;
            try{
                adminAllowed=!request.getRequestURI().startsWith("/api/admin/")||db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='ADMIN'",Integer.class,token.getName())==1;
                var rows=db.query("select enabled,auth_generation from users where id=?",(rs,n)->new long[]{rs.getBoolean(1)?1:0,rs.getLong(2)},token.getName());
                if(rows.isEmpty())allowed=false;else{var row=rows.getFirst();Object claim=token.getToken().getClaims().get("accountGeneration");long generation=claim instanceof Number value&&value.doubleValue()==value.longValue()?value.longValue():claim==null?0:-1;allowed=!enabled&&row[1]==0||row[0]==1&&row[1]==generation;}
            }catch(org.springframework.dao.DataAccessException failure){response.setStatus(503);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"账号状态校验暂不可用，请稍后重试。\"}");return;}
            if(!allowed){SecurityContextHolder.clearContext();response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"账号已停用或会话已撤销，请重新登录或联系管理员。\"}");return;}
            if(!adminAllowed){response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"当前账号没有管理权限，请返回个人工作区。\"}");return;}
        }
        chain.doFilter(request,response);
    }
}
