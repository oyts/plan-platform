package com.oyts.planplatform.ai;

public class AiFailure extends RuntimeException {
    public enum Code { DISABLED, NOT_IMPLEMENTED, INVALID_CONFIG, BUSY, TIMEOUT, UPSTREAM_UNAVAILABLE, INVALID_OUTPUT }
    private final Code code;
    private final boolean uncertain;
    public enum Reason { OUTPUT_INVALID, OUTPUT_TRUNCATED, USAGE_MISSING, ENVELOPE_INVALID, DRAFT_INVALID, MODE_MISMATCH, UPSTREAM, TIMEOUT }
    private final Reason reason;
    private final AiProvider.Result candidate;
    public AiFailure(Code code) { this(code, code == Code.TIMEOUT || code == Code.UPSTREAM_UNAVAILABLE); }
    public AiFailure(Code code, boolean uncertain) { this(code,uncertain,code==Code.TIMEOUT?Reason.TIMEOUT:code==Code.INVALID_OUTPUT?Reason.OUTPUT_INVALID:Reason.UPSTREAM,null); }
    public AiFailure(Code code,boolean uncertain,Reason reason,AiProvider.Result candidate){super("AI 服务暂不可用（"+code.name()+"），已保存的计划不受影响。");this.code=code;this.uncertain=uncertain;this.reason=reason;this.candidate=candidate;}
    public Reason reason(){return reason;}
    AiProvider.Result candidate(){return candidate;}
    public Code code() { return code; }
    public boolean uncertain() { return uncertain; }
}
