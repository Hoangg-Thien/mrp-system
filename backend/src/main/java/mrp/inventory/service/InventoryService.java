package mrp.inventory.service;


import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.dto.InventoryQuery;
import mrp.inventory.dto.InventoryRequests;
import mrp.inventory.dto.InventoryViews;
import mrp.inventory.dto.MovementFilter;
import mrp.inventory.entity.MovementType;
import mrp.inventory.entity.StockMovement;
import mrp.inventory.repository.StockMovementRepository;
import mrp.security.CurrentUser;
import mrp.user.entity.User;
import mrp.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import mrp.inventory.dto.InventoryRequests.AdjustmentDirection;
import mrp.inventory.dto.InventoryRequests.StockAdjustmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static mrp.inventory.dto.InventoryViews.*;

/**
 * Nghiệp vụ tồn kho P3: đọc tồn / sổ kho và ghi điều chỉnh tồn (WM).
 * Mọi thay đổi tồn đều đi qua INSERT {@code stock_movements} [D11]; tồn do trigger tính.
 */
@Service
public class InventoryService {
    private final InventoryQuery queries;
    private final StockMovementRepository movements;
    private final StockLedger ledger;
    private final MaterialRepository materials;
    private final ProductRepository products;
    private final UserRepository users;

    public InventoryService(InventoryQuery queries, StockMovementRepository movements, StockLedger ledger,
                            MaterialRepository materials, ProductRepository products, UserRepository users) {
        this.queries = queries;
        this.movements = movements;
        this.ledger = ledger;
        this.materials = materials;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<MaterialStockView> materials(String keyword, boolean positiveOnly, Boolean active,
                                             int page, int size, String sort, String direction) {
        return queries.materials(keyword, positiveOnly, active, page, size, sort, direction);
    }

    @Transactional(readOnly = true)
    public Page<ProductStockView> products(String keyword, Long categoryId, boolean positiveOnly, Boolean active,
                                           int page, int size, String sort, String direction) {
        return queries.products(keyword, categoryId, positiveOnly, active, page, size, sort, direction);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementView> movements(MovementFilter filter, int page, int size, String sort, String direction) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("from phải nhỏ hơn hoặc bằng to");
        }
        Page<StockMovement> result = movements.findAll(specification(filter),
                InventoryQuery.page(page, size, sort, direction, Set.of("id", "createdAt", "quantity", "movementType")));
        return enrich(result);
    }

    private static Specification<StockMovement> specification(MovementFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.type() != null) predicates.add(cb.equal(root.get("movementType"), filter.type()));
            if (filter.materialId() != null) predicates.add(cb.equal(root.get("materialId"), filter.materialId()));
            if (filter.productId() != null) predicates.add(cb.equal(root.get("productId"), filter.productId()));
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<java.time.LocalDateTime>get("createdAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.<java.time.LocalDateTime>get("createdAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (filter.purchaseReceiptId() != null) {
                predicates.add(cb.equal(root.get("purchaseReceiptId"), filter.purchaseReceiptId()));
            }
            if (filter.materialIssueRequestId() != null) {
                predicates.add(cb.equal(root.get("materialIssueRequestId"), filter.materialIssueRequestId()));
            }
            if (filter.productionId() != null) predicates.add(cb.equal(root.get("productionId"), filter.productionId()));
            if (filter.fgIssueRequestId() != null) {
                predicates.add(cb.equal(root.get("fgIssueRequestId"), filter.fgIssueRequestId()));
            }
            if (filter.createdBy() != null) predicates.add(cb.equal(root.get("createdBy"), filter.createdBy()));
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Điều chỉnh tồn (chỉ WM). Ghi một dòng ADJUST_IN / ADJUST_OUT mới; không sửa / xóa dòng cũ
     * và không update {@code inventories} [D11, DB trg_inv_guard / trg_sm_ro].
     * Giảm nhiều hơn tồn hiện tại bị từ chối (CHECK {@code quantity >= 0} của DB là lưới cuối → 409).
     */
    @Transactional
    public StockAdjustmentView adjust(StockAdjustmentRequest request, CurrentUser actor) {
        boolean forMaterial = request.materialId() != null;
        if (forMaterial == (request.productId() != null)) {
            throw new BadRequestException("Phải có đúng một trong materialId hoặc productId");
        }
        BigDecimal quantity = request.quantity();
        String note = request.note().trim();
        MovementType type = request.direction() == AdjustmentDirection.IN ? MovementType.ADJUST_IN : MovementType.ADJUST_OUT;
        Long materialId = null;
        Long productId = null;
        String materialCode = null;
        String materialName = null;
        String productName = null;
        String unit;
        if (forMaterial) {
            Material material = materials.findById(request.materialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên vật liệu"));
            materialId = material.getId();
            materialCode = material.getCode();
            materialName = material.getName();
            unit = material.getUnit();
        } else {
            Product product = products.findById(request.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new BadRequestException("Số lượng điều chỉnh của thành phẩm phải là số nguyên");
            }
            productId = product.getId();
            productName = product.getName();
            unit = product.getUnit();
        }
        BigDecimal before = ledger.balanceOf(materialId, productId);
        if (type == MovementType.ADJUST_OUT && quantity.compareTo(before) > 0) {
            throw new BusinessException("Số lượng điều chỉnh giảm (" + plain(quantity) + ") vượt tồn kho hiện tại ("
                    + plain(before) + ")");
        }
        StockLedger.PostedMovement posted = ledger.postAdjustment(type, materialId, productId, quantity,
                actor.userId(), note);
        return new StockAdjustmentView(posted.id(), type, materialId, materialCode, materialName, productId,
                productName, unit, quantity, before, ledger.balanceOf(materialId, productId), note,
                actor.userId(), actor.username(), posted.createdAt());
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    /** Bổ sung tên vật tư / sản phẩm / username cho các dòng sổ kho bằng 3 truy vấn theo id (không N+1). */
    private Page<StockMovementView> enrich(Page<StockMovement> page) {
        Set<Long> materialIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (StockMovement row : page.getContent()) {
            if (row.getMaterialId() != null) materialIds.add(row.getMaterialId());
            if (row.getProductId() != null) productIds.add(row.getProductId());
            if (row.getCreatedBy() != null) userIds.add(row.getCreatedBy());
        }
        Map<Long, Material> materialsById = materialIds.isEmpty() ? Map.of() : materials.findAllById(materialIds)
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<Long, Product> productsById = productIds.isEmpty() ? Map.of() : products.findAllById(productIds)
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> usernames = userIds.isEmpty() ? Map.of() : users.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return page.map(row -> view(row, materialsById, productsById, usernames));
    }

    private static StockMovementView view(StockMovement row, Map<Long, Material> materialsById,
                                          Map<Long, Product> productsById, Map<Long, String> usernames) {
        Material material = row.getMaterialId() == null ? null : materialsById.get(row.getMaterialId());
        Product product = row.getProductId() == null ? null : productsById.get(row.getProductId());
        String unit = material != null ? material.getUnit() : (product != null ? product.getUnit() : null);
        return new StockMovementView(row.getId(), row.getMovementType(), row.getQuantity(),
                row.getMaterialId(), material == null ? null : material.getCode(),
                material == null ? null : material.getName(),
                row.getProductId(), product == null ? null : product.getName(), unit,
                row.getPurchaseReceiptId(), row.getMaterialIssueRequestId(), row.getProductionId(),
                row.getFgIssueRequestId(), sourceType(row), sourceId(row),
                row.getCreatedBy(), usernames.get(row.getCreatedBy()), row.getCreatedAt(), row.getNote());
    }

    /** Chứng từ nguồn của dòng sổ (ADJUST_* không có nguồn). */
    private static String sourceType(StockMovement row) {
        if (row.getPurchaseReceiptId() != null) return "PURCHASE_RECEIPT";
        if (row.getMaterialIssueRequestId() != null) return "MATERIAL_ISSUE_REQUEST";
        if (row.getProductionId() != null) return "PRODUCTION";
        if (row.getFgIssueRequestId() != null) return "FG_ISSUE_REQUEST";
        return null;
    }

    private static Long sourceId(StockMovement row) {
        if (row.getPurchaseReceiptId() != null) return row.getPurchaseReceiptId();
        if (row.getMaterialIssueRequestId() != null) return row.getMaterialIssueRequestId();
        if (row.getProductionId() != null) return row.getProductionId();
        return row.getFgIssueRequestId();
    }
}