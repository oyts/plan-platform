package com.oyts.planplatform.ai;

/** Remove one enclosing fence only; never extract a substring or change JSON semantics. */
final class AiOutputText {
    private AiOutputText(){}
    static String unwrap(String value){
        String s=value.strip();
        if((s.startsWith("```json\n")||s.startsWith("```\n")) && s.endsWith("\n```")){
            String inner=s.substring(s.indexOf('\n')+1,s.length()-4);
            if(!inner.contains("```"))return inner;
        }
        return value;
    }
}
