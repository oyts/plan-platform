package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.Mainline;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MainlineRepository extends JpaRepository<Mainline, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Mainline m where m.id = :id and m.owner.id = :owner and m.deletedAt is null")
    Optional<Mainline> lockOwned(@org.springframework.data.repository.query.Param("id") String id, @org.springframework.data.repository.query.Param("owner") String owner);
    List<Mainline> findByOwnerIdAndDeletedAtIsNullAndArchivedFalseOrderByCreatedAtAsc(String ownerId);
    List<Mainline> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    Optional<Mainline> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<Mainline> findByIdAndOwnerId(String id, String ownerId);
}
