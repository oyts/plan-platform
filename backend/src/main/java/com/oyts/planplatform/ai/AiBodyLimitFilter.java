package com.oyts.planplatform.ai;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class AiBodyLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT=256000;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getRequestURI().startsWith("/api/ai/") || !java.util.Set.of("POST","PUT","PATCH").contains(request.getMethod())) { chain.doFilter(request,response);return; }
        if(request.getContentLengthLong()>LIMIT) { response.sendError(413);return; }
        byte[] body=request.getInputStream().readNBytes(LIMIT+1);
        if(body.length>LIMIT) { response.sendError(413);return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}
                    public void setReadListener(ReadListener listener){} public int read(){return input.read();}
                };
            }
            @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
        },response);
    }
}
