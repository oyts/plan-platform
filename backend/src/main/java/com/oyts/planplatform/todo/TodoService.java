package com.oyts.planplatform.todo;

import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {
    private final TodoRepository todos;
    private final UserAccountRepository users;
    private final EntityManager em;
    TodoService(TodoRepository todos, UserAccountRepository users, EntityManager em) {
        this.todos = todos; this.users = users; this.em = em;
    }
    @Transactional(readOnly = true)
    public List<TodoDtos.Response> list(String owner, LocalDate from, LocalDate to, boolean deleted) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 30)
            throw new IllegalArgumentException("待办一次最多查询连续 31 天，请缩小日期范围。");
        var rows = todos.findTop2001ByOwnerIdAndDeletedAndDueDateBetweenOrderByDueDateAscCreatedAtAscIdAsc(owner, deleted, from, to);
        if (rows.size() > 2000) throw new IllegalArgumentException("待办超过 2000 项，请缩小日期范围。");
        return rows.stream().map(this::response).toList();
    }
    @Transactional
    public TodoDtos.Response create(String owner, TodoDtos.Create request) {
        var user = users.findById(owner).orElseThrow(() -> new NotFoundException("用户不存在。"));
        var item = todos.save(new Todo(user, request.title(), request.note(), request.dueDate()));
        em.flush();
        return response(item);
    }
    @Transactional(readOnly=true)
    public com.oyts.planplatform.plan.PlanDtos.PageResponse<TodoDtos.Response> page(String owner,LocalDate from,LocalDate to,boolean deleted,String status,String search,int page,int size){
        if(from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>3660||page<0||page>100000||size<1||size>100||search.length()>180||!java.util.Set.of("ALL","OPEN","DONE").contains(status))throw new IllegalArgumentException("历史范围最多 3661 天，每页 1—100 项，请检查查询条件。");
        org.springframework.data.jpa.domain.Specification<Todo> specification=(root,query,b)->{
            var predicates=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();predicates.add(b.equal(root.get("owner").get("id"),owner));predicates.add(b.equal(root.get("deleted"),deleted));predicates.add(b.between(root.get("dueDate"),from,to));
            if(!status.equals("ALL"))predicates.add(b.equal(root.get("done"),status.equals("DONE")));
            if(!search.isBlank()){String pattern="%"+search.trim().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";predicates.add(b.or(b.like(b.lower(root.get("title")),pattern,'!'),b.like(b.lower(root.get("note")),pattern,'!')));}
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=todos.findAll(specification,org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"dueDate","createdAt","id")));
        return new com.oyts.planplatform.plan.PlanDtos.PageResponse<>(result.getContent().stream().map(this::response).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Transactional
    public TodoDtos.Response update(String owner, String id, TodoDtos.Update request) {
        var item = todos.findByIdAndOwnerId(id, owner).orElseThrow(() -> new NotFoundException("待办不存在。"));
        if (item.getVersion() != request.version())
            throw new ConflictException("此待办已在其他设备修改，请查看最新数据后重新编辑。");
        item.update(request.title(), request.note(), request.dueDate(), request.done(), request.deleted());
        em.flush(); em.refresh(item);
        return response(item);
    }
    private TodoDtos.Response response(Todo item) {
        return new TodoDtos.Response(item.getId(), item.getTitle(), item.getNote(), item.getDueDate(),
            item.isDone(), item.isDeleted(), item.getVersion(), item.getUpdatedAt());
    }
}
