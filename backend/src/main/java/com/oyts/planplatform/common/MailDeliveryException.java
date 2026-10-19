package com.oyts.planplatform.common;

/** A safe, fixed message only; SMTP exceptions and credentials are never exposed. */
public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException() { super("发件服务暂未接受邮件，请稍后重试或联系管理员。没有确认邮件已投递到收件箱。"); }
}
