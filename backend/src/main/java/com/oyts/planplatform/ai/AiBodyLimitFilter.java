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
        if(!(request.getRequestURI().startsWith("/api/ai/")||request.getRequestURI().startsWith("/api/reflection/")) || !java.util.Set.of("POST","PUT","PATCH").contains(request.getMethod())) { chain.doFilter(request,response);return; }
        String path=request.getRequestURI();
        // A 128k-character draft or 160k-character collaboration can exceed 256k
        // UTF-8 bytes. Include JSON string escaping, but keep admission bounded.
        boolean largeDraft=path.matches("/api/ai/sessions/[^/]+/(draft|batch|collaboration/(preview|accept))");
        int limit=largeDraft?1048576:path.startsWith("/api/ai/saved-drafts/")?524288:LIMIT;
        if(request.getContentLengthLong()>limit) { response.sendError(413);return; }
        byte[] body=request.getInputStream().readNBytes(limit+1);
        if(body.length>limit) { response.sendError(413);return; }
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
