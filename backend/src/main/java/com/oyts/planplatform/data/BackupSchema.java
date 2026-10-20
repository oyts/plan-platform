package com.oyts.planplatform.data;

import java.util.*;

/** Versioned file schema, not database introspection. SQL names come only from this catalog. */
final class BackupSchema {
    enum Kind { STRING, ID, REFERENCE, BATCH, INTEGER, BOOLEAN, DATE, TIME, INSTANT, STRINGS, BOOLEANS, IDS }
    record Field(String name,Kind kind,boolean nullable,int max,boolean nonblank,String reference,long min,long upper) {}
    record Table(String name,String target,String from,boolean owned,List<Field> fields) {}
    private static Field s(String name,int max){return new Field(name,Kind.STRING,false,max,false,null,0,0);}
    private static Field title(String name,int max){return new Field(name,Kind.STRING,false,max,true,null,0,0);}
    private static Field optionalText(String name,int max){return new Field(name,Kind.STRING,true,max,false,null,0,0);}
    private static Field f(String name,Kind kind,boolean nullable){return new Field(name,kind,nullable,0,false,null,0,0);}
    private static Field ref(String name,String table,boolean nullable){return new Field(name,Kind.REFERENCE,nullable,36,false,table,0,0);}
    private static Field integer(String name,long min,long max){return new Field(name,Kind.INTEGER,false,0,false,null,min,max);}
    private static Field bool(String name){return f(name,Kind.BOOLEAN,false);}
    private static Field date(String name){return f(name,Kind.DATE,false);}
    private static Field instant(String name){return f(name,Kind.INSTANT,true);}
    private static Table table(String name,String from,boolean owned,Field...columns){
        List<Field> fields=new ArrayList<>();fields.add(f("id",Kind.ID,false));fields.addAll(List.of(columns));
        fields.add(f("created_at",Kind.INSTANT,false));if(!name.equals("time_entries"))fields.add(f("updated_at",Kind.INSTANT,false));
        return new Table(name,name.equals("weekly_reviews")?"restored_weekly_reviews":name.equals("daily_reviews")?"restored_daily_reviews":name,from,owned,List.copyOf(fields));
    }
    static final List<Table> TABLES=List.of(
        table("plans","plans t where t.owner_id=?",true,title("name",120),s("description",1000),date("start_date"),date("end_date"),bool("archived"),integer("rest_days_mask",0,127),instant("deleted_at")),
        table("task_templates","task_templates t where t.owner_id=?",true,title("name",80),optionalText("restored_original_name",80),title("category",40),title("title",180),s("description",2000),integer("planned_minutes",1,1440),f("acceptance_json",Kind.STRINGS,false),s("resource_url",1000),bool("required_task"),instant("deleted_at")),
        table("plan_tags","plan_tags t join plans p on p.id=t.plan_id where p.owner_id=?",false,ref("plan_id","plans",false),title("name",60),title("color",20),bool("overall_tag"),instant("deleted_at")),
        table("plan_tasks","plan_tasks t join plans p on p.id=t.plan_id where p.owner_id=?",false,ref("plan_id","plans",false),ref("tag_id","plan_tags",true),date("task_date"),f("start_time",Kind.TIME,true),f("end_time",Kind.TIME,true),integer("planned_minutes",1,1440),title("category",40),title("title",180),s("description",2000),f("acceptance_json",Kind.STRINGS,false),s("resource_url",1000),bool("required_task"),integer("sort_order",Integer.MIN_VALUE,Integer.MAX_VALUE),f("recurrence_id",Kind.BATCH,true),ref("template_id","task_templates",true),instant("deleted_at")),
        table("task_progress","task_progress t join plan_tasks k on k.id=t.task_id join plans p on p.id=k.plan_id where p.owner_id=?",false,ref("task_id","plan_tasks",false),title("status",30),integer("accumulated_seconds",0,Long.MAX_VALUE),instant("started_at"),instant("completed_at"),f("acceptance_json",Kind.BOOLEANS,false),s("note",2000),f("defer_to",Kind.DATE,true),s("skip_reason",1000),bool("ever_started")),
        table("time_entries","time_entries t join task_progress g on g.id=t.progress_id join plan_tasks k on k.id=g.task_id join plans p on p.id=k.plan_id where p.owner_id=?",false,ref("progress_id","task_progress",false),f("started_at",Kind.INSTANT,false),f("ended_at",Kind.INSTANT,false),integer("seconds",Long.MIN_VALUE,Long.MAX_VALUE),title("source",20)),
        table("plan_milestones","plan_milestones t join plans p on p.id=t.plan_id where p.owner_id=?",false,ref("plan_id","plans",false),title("name",120),date("milestone_date"),s("description",1000),f("acceptance_json",Kind.STRINGS,false),f("checks_json",Kind.BOOLEANS,false),s("note",2000),instant("reviewed_at"),instant("deleted_at")),
        table("daily_reflections","daily_reflections t join plans p on p.id=t.plan_id where p.owner_id=?",false,ref("plan_id","plans",false),date("reflection_date"),s("completed_summary",2000),s("unfinished_summary",2000),s("blockers",2000),s("first_task_tomorrow",1000),new Field("energy",Kind.INTEGER,true,0,false,null,1,5),s("note",2000)),
        table("mainlines","mainlines t where t.owner_id=?",true,title("name",120),s("description",1000),title("color",20),bool("archived"),bool("paused"),title("execution_mode",20),instant("deleted_at")),
        table("mainline_stages","mainline_stages t join mainlines l on l.id=t.mainline_id where l.owner_id=?",false,ref("mainline_id","mainlines",false),title("name",120),s("description",1000),integer("sort_order",Integer.MIN_VALUE,Integer.MAX_VALUE)),
        table("mainline_items","mainline_items t join mainlines l on l.id=t.mainline_id where l.owner_id=?",false,ref("mainline_id","mainlines",false),title("title",180),s("description",2000),f("acceptance_json",Kind.STRINGS,false),integer("planned_minutes",1,Integer.MAX_VALUE),integer("sort_order",0,Integer.MAX_VALUE),title("status",30),integer("accumulated_seconds",0,Long.MAX_VALUE),s("note",2000),instant("started_at"),instant("completed_at"),instant("deleted_at"),ref("stage_id","mainline_stages",true),title("dependency_mode",20),f("prerequisite_ids_json",Kind.IDS,false)),
        table("todos","todos t where t.owner_id=?",true,title("title",180),s("note",2000),date("due_date"),bool("done"),bool("deleted"),instant("completed_at")),
        table("weekly_reviews","weekly_reviews t where t.owner_id=?",true,date("week_start"),s("wins",4000),s("blockers",4000),s("next_focus",4000),title("energy",16),title("burden",16),bool("reviewed"),bool("open_to_increase")),
        table("daily_reviews","daily_reviews t where t.owner_id=?",true,date("review_date"),s("wins",4000),s("blockers",4000),s("next_focus",4000),title("energy",16),title("burden",16),bool("reviewed")),
        table("personal_templates","personal_templates t where t.owner_id=?",true,title("kind",8),title("name",120),s("description",1000),s("category",60),bool("favorite"),s("blueprint_json",262144))
    );
    static final Set<String> NAMES=TABLES.stream().map(Table::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private BackupSchema(){}
}
