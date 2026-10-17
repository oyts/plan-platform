package com.oyts.planplatform.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Opaque correlation only; never logs headers, query strings or payloads. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+20)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String id=request.getHeader("X-Request-ID");
        if(id==null||!id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))id=UUID.randomUUID().toString();
        response.setHeader("X-Request-ID",id);chain.doFilter(request,response);
    }
}
