package com.oyts.planplatform.feedback;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name="feedback_tickets")
class FeedbackTicket extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="owner_id") UserAccount owner;
    @Column(nullable=false,length=36) String operationKey;
    @Column(nullable=false,length=64) String fingerprint;
    @Column(nullable=false,length=24) String category;
    @Column(nullable=false,length=120) String title;
    @Column(nullable=false,length=4000) String steps;
    @Column(nullable=false,length=2000) String expectedResult;
    @Column(nullable=false,length=2000) String actualResult;
    @Column(length=2000) String diagnosticJson;
    @Column(nullable=false,length=24) String status="OPEN";
    @Column(nullable=false) long activity;
    protected FeedbackTicket() {}
    FeedbackTicket(UserAccount owner,FeedbackDtos.Create r,String fingerprint,String diagnostic) {
        this.owner=owner;operationKey=r.operationKey();this.fingerprint=fingerprint;category=r.category();
        title=r.title().trim();steps=r.steps().trim();expectedResult=r.expectedResult().trim();
        actualResult=r.actualResult().trim();diagnosticJson=diagnostic;
    }
}
