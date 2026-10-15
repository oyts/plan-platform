package com.oyts.planplatform.ai;

public class AiFailure extends RuntimeException {
    public enum Code { DISABLED, NOT_IMPLEMENTED, INVALID_CONFIG, BUSY, TIMEOUT, UPSTREAM_UNAVAILABLE, INVALID_OUTPUT }
    private final Code code;
    private final boolean uncertain;
    public AiFailure(Code code) { this(code, code == Code.TIMEOUT || code == Code.UPSTREAM_UNAVAILABLE); }
    public AiFailure(Code code, boolean uncertain) { super("AI 服务暂不可用（" + code.name() + "），已保存的计划不受影响。"); this.code = code; this.uncertain = uncertain; }
    public Code code() { return code; }
    public boolean uncertain() { return uncertain; }
}
