package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.Mainline;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MainlineRepository extends JpaRepository<Mainline, String> {
    List<Mainline> findByOwnerIdAndDeletedAtIsNullAndArchivedFalseOrderByCreatedAtAsc(String ownerId);
    List<Mainline> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    Optional<Mainline> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<Mainline> findByIdAndOwnerId(String id, String ownerId);
}
