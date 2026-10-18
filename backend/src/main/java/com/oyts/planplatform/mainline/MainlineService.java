package com.oyts.planplatform.mainline;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.mainline.domain.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MainlineService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final UserAccountRepository users;
    private final MainlineRepository mainlines;
    private final MainlineItemRepository items;
    private final MainlineStageRepository stages;
    private final ObjectMapper mapper;
    private final EntityManager em;
    MainlineService(UserAccountRepository users, MainlineRepository mainlines, MainlineItemRepository items, MainlineStageRepository stages, ObjectMapper mapper, EntityManager em) {
        this.users = users; this.mainlines = mainlines; this.items = items; this.stages = stages; this.mapper = mapper; this.em = em;
    }
    @Transactional(readOnly = true) List<MainlineDtos.MainlineResponse> list(String userId) {
        return mainlines.findByOwnerIdAndDeletedAtIsNullAndArchivedFalseOrderByCreatedAtAsc(userId).stream().map(this::response).toList();
    }
    @Transactional MainlineDtos.MainlineResponse create(String userId, MainlineDtos.MainlineRequest r) {
        Mainline line = new Mainline(users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。")), r.name(), r.description(), r.color());
        if (r.executionMode() != null) line.setExecutionMode(r.executionMode());
        return response(mainlines.save(line));
    }
    @Transactional MainlineDtos.MainlineResponse update(String userId, String id, MainlineDtos.MainlineRequest r) {
        Mainline line = lock(userId, id); check(line.getVersion(), r.version());
        line.update(r.name(), r.description(), r.color());
        if (r.executionMode() != null) {
            line.setExecutionMode(r.executionMode()); validateGraph(all(line)); rejectLockedRunning(line);
        }
        flush(line); return response(line);
    }
    @Transactional void archive(String userId, String id) { lock(userId, id).trash(); }
    @Transactional MainlineDtos.MainlineResponse setPaused(String owner,String id,MainlineDtos.PauseRequest request){
        Mainline line=lock(owner,id);check(line.getVersion(),request.version());
        if(request.paused()&&active(line).stream().anyMatch(item->item.getStatus()==MainlineItem.Status.RUNNING))throw new ConflictException("有步骤正在计时，请先暂停该步骤，结算用时后再暂停整条路线。");
        line.setPaused(request.paused());flush(line);return response(line);
    }
    @Transactional(readOnly=true) Object itemPage(String owner,String id,int page,int size,String search,String status,boolean deleted){
        Mainline line=mainlines.findByIdAndOwnerIdAndDeletedAtIsNull(id,owner).orElseThrow(()->new NotFoundException("目标路线不存在。"));
        if(page<0||page>100000||size<1||size>100||search.length()>180||!Set.of("ALL","OPEN","COMPLETED").contains(status))throw new IllegalArgumentException("分页或搜索条件无效。");
        org.springframework.data.jpa.domain.Specification<MainlineItem> specification=(root,query,b)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();predicates.add(b.equal(root.get("mainline").get("id"),id));
            predicates.add(deleted?b.isNotNull(root.get("deletedAt")):b.isNull(root.get("deletedAt")));
            if(!status.equals("ALL"))predicates.add(status.equals("COMPLETED")?b.equal(root.get("status"),MainlineItem.Status.COMPLETED):b.notEqual(root.get("status"),MainlineItem.Status.COMPLETED));
            if(!search.isBlank()){String pattern="%"+search.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";predicates.add(b.or(b.like(b.lower(root.get("title")),pattern,'!'),b.like(b.lower(root.get("description")),pattern,'!')));}
            return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=items.findAll(specification,org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by("sortOrder","id")));
        var graph=active(line);return new com.oyts.planplatform.plan.PlanDtos.PageResponse<>(result.getContent().stream().map(item->itemResponse(item,graph)).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Transactional MainlineDtos.ItemResponse addItem(String userId, String lineId, MainlineDtos.ItemRequest r) {
        Mainline line = lock(userId, lineId); MainlineItem item = new MainlineItem(line);
        apply(item, r, line); items.saveAndFlush(item); validateGraph(all(line)); rejectLockedRunning(line);
        return itemResponse(item, active(line));
    }
    @Transactional MainlineDtos.ItemResponse updateItem(String userId, String itemId, MainlineDtos.ItemRequest r) {
        MainlineItem item = lockedItem(userId, itemId); check(item.getVersion(), r.version());
        apply(item, r, item.getMainline()); validateGraph(all(item.getMainline())); rejectLockedRunning(item.getMainline());
        flush(item); return itemResponse(item, active(item.getMainline()));
    }
    @Transactional void deleteItem(String userId, String id) {
        MainlineItem item = lockedItem(userId, id);
        if (active(item.getMainline()).stream().anyMatch(row -> "EXPLICIT".equals(row.getDependencyMode()) && read(row.getPrerequisiteIdsJson()).contains(id)))
            throw new ConflictException("其他步骤依赖此步骤，请先调整它们的前置条件。");
        item.trash();
    }
    @Transactional MainlineDtos.ItemResponse start(String userId, String id, Long version) {
        MainlineItem item = lockedItem(userId, id); check(item.getVersion(), version); requireUnlocked(item);
        if (item.getStatus() == MainlineItem.Status.RUNNING || item.getStatus() == MainlineItem.Status.COMPLETED) throw new ConflictException("此步骤正在进行或已经完成，请刷新状态。");
        item.start(); flush(item); return itemResponse(item, active(item.getMainline()));
    }
    @Transactional MainlineDtos.ItemResponse pause(String userId, String id, Long version) {
        MainlineItem item = lockedItem(userId, id); check(item.getVersion(), version);
        if (item.getStatus() != MainlineItem.Status.RUNNING) throw new ConflictException("只有进行中的步骤可以暂停。");
        item.pause(); flush(item); return itemResponse(item, active(item.getMainline()));
    }
    @Transactional MainlineDtos.ItemResponse complete(String userId, String id, MainlineDtos.CompleteRequest r) {
        MainlineItem item = lockedItem(userId, id); check(item.getVersion(), r.version()); requireUnlocked(item);
        if (item.getStatus() == MainlineItem.Status.COMPLETED) throw new ConflictException("此步骤已经完成。");
        item.complete(r.note()); flush(item); return itemResponse(item, active(item.getMainline()));
    }
    @Transactional MainlineDtos.StageResponse addStage(String userId, String lineId, MainlineDtos.StageRequest r) {
        Mainline line = lock(userId, lineId);
        return stageResponse(stages.saveAndFlush(new MainlineStage(line, r.name(), r.description(), r.sortOrder())));
    }
    @Transactional MainlineDtos.StageResponse updateStage(String userId, String id, MainlineDtos.StageRequest r) {
        MainlineStage stage = lockedStage(userId, id); check(stage.getVersion(), r.version()); stage.update(r.name(), r.description(), r.sortOrder()); flush(stage); return stageResponse(stage);
    }
    @Transactional void deleteStage(String userId, String id, Long version) {
        MainlineStage stage = lockedStage(userId, id); check(stage.getVersion(), version);
        // Include trashed steps so restoration never points to a deleted stage.
        all(stage.getMainline()).stream().filter(item -> id.equals(item.getStageId())).forEach(MainlineItem::clearStage);
        em.flush(); stages.delete(stage);
    }
    private Mainline lock(String userId, String id) {
        Mainline line = mainlines.lockOwned(id, userId).orElseThrow(() -> new NotFoundException("目标路线不存在。")); em.refresh(line); return line;
    }
    private MainlineItem lockedItem(String userId, String id) {
        MainlineItem item = items.findByIdAndMainlineOwnerIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> new NotFoundException("路线步骤不存在。"));
        lock(userId, item.getMainline().getId()); em.refresh(item);
        if (item.getDeletedAt() != null) throw new NotFoundException("路线步骤不存在。"); return item;
    }
    private MainlineStage lockedStage(String userId, String id) {
        MainlineStage stage = stages.findByIdAndMainlineOwnerId(id, userId).orElseThrow(() -> new NotFoundException("阶段不存在。"));
        lock(userId, stage.getMainline().getId()); em.refresh(stage); return stage;
    }
    private void apply(MainlineItem item, MainlineDtos.ItemRequest r, Mainline line) {
        if (all(line).stream().anyMatch(other -> !Objects.equals(other.getId(), item.getId()) && other.getSortOrder() == r.sortOrder()))
            throw new ConflictException("步骤位置已被占用，请刷新后重新添加。");
        item.update(r.title(), r.description(), json(r.acceptance()), r.plannedMinutes(), r.sortOrder());
        // Omitted routing in older clients must not clear newer metadata.
        if (r.routing() == null) return;
        var routing = r.routing(); String mode = routing.dependencyMode();
        if (!Set.of("INHERIT", "NONE", "EXPLICIT").contains(mode)) throw new IllegalArgumentException("前置条件类型无效。");
        if (routing.stageId() != null && stages.findByMainlineIdOrderBySortOrderAscCreatedAtAsc(line.getId()).stream().noneMatch(stage -> stage.getId().equals(routing.stageId())))
            throw new NotFoundException("此路线中没有该阶段。");
        List<String> ids = routing.prerequisiteIds() == null ? List.of() : routing.prerequisiteIds().stream().distinct().toList();
        if (!"EXPLICIT".equals(mode) && !ids.isEmpty()) throw new IllegalArgumentException("只有指定前置步骤时才能提交依赖。");
        Set<String> activeIds = new HashSet<>(active(line).stream().map(MainlineItem::getId).toList());
        for (String id : ids) {
            if (id.equals(item.getId())) throw new IllegalArgumentException("步骤不能依赖自身。");
            if (!activeIds.contains(id)) throw new NotFoundException("前置步骤不属于当前路线或已移入回收站。");
        }
        item.route(routing.stageId(), mode, json(ids));
    }
    private List<MainlineItem> all(Mainline line) { return items.findByMainlineIdOrderBySortOrderAsc(line.getId()); }
    private List<MainlineItem> active(Mainline line) { return items.findByMainlineIdAndDeletedAtIsNullOrderBySortOrderAsc(line.getId()); }
    private List<String> prerequisites(MainlineItem item, List<MainlineItem> rows) {
        if ("EXPLICIT".equals(item.getDependencyMode())) return read(item.getPrerequisiteIdsJson());
        if ("NONE".equals(item.getDependencyMode()) || "FLEXIBLE".equals(item.getMainline().getExecutionMode())) return List.of();
        return rows.stream().filter(row -> row.getSortOrder() < item.getSortOrder()).map(MainlineItem::getId).toList();
    }
    private List<String> blockers(MainlineItem item, List<MainlineItem> rows) {
        Set<String> completedIds = new HashSet<>(rows.stream().filter(row -> row.getStatus() == MainlineItem.Status.COMPLETED).map(MainlineItem::getId).toList());
        return prerequisites(item, rows).stream().filter(id -> !completedIds.contains(id)).toList();
    }
    private void requireUnlocked(MainlineItem item) { if(item.getMainline().isPaused())throw new ConflictException("此路线已暂停，请先恢复路线再开始或完成步骤。"); if (!blockers(item, active(item.getMainline())).isEmpty()) throw new ConflictException("请先完成此步骤的前置条件。"); }
    private void rejectLockedRunning(Mainline line) {
        List<MainlineItem> rows = active(line);
        if (rows.stream().anyMatch(row -> row.getStatus() == MainlineItem.Status.RUNNING && !blockers(row, rows).isEmpty())) throw new ConflictException("调整会锁定正在进行的步骤，请先暂停该步骤。");
    }
    private void validateGraph(List<MainlineItem> rows) {
        // Retain trashed nodes in validation: restoring them cannot introduce a cycle.
        Map<String, List<String>> graph = new HashMap<>();
        rows.forEach(row -> graph.put(row.getId(), prerequisites(row, rows)));
        Set<String> visiting = new HashSet<>(), visited = new HashSet<>();
        for (String id : graph.keySet()) visit(id, graph, visiting, visited);
    }
    private void visit(String id, Map<String, List<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id) || !graph.containsKey(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("前置步骤形成了循环，请调整依赖关系。");
        for (String dependency : graph.get(id)) visit(dependency, graph, visiting, visited);
        visiting.remove(id); visited.add(id);
    }
    private MainlineDtos.MainlineResponse response(Mainline line) {
        List<MainlineItem> rows = active(line);
        int next = all(line).stream().mapToInt(MainlineItem::getSortOrder).max().orElse(-1) + 1;
        return new MainlineDtos.MainlineResponse(line.getId(), line.getName(), line.getDescription(), line.getColor(), line.getVersion(), rows.stream().filter(item -> item.getStatus() == MainlineItem.Status.COMPLETED).count(), rows.size(), rows.stream().map(item -> itemResponse(item, rows)).toList(), line.getExecutionMode(), stages.findByMainlineIdOrderBySortOrderAscCreatedAtAsc(line.getId()).stream().map(this::stageResponse).toList(), next, line.isPaused());
    }
    private MainlineDtos.ItemResponse itemResponse(MainlineItem item, List<MainlineItem> rows) {
        List<String> blockers = blockers(item, rows);
        return new MainlineDtos.ItemResponse(item.getId(), item.getTitle(), item.getDescription(), read(item.getAcceptanceJson()), item.getPlannedMinutes(), item.getSortOrder(), item.getStatus().name(), blockers.isEmpty()&&!item.getMainline().isPaused(), item.getAccumulatedSeconds(), item.getNote(), item.getStartedAt(), item.getCompletedAt(), item.getVersion(), item.getStageId(), item.getDependencyMode(), read(item.getPrerequisiteIdsJson()), blockers);
    }
    private MainlineDtos.StageResponse stageResponse(MainlineStage s) { return new MainlineDtos.StageResponse(s.getId(), s.getName(), s.getDescription(), s.getSortOrder(), s.getVersion()); }
    private void check(long current, Long requested) { if (requested != null && requested != current) throw new ConflictException("数据已经被其他设备修改，请刷新后重试。"); }
    private void flush(Object entity) { em.flush(); em.refresh(entity); }
    private String json(Object value) { try { return mapper.writeValueAsString(value == null ? List.of() : value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("步骤内容无法序列化。", e); } }
    private List<String> read(String value) { try { return mapper.readValue(value, STRING_LIST); } catch (JsonProcessingException e) { throw new IllegalStateException("步骤内容数据损坏。", e); } }
}
