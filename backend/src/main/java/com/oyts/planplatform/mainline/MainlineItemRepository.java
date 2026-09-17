package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.MainlineItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MainlineItemRepository extends JpaRepository<MainlineItem, String> {
    List<MainlineItem> findByMainlineIdOrderBySortOrderAsc(String mainlineId);
    Optional<MainlineItem> findByIdAndMainlineOwnerId(String id, String ownerId);
}
