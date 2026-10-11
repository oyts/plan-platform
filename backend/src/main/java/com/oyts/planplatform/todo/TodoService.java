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
