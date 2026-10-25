package com.oyts.planplatform.ai;

/** No implementation invokes a paid provider in this foundation stage. */
public interface AiProvider {
    record Request(String message, int maxOutputTokens,int contextLimit,int timeoutSeconds) {
        public Request(String message,int maxOutputTokens){this(message,maxOutputTokens,0,0);}
        public Request {
            if (message == null || message.isBlank() || message.length() > 32000 || maxOutputTokens < 128 || maxOutputTokens > 8192)
                throw new IllegalArgumentException("AI 输入或输出长度限制无效。");
            if(!(contextLimit==0&&timeoutSeconds==0)&&(contextLimit<100||contextLimit>32000||timeoutSeconds<1||timeoutSeconds>120))throw new IllegalArgumentException("AI执行快照无效。");
        }
        @Override public String toString() { return "AiRequest[content withheld]"; }
    }
    record Result(String text, long inputTokens, long outputTokens) {
        @Override public String toString() { return "AiResult[content withheld]"; }
    }
    Result generate(Request request);
}
