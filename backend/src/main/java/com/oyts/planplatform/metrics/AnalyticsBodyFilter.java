package com.oyts.planplatform.metrics;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(-200)
class AnalyticsBodyFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        if(!req.getMethod().equals("POST")||!java.util.Set.of("/api/public/analytics/ticket","/api/public/analytics/event").contains(req.getRequestURI())){chain.doFilter(req,res);return;}
        byte[] bytes=req.getInputStream().readNBytes(1025);
        if(bytes.length>1024){res.setStatus(413);res.setHeader("Cache-Control","no-store");return;}
        chain.doFilter(new HttpServletRequestWrapper(req){@Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}},res);
    }
}
