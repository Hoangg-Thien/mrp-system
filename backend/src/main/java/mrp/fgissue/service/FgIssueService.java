package mrp.fgissue.service;


import mrp.catalog.entity.Product;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.BusinessException;
import mrp.common.ResourceNotFoundException;
import mrp.fgissue.dto.FgIssueQuery;
import mrp.fgissue.dto.FgIssueRequests;
import mrp.fgissue.dto.FgIssueViews;
import mrp.fgissue.entity.FgIssueRequestStatus;
import mrp.fgissue.entity.FinishedGoodsIssueRequest;
import mrp.fgissue.entity.FinishedGoodsIssueRequestItem;
import mrp.fgissue.repository.FinishedGoodsIssueRequestItemRepository;
import mrp.fgissue.repository.FinishedGoodsIssueRequestRepository;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.security.CurrentUser;
import mrp.user.entity.Staff;
import mrp.user.entity.User;
import mrp.user.entity.UserRole;
import mrp.user.entity.WarehouseManager;
import mrp.user.repository.StaffRepository;
import mrp.user.repository.UserRepository;
import mrp.user.repository.WarehouseManagerRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FgIssueService {

    private final FinishedGoodsIssueRequestRepository requestRepo;
    private final FinishedGoodsIssueRequestItemRepository itemRepo;
    private final ProductRepository productRepo;
    private final StaffRepository staffRepo;
    private final WarehouseManagerRepository wmRepo;
    private final UserRepository userRepo;
    private final CatalogService catalogService;
    private final StockLedger stockLedger;
    private final JdbcTemplate jdbc;

    public FgIssueService(
            FinishedGoodsIssueRequestRepository requestRepo,
            FinishedGoodsIssueRequestItemRepository itemRepo,
            ProductRepository productRepo,
            StaffRepository staffRepo,
            WarehouseManagerRepository wmRepo,
            UserRepository userRepo,
            CatalogService catalogService,
            StockLedger stockLedger,
            JdbcTemplate jdbc) {
        this.requestRepo = requestRepo;
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.staffRepo = staffRepo;
        this.wmRepo = wmRepo;
        this.userRepo = userRepo;
        this.catalogService = catalogService;
        this.stockLedger = stockLedger;
        this.jdbc = jdbc;
    }

    @Transactional
    public FgIssueViews.FgIssueRequestDetailView create(
            FgIssueRequests.CreateFgIssueRequest req, CurrentUser actor) {

        Set<Long> seen = new HashSet<>();
        for (FgIssueRequests.FgIssueItemRequest itemReq : req.items()) {
            if (!seen.add(itemReq.productId())) {
                throw new BusinessException("Sản phẩm #" + itemReq.productId() + " bị trùng lặp trong phiếu");
            }
        }

        boolean hasShortage = false;

        for (FgIssueRequests.FgIssueItemRequest itemReq : req.items()) {
            catalogService.requireActiveProduct(itemReq.productId());

            // Quyết định D5: Cảnh báo nếu số lượng xin + tổng các phiếu PENDING khác vượt tồn
            BigDecimal onHand = stockLedger.productBalance(itemReq.productId());
            if (onHand == null) onHand = BigDecimal.ZERO;

            BigDecimal otherPending = jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.quantity), 0)
                  FROM finished_goods_issue_request_items i
                  JOIN finished_goods_issue_requests r ON r.id = i.request_id
                 WHERE r.status = 'PENDING' AND i.product_id = ?
                """, BigDecimal.class, itemReq.productId());
            if (otherPending == null) otherPending = BigDecimal.ZERO;

            BigDecimal totalRequested = otherPending.add(BigDecimal.valueOf(itemReq.quantity()));
            if (totalRequested.compareTo(onHand) > 0) {
                hasShortage = true;
            }
        }

        // Tạo header trước
        FinishedGoodsIssueRequest fgir = new FinishedGoodsIssueRequest(actor.profileId(), req.note());
        fgir = requestRepo.saveAndFlush(fgir);

        // Lưu từng dòng
        for (FgIssueRequests.FgIssueItemRequest itemReq : req.items()) {
            FinishedGoodsIssueRequestItem item = new FinishedGoodsIssueRequestItem(
                fgir.getId(), itemReq.productId(), itemReq.quantity()
            );
            itemRepo.save(item);
        }
        itemRepo.flush();

        String warning = hasShortage ? "Số lượng xuất yêu cầu vượt quá tồn kho khả dụng hiện tại" : null;
        return detail(fgir.getId(), actor, hasShortage, warning);
    }

    public Page<FgIssueViews.FgIssueRequestView> list(
            Long productId, FgIssueRequestStatus status, Long requestedBy,
            int page, int size, String sort, String direction, CurrentUser actor, boolean isStaff) {

        PageRequest pageRequest = FgIssueQuery.page(page, size, sort, direction);
        final Long effectiveRequestedBy = isStaff ? actor.profileId() : requestedBy;

        Specification<FinishedGoodsIssueRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<FinishedGoodsIssueRequestItem> itemRoot = sub.from(FinishedGoodsIssueRequestItem.class);
                sub.select(itemRoot.get("requestId")).where(cb.equal(itemRoot.get("productId"), productId));
                predicates.add(root.get("id").in(sub));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (effectiveRequestedBy != null) {
                predicates.add(cb.equal(root.get("requestedBy"), effectiveRequestedBy));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FinishedGoodsIssueRequest> pageResult = requestRepo.findAll(spec, pageRequest);
        if (pageResult.isEmpty()) {
            return pageResult.map(r -> null);
        }

        Set<Long> staffIds = pageResult.getContent().stream().map(FinishedGoodsIssueRequest::getRequestedBy).collect(Collectors.toSet());
        Map<Long, Staff> staffMap = staffRepo.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        Set<Long> wmIds = pageResult.getContent().stream().map(FinishedGoodsIssueRequest::getReviewedBy).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, WarehouseManager> wmMap = wmRepo.findAllById(wmIds).stream()
                .collect(Collectors.toMap(WarehouseManager::getId, w -> w));

        List<Long> reqIds = pageResult.getContent().stream().map(FinishedGoodsIssueRequest::getId).toList();
        List<FinishedGoodsIssueRequestItem> allItems = itemRepo.findByRequestIdIn(reqIds);
        Map<Long, List<FinishedGoodsIssueRequestItem>> itemsByReq = allItems.stream()
                .collect(Collectors.groupingBy(FinishedGoodsIssueRequestItem::getRequestId));

        return pageResult.map(fgir -> {
            Staff requester = staffMap.get(fgir.getRequestedBy());
            WarehouseManager reviewer = wmMap.get(fgir.getReviewedBy());
            List<FinishedGoodsIssueRequestItem> items = itemsByReq.getOrDefault(fgir.getId(), List.of());
            int totalQty = items.stream().mapToInt(FinishedGoodsIssueRequestItem::getQuantity).sum();

            return new FgIssueViews.FgIssueRequestView(
                fgir.getId(),
                fgir.getRequestedBy(),
                requester != null ? requester.getFullName() : null,
                fgir.getReviewedBy(),
                reviewer != null ? reviewer.getFullName() : null,
                fgir.getStatus().name(),
                fgir.getRequestedAt(),
                fgir.getReviewedAt(),
                fgir.getNote(),
                items.size(),
                totalQty
            );
        });
    }

    public FgIssueViews.FgIssueRequestDetailView detail(Long id, CurrentUser actor) {
        return detail(id, actor, false, null);
    }

    private FgIssueViews.FgIssueRequestDetailView detail(
            Long id, CurrentUser actor, boolean hasShortage, String warning) {

        FinishedGoodsIssueRequest fgir = requestRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id));

        // STAFF chỉ xem được phiếu của mình (D11: 404 để không lộ tồn tại)
        if (actor != null && !fgir.getRequestedBy().equals(actor.profileId())) {
            User user = userRepo.findById(actor.userId()).orElse(null);
            if (user != null && user.getRole() == UserRole.STAFF) {
                throw new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id);
            }
        }

        Staff requester = staffRepo.findById(fgir.getRequestedBy()).orElse(null);
        WarehouseManager reviewer = fgir.getReviewedBy() != null ? wmRepo.findById(fgir.getReviewedBy()).orElse(null) : null;

        List<FinishedGoodsIssueRequestItem> items = itemRepo.findByRequestId(id);
        Set<Long> productIds = items.stream().map(FinishedGoodsIssueRequestItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepo.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        boolean shortageDetected = hasShortage;
        List<FgIssueViews.FgIssueItemView> itemViews = new ArrayList<>();

        for (FinishedGoodsIssueRequestItem item : items) {
            Product p = productMap.get(item.getProductId());
            BigDecimal currentStock = stockLedger.productBalance(item.getProductId());
            if (currentStock == null) currentStock = BigDecimal.ZERO;
            if (currentStock.compareTo(BigDecimal.valueOf(item.getQuantity())) < 0) {
                shortageDetected = true;
            }
            itemViews.add(new FgIssueViews.FgIssueItemView(
                item.getId(),
                item.getProductId(),
                p != null ? p.getName() : null,
                p != null ? p.getUnit() : null,
                item.getQuantity(),
                currentStock
            ));
        }

        if (shortageDetected && warning == null && fgir.getStatus() == FgIssueRequestStatus.PENDING) {
            warning = "Kho hiện tại không đủ tồn cho một số sản phẩm trong phiếu";
        }

        return new FgIssueViews.FgIssueRequestDetailView(
            fgir.getId(),
            fgir.getRequestedBy(),
            requester != null ? requester.getFullName() : null,
            fgir.getReviewedBy(),
            reviewer != null ? reviewer.getFullName() : null,
            fgir.getStatus().name(),
            fgir.getRequestedAt(),
            fgir.getReviewedAt(),
            fgir.getNote(),
            shortageDetected,
            warning,
            itemViews
        );
    }

    @Transactional
    public FgIssueViews.FgIssueRequestDetailView approve(Long id, CurrentUser actor) {
        FinishedGoodsIssueRequest fgir = requestRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id));

        if (fgir.getStatus() != FgIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được duyệt phiếu ở trạng thái PENDING (hiện tại: " + fgir.getStatus() + ")");
        }

        List<FinishedGoodsIssueRequestItem> items = itemRepo.findByRequestId(id);

        // Pre-check tồn kho từng dòng: nếu thiếu 1 dòng thì rollback toàn bộ, không movement nào
        for (FinishedGoodsIssueRequestItem item : items) {
            BigDecimal balance = stockLedger.productBalance(item.getProductId());
            if (balance == null) balance = BigDecimal.ZERO;
            BigDecimal needed = BigDecimal.valueOf(item.getQuantity());
            if (balance.compareTo(needed) < 0) {
                throw new BusinessException("Tồn kho thành phẩm #" + item.getProductId()
                        + " không đủ để xuất kho (hiện có: " + balance + ", yêu cầu: " + needed + ")");
            }
        }

        fgir.setStatus(FgIssueRequestStatus.APPROVED);
        fgir.setReviewedBy(actor.profileId());
        fgir.setReviewedAt(LocalDateTime.now());
        requestRepo.saveAndFlush(fgir);

        // Ghi stock_movements OUT từng sản phẩm
        for (FinishedGoodsIssueRequestItem item : items) {
            stockLedger.post(
                MovementType.OUT,
                null,
                item.getProductId(),
                BigDecimal.valueOf(item.getQuantity()),
                null,
                null,
                null,
                fgir.getId(),
                actor.userId(),
                "Xuất thành phẩm theo phiếu xuất #" + fgir.getId()
            );
        }

        return detail(id, actor);
    }

    @Transactional
    public FgIssueViews.FgIssueRequestDetailView reject(
            Long id, FgIssueRequests.RejectRequest req, CurrentUser actor) {

        FinishedGoodsIssueRequest fgir = requestRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id));

        if (fgir.getStatus() != FgIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được từ chối phiếu ở trạng thái PENDING (hiện tại: " + fgir.getStatus() + ")");
        }

        fgir.setStatus(FgIssueRequestStatus.REJECTED);
        fgir.setReviewedBy(actor.profileId());
        fgir.setReviewedAt(LocalDateTime.now());
        String originalNote = fgir.getNote() != null && !fgir.getNote().isBlank() ? fgir.getNote().trim() + " " : "";
        fgir.setNote(originalNote + "[TỪ CHỐI] " + req.reason().trim());
        requestRepo.saveAndFlush(fgir);

        return detail(id, actor);
    }

    @Transactional
    public FgIssueViews.FgIssueRequestDetailView cancel(Long id, CurrentUser actor) {
        FinishedGoodsIssueRequest fgir = requestRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id));

        if (!fgir.getRequestedBy().equals(actor.profileId())) {
            throw new ResourceNotFoundException("Không tìm thấy phiếu xuất thành phẩm #" + id);
        }

        if (fgir.getStatus() != FgIssueRequestStatus.PENDING) {
            throw new BusinessException("Chỉ được hủy phiếu ở trạng thái PENDING (hiện tại: " + fgir.getStatus() + ")");
        }

        fgir.setStatus(FgIssueRequestStatus.CANCELLED);
        requestRepo.saveAndFlush(fgir);

        return detail(id, actor);
    }
}