package com.oyts.planplatform.feedback;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="feedback_messages")
class FeedbackMessage {
    @Id @Column(length=36) String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="ticket_id") FeedbackTicket ticket;
    @Column(nullable=false,length=12) String authorKind;
    @Column(nullable=false,length=4000) String body;
    @Column(nullable=false) Instant createdAt;
    protected FeedbackMessage() {}
    FeedbackMessage(FeedbackTicket ticket,String author,String body) {
        id=UUID.randomUUID().toString();this.ticket=ticket;authorKind=author;this.body=body.trim();createdAt=Instant.now();
    }
}
