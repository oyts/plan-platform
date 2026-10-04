package com.oyts.planplatform.mainline;
import com.oyts.planplatform.mainline.domain.MainlineStage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MainlineStageRepository extends JpaRepository<MainlineStage, String> {
    List<MainlineStage> findByMainlineIdOrderBySortOrderAscCreatedAtAsc(String lineId);
    Optional<MainlineStage> findByIdAndMainlineOwnerId(String id, String ownerId);
}
