package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.MainlineItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MainlineItemRepository extends JpaRepository<MainlineItem, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<MainlineItem> {
    List<MainlineItem> findByMainlineIdOrderBySortOrderAsc(String mainlineId);
    List<MainlineItem> findByMainlineIdAndDeletedAtIsNullOrderBySortOrderAsc(String mainlineId);
    List<MainlineItem> findByMainlineOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    Optional<MainlineItem> findByIdAndMainlineOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<MainlineItem> findByIdAndMainlineOwnerId(String id, String ownerId);
}
