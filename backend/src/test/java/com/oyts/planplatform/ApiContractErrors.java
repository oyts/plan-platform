package com.oyts.planplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Shared failure families, not a claim that every operation can return every status. */
final class ApiContractErrors {
    static ObjectNode catalog(ObjectMapper json) {
        var shapes=new ApiContractShapes(json,t->{throw new IllegalStateException("No DTO needed");});
        ObjectNode root=json.createObjectNode();
        root.put("description","按实际异常处理器与过滤器区分错误族，不对每个接口承诺全部状态。客户端先读HTTP状态，再按Content-Type安全读取可选message/fields；空正文、容器或反向代理HTML仍须兜底。");
        ObjectNode advice=root.putObject("controllerAdvice");
        advice.set("statuses",json.valueToTree(List.of(400,401,403,404,409,429,503)));
        advice.put("source","common.ApiExceptionHandler");
        advice.set("schema",shapes.object("timestamp",shapes.instant(),"status",shapes.integer(),"message",shapes.str()));
        ObjectNode validation=root.putObject("beanValidation");validation.put("status",400);
        ObjectNode fields=json.createObjectNode().put("type","object");fields.set("additionalProperties",shapes.str());
        validation.set("schema",shapes.object("timestamp",shapes.instant(),"status",shapes.integer(),"message",shapes.str(),"fields",fields));
        validation.put("source","common.ApiExceptionHandler#validation");
        ObjectNode member=root.putObject("memberAccess");member.set("statuses",json.valueToTree(List.of(401,503)));
        member.put("source","auth.MemberAccessFilter");member.set("schema",shapes.object("message",shapes.str()));
        ObjectNode replay=root.putObject("idempotencyConflict");replay.put("status",409);
        replay.put("source","sync.IdempotencyFilter");replay.set("schema",shapes.object("status",shapes.integer(),"message",shapes.str()));
        ObjectNode container=root.putObject("containerOrSecurity");
        container.set("exampleStatuses",json.valueToTree(List.of(400,401,403,413)));
        container.put("bodySchemaGuaranteed",false);
        container.put("description","畸形幂等键sendError(400)、AI/复盘体积拦截sendError(413)、Spring Security认证/授权与容器解析错误不经过上述Advice；正文可能为空或由容器生成。此表不是全部上游错误枚举。");
        return root;
    }
}
