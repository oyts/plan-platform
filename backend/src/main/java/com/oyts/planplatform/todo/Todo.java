package com.oyts.planplatform.todo;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "todos")
public class Todo extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, length = 2000) private String note = "";
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(nullable = false) private boolean done;
    @Column(nullable = false) private boolean deleted;

    protected Todo() {}
    Todo(UserAccount owner, String title, String note, LocalDate dueDate) {
        this.owner = owner;
        update(title, note, dueDate, false, false);
    }
    void update(String title, String note, LocalDate dueDate, boolean done, boolean deleted) {
        this.title = title.trim();
        this.note = note == null ? "" : note.trim();
        this.dueDate = dueDate;
        this.done = done;
        this.deleted = deleted;
    }
    String getTitle() { return title; }
    String getNote() { return note; }
    LocalDate getDueDate() { return dueDate; }
    boolean isDone() { return done; }
    boolean isDeleted() { return deleted; }
}
