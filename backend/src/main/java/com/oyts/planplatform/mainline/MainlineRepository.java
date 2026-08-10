package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.Mainline;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
interface MainlineRepository extends JpaRepository<Mainline, String> {
    List<Mainline> findByOwnerIdAndArchivedFalseOrderByCreatedAtAsc(String ownerId);
    Optional<Mainline> findByIdAndOwnerId(String id, String ownerId);
}
