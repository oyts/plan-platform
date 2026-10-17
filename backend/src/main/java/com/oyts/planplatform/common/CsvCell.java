package com.oyts.planplatform.common;

public final class CsvCell {
    private CsvCell(){}
    public static String encode(String value){
        String text=value==null?"":value;
        // Spreadsheet formulas may follow leading spaces or control characters.
        String first=text.stripLeading();
        if(!first.isEmpty()&&"=+-@\t\r\n".indexOf(first.charAt(0))>=0||!text.isEmpty()&&"\t\r\n".indexOf(text.charAt(0))>=0)text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
}
