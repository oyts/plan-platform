package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.mainline.*;
import com.oyts.planplatform.mainline.domain.*;
import com.oyts.planplatform.plan.*;
import com.oyts.planplatform.todo.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Called inside the conversation's single apply transaction. Creates new records only. */
@Service
class AiDraftApplier {
    private final PlanService plans; private final TodoService todos; private final UserAccountRepository users;
    private final MainlineRepository lines; private final MainlineItemRepository items; private final MainlineStageRepository stages; private final ObjectMapper mapper;
    AiDraftApplier(PlanService plans,TodoService todos,UserAccountRepository users,MainlineRepository lines,MainlineItemRepository items,MainlineStageRepository stages,ObjectMapper mapper) {
        this.plans=plans;this.todos=todos;this.users=users;this.lines=lines;this.items=items;this.stages=stages;this.mapper=mapper;
    }
    record Applied(String mode,String path,int count) {}
    Applied apply(String owner,AiDrafts.Draft draft) {
        return switch(draft.mode()) {
            case PLAN -> {
                var plan=plans.createPlan(owner,new PlanDtos.PlanRequest(draft.title(),"",draft.startDate(),draft.endDate(),"总计","#002FA7",null,draft.restDays()));
                int index=0;
                for(var item:draft.items()) plans.createTask(owner,plan.id(),new PlanDtos.TaskRequest(null,item.dueDate(),null,null,item.plannedMinutes(),"AI规划",item.title(),item.note(),List.of(),"",true,index++,null));
                yield new Applied("PLAN","/plans/"+plan.id(),draft.items().size());
            }
            case TODO -> {
                for(var item:draft.items()) todos.create(owner,new TodoDtos.Create(item.title(),item.note(),item.dueDate()));
                yield new Applied("TODO","/todos",draft.items().size());
            }
            case ROUTE -> {
                var user=users.findById(owner).orElseThrow(()->new NotFoundException("用户不存在。"));
                var line=new Mainline(user,draft.title(),"","#002FA7");line.setExecutionMode("FLEXIBLE");lines.save(line);
                Map<String,String> stageIds=new HashMap<>();int index=0;
                for(var stage:draft.stages()) stageIds.put(stage.localId(),stages.save(new MainlineStage(line,stage.title(),"",index++)).getId());
                Map<String,MainlineItem> created=new HashMap<>();index=0;
                for(var item:draft.items()) { var row=new MainlineItem(line);row.update(item.title(),item.note(),"[]",item.plannedMinutes(),index++);items.save(row);created.put(item.localId(),row); }
                for(var item:draft.items()) {
                    var ids=item.prerequisites().stream().map(id->created.get(id).getId()).toList();
                    try { created.get(item.localId()).route(item.stageId()==null?null:stageIds.get(item.stageId()),ids.isEmpty()?"NONE":"EXPLICIT",mapper.writeValueAsString(ids)); }
                    catch (java.io.IOException impossible) { throw new IllegalStateException("Cannot serialize dependency ids"); }
                }
                yield new Applied("ROUTE","/mainlines?line="+line.getId(),draft.items().size());
            }
        };
    }
}
