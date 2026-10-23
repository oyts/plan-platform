package com.oyts.planplatform;

import java.lang.reflect.*;
import java.util.*;

/** Reviewed delegation hints only: retain the real service generic type, never infer from user data. */
final class ApiContractServiceTypes {
    private static final Map<String,Type> TYPES=new HashMap<>();
    static {
        add("AiController","quota","ai.AiQuotaService","usage",1);
        for(String method:List.of("list","create","read","choose"))
            add("AiController",method,"ai.AiConversationService",method,switch(method){case "list"->1;case "create"->3;case "read"->2;default->4;});
        add("AiController","adjust","ai.AiConversationService","createAdjustment",4);
        add("AiController","draft","ai.AiConversationService","saveDraft",4);
        add("AiController","enqueue","ai.AiConversationService","enqueue",5);
        add("AiQuotaController","settings:GET","ai.AiQuotaService","policy",0);
        add("AiQuotaController","settings:PUT","ai.AiQuotaService","changePolicy",3);
        add("AiQuotaController","users","ai.AiQuotaService","members",3);
        add("AiQuotaController","user","ai.AiQuotaService","changeUser",5);
        add("AiQuotaController","audit","ai.AiQuotaService","history",2);
        add("AiUsageController","get","ai.AiUsageService","report",4);
        for(var entry:Map.of("list",1,"read",2,"save",5,"clear",2,"resume",4).entrySet())
            add("AiWorkbenchController",entry.getKey(),"ai.AiWorkbenchService",entry.getKey(),entry.getValue());
        for(var entry:Map.of("routes",5,"copyPreview",2,"copy",2,"batchPreview",2,"batch",2).entrySet())
            add("OrchestrationController",entry.getKey(),"organize.OrchestrationService",entry.getKey(),entry.getValue());
        for(var entry:Map.of("list",6,"read",2,"save",2,"edit",3,"apply",3).entrySet())
            add("PersonalTemplateController",entry.getKey(),"organize.PersonalTemplateService",entry.getKey(),entry.getValue());
        add("PersonalTemplateController","preview","organize.PersonalTemplateService","capturePreview",2);
        add("PersonalTemplateController","use","organize.PersonalTemplateService","usePreview",3);
        for(var entry:Map.of("preview",1,"restore",5,"history",2).entrySet())
            add("PersonalDataController",entry.getKey(),"data.PersonalDataService",entry.getKey(),entry.getValue());
        add("PersonalDataController","copies","data.PersonalDataService","weeklyCopies",2);
        add("PersonalDataController","dayCopies","data.PersonalDataService","dailyCopies",2);
        add("TodoController","page","todo.TodoService","page",8);
        for(var entry:Map.of("facts",3,"items",7,"notes",2,"save",3,"preferences",1,"trend",2,"decide",3,"preview",6).entrySet())
            add("ReflectionController",entry.getKey(),"reflection.ReflectionService",entry.getKey(),entry.getValue());
        add("ReflectionController","preferences:PUT","reflection.ReflectionService","savePreferences",2);
        add("ReflectionController","guide","reflection.ReflectionService","claimGuide",1);
        add("ReflectionController","targets","reflection.ReflectionService","targets",3);
        add("ReflectionController","analyse","ai.AiConversationService","createReview",2);
        add("ReflectionController","analyses","ai.AiConversationService","reviewList",1);
        add("OperationsController","status","operations.OperationsService","status",0);
        add("OperationsController","policy","operations.OperationsService","updatePolicy",2);
        add("OperationsController","mailStatus","operations.OperationsMailDelivery","status",0);
        nested("MailAdminController","status","auth.MailAdminController$Status");
        nested("MailAdminController","test","auth.MailAdminController$Delivery");
        nested("AiController","apply","ai.AiDraftApplier$Applied");
    }
    private static void nested(String controller,String handler,String className){
        try{TYPES.put(controller+"#"+handler,Class.forName("com.oyts.planplatform."+className));}
        catch(ClassNotFoundException e){throw new ExceptionInInitializerError(e);}
    }
    private static void add(String controller,String handler,String service,String method,int argc){
        try{
            var matches=Arrays.stream(Class.forName("com.oyts.planplatform."+service).getDeclaredMethods())
                .filter(m->m.getName().equals(method)&&m.getParameterCount()==argc&&!Modifier.isPrivate(m.getModifiers())).toList();
            if(matches.size()!=1)throw new IllegalStateException("Ambiguous/missing contract delegation: "+service+"#"+method);
            TYPES.put(controller+"#"+handler,matches.getFirst().getGenericReturnType());
        }catch(ClassNotFoundException e){throw new ExceptionInInitializerError(e);}
    }
    static Type response(Class<?> controller,Method handler,String verb){
        String key=controller.getSimpleName()+"#"+handler.getName();
        return TYPES.getOrDefault(key+":"+verb,TYPES.get(key));
    }
    /** Spring patterns can contain nested regex quantifier braces; OpenAPI path variables cannot. */
    static String normalizePath(String springPath){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<springPath.length();i++){
            char c=springPath.charAt(i);if(c!='{'){out.append(c);continue;}
            int start=i+1,end=start,depth=1;boolean escaped=false;
            for(;end<springPath.length();end++){
                char r=springPath.charAt(end);if(escaped){escaped=false;continue;}if(r=='\\'){escaped=true;continue;}
                if(r=='{')depth++;else if(r=='}'&&--depth==0)break;
            }
            if(depth!=0)throw new IllegalArgumentException("Unbalanced Spring path: "+springPath);
            String variable=springPath.substring(start,end);int colon=variable.indexOf(':');
            out.append('{').append(colon<0?variable:variable.substring(0,colon)).append('}');i=end;
        }
        return out.toString();
    }
    private ApiContractServiceTypes(){}
}
