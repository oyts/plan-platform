package com.oyts.planplatform.ai;

public class AiFailure extends RuntimeException {
    public enum Code { DISABLED, NOT_IMPLEMENTED, INVALID_CONFIG, BUSY, TIMEOUT, UPSTREAM_UNAVAILABLE, INVALID_OUTPUT }
    private final Code code;
    public AiFailure(Code code) { super("AI 服务暂不可用（" + code.name() + "），已保存的计划不受影响。"); this.code = code; }
    public Code code() { return code; }
}
