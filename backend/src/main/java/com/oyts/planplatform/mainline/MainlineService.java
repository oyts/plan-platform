package com.oyts.planplatform.mainline;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.mainline.domain.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MainlineService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final UserAccountRepository users; private final MainlineRepository mainlines; private final MainlineItemRepository items; private final ObjectMapper objectMapper;
    MainlineService(UserAccountRepository users, MainlineRepository mainlines, MainlineItemRepository items, ObjectMapper objectMapper) { this.users = users; this.mainlines = mainlines; this.items = items; this.objectMapper = objectMapper; }
    @Transactional(readOnly = true) List<MainlineDtos.MainlineResponse> list(String userId) { return mainlines.findByOwnerIdAndArchivedFalseOrderByCreatedAtAsc(userId).stream().map(this::response).toList(); }
    @Transactional MainlineDtos.MainlineResponse create(String userId, MainlineDtos.MainlineRequest r) { UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。")); return response(mainlines.save(new Mainline(owner, r.name(), r.description(), r.color()))); }
    @Transactional MainlineDtos.MainlineResponse update(String userId, String id, MainlineDtos.MainlineRequest r) { Mainline line = owned(userId, id); check(line.getVersion(), r.version()); line.update(r.name(), r.description(), r.color()); return response(line); }
    @Transactional void archive(String userId, String id) { owned(userId, id).archive(); }
    @Transactional MainlineDtos.ItemResponse addItem(String userId, String lineId, MainlineDtos.ItemRequest r) { Mainline line = owned(userId, lineId); MainlineItem item = new MainlineItem(line); apply(item, r); items.save(item); return itemResponse(item, unlocked(item)); }
    @Transactional MainlineDtos.ItemResponse updateItem(String userId, String itemId, MainlineDtos.ItemRequest r) { MainlineItem item = ownedItem(userId, itemId); check(item.getVersion(), r.version()); apply(item, r); return itemResponse(item, unlocked(item)); }
    @Transactional void deleteItem(String userId, String itemId) { items.delete(ownedItem(userId, itemId)); }
    @Transactional MainlineDtos.ItemResponse start(String userId, String itemId, Long version) { MainlineItem item = ownedItem(userId, itemId); check(item.getVersion(), version); if (!unlocked(item)) throw new ConflictException("请先完成前一个主线步骤。"); item.start(); return itemResponse(item, true); }
    @Transactional MainlineDtos.ItemResponse pause(String userId, String itemId, Long version) { MainlineItem item = ownedItem(userId, itemId); check(item.getVersion(), version); item.pause(); return itemResponse(item, unlocked(item)); }
    @Transactional MainlineDtos.ItemResponse complete(String userId, String itemId, MainlineDtos.CompleteRequest r) { MainlineItem item = ownedItem(userId, itemId); check(item.getVersion(), r.version()); if (!unlocked(item)) throw new ConflictException("请先完成前一个主线步骤。"); item.complete(r.note()); return itemResponse(item, true); }
    private Mainline owned(String userId, String id) { return mainlines.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("主线不存在。")); }
    private MainlineItem ownedItem(String userId, String id) { return items.findByIdAndMainlineOwnerId(id, userId).orElseThrow(() -> new NotFoundException("主线步骤不存在。")); }
    private void apply(MainlineItem item, MainlineDtos.ItemRequest r) { item.update(r.title(), r.description(), json(r.acceptance()), r.plannedMinutes(), r.sortOrder()); }
    private boolean unlocked(MainlineItem item) { return items.findByMainlineIdOrderBySortOrderAsc(item.getMainline().getId()).stream().filter(candidate -> candidate.getSortOrder() < item.getSortOrder()).allMatch(candidate -> candidate.getStatus() == MainlineItem.Status.COMPLETED); }
    private MainlineDtos.MainlineResponse response(Mainline line) { List<MainlineItem> rows = items.findByMainlineIdOrderBySortOrderAsc(line.getId()); return new MainlineDtos.MainlineResponse(line.getId(), line.getName(), line.getDescription(), line.getColor(), line.getVersion(), rows.stream().filter(item -> item.getStatus() == MainlineItem.Status.COMPLETED).count(), rows.size(), rows.stream().map(item -> itemResponse(item, unlocked(item))).toList()); }
    private MainlineDtos.ItemResponse itemResponse(MainlineItem item, boolean unlocked) { return new MainlineDtos.ItemResponse(item.getId(), item.getTitle(), item.getDescription(), read(item.getAcceptanceJson()), item.getPlannedMinutes(), item.getSortOrder(), item.getStatus().name(), unlocked, item.getAccumulatedSeconds(), item.getNote(), item.getStartedAt(), item.getCompletedAt(), item.getVersion()); }
    private void check(long current, Long requested) { if (requested != null && requested != current) throw new ConflictException("数据已经被其他设备修改，请刷新后重试。"); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value == null ? List.of() : value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("验收项无法序列化。", e); } }
    private List<String> read(String value) { try { return objectMapper.readValue(value, STRING_LIST); } catch (JsonProcessingException e) { throw new IllegalStateException("验收项数据损坏。", e); } }
}
