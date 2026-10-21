package mrp.purchase.service;


import mrp.catalog.entity.Material;
import mrp.catalog.entity.Supplier;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.SupplierRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.inventory.entity.MovementType;
import mrp.inventory.service.StockLedger;
import mrp.purchase.dto.PurchaseQuery;
import mrp.purchase.dto.PurchaseRequests;
import mrp.purchase.dto.PurchaseViews;
import mrp.purchase.entity.PurchaseReceipt;
import mrp.purchase.entity.PurchaseReceiptItem;
import mrp.purchase.entity.PurchaseReceiptStatus;
import mrp.purchase.repository.PurchaseReceiptItemRepository;
import mrp.purchase.repository.PurchaseReceiptRepository;
import mrp.security.CurrentUser;
import mrp.user.entity.WarehouseManager;
import mrp.user.repository.WarehouseManagerRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service phiếu nhập kho NVL (P4). Quy tắc:
 * <ul>
 *   <li>Tạo / sửa dòng chỉ khi PENDING; COMPLETED / CANCELLED là trạng thái cuối
 *       (trigger {@code trg_final_immutable} + {@code trg_freeze_items} là backstop DB).</li>
 *   <li>{@code amount = round(quantity × unit_price, 2)} tính server [DB {@code ck_pri_amount}];
 *       {@code total_amount = Σ amount} [SVC].</li>
 *   <li>{@code complete}: đặt COMPLETED, flush, rồi post movement IN từng dòng qua {@link StockLedger#post}.
 *       {@code created_by} = {@code actor.userId()} [users.id], khác {@code warehouse_manager_id} [profile id].
 *       Trigger {@code fn_sm_validate} yêu cầu receipt COMPLETED + số lượng khớp dòng → flush trước khi post.</li>
 *   <li>{@link PurchaseReceiptRepository#findByIdForUpdate} khóa bi quan [D12] serialize complete / cancel / replace-items.</li>
 *   <li>Material phải active khi tạo dòng và khi complete (gọi {@link CatalogService#requireActiveMaterial}) [P3].</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PurchaseService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "receiptDate", "totalAmount", "status");

    private final PurchaseReceiptRepository receipts;
    private final PurchaseReceiptItemRepository items;
    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final WarehouseManagerRepository managers;
    private final StockLedger ledger;
    private final CatalogService catalog;

    public PurchaseService(PurchaseReceiptRepository receipts, PurchaseReceiptItemRepository items,
                           SupplierRepository suppliers, MaterialRepository materials,
                           WarehouseManagerRepository managers, StockLedger ledger, CatalogService catalog) {
        this.receipts = receipts; this.items = items; this.suppliers = suppliers;
        this.materials = materials; this.managers = managers; this.ledger = ledger; this.catalog = catalog;
    }

    @Transactional
    public PurchaseViews.PurchaseReceiptDetailView create(PurchaseRequests.PurchaseReceiptRequest request, CurrentUser actor) {
        if (!suppliers.existsById(request.supplierId())) throw missing("nhà cung cấp");
        List<PurchaseReceiptItem> newItems = buildItems(null, request.items());
        BigDecimal total = sumAmounts(newItems);
        PurchaseReceipt receipt = new PurchaseReceipt(request.supplierId(), actor.profileId());
        receipt.setStatus(PurchaseReceiptStatus.PENDING);
        receipt.setTotalAmount(total);
        receipts.saveAndFlush(receipt);
        for (PurchaseReceiptItem it : newItems) { it.setPurchaseReceiptId(receipt.getId()); items.save(it); }
        items.flush();
        return detailView(receipt);
    }

    public Page<PurchaseViews.PurchaseReceiptView> list(Long supplierId, PurchaseReceiptStatus status,
            LocalDate from, LocalDate to, Long warehouseManagerId, int page, int size, String sort, String direction) {
        if (from != null && to != null && from.isAfter(to)) throw new BadRequestException("from phải nhỏ hơn hoặc bằng to");
        PageRequest pageable = PurchaseQuery.page(page, size, sort, direction, SORT_FIELDS);
        Page<PurchaseReceipt> result = receipts.findAll(specification(supplierId, status, from, to, warehouseManagerId), pageable);
        return enrichList(result);
    }

    public PurchaseViews.PurchaseReceiptDetailView detail(Long id) {
        PurchaseReceipt receipt = receipts.findById(id).orElseThrow(() -> missing("phiếu nhập"));
        return detailView(receipt);
    }

    @Transactional
    public PurchaseViews.PurchaseReceiptDetailView replaceItems(Long id, PurchaseRequests.PurchaseReceiptRequest request) {
        PurchaseReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(() -> missing("phiếu nhập"));
        if (receipt.getStatus() != PurchaseReceiptStatus.PENDING)
            throw new BusinessException("Chỉ sửa được phiếu đang PENDING");
        if (!suppliers.existsById(request.supplierId())) throw missing("nhà cung cấp");
        items.deleteByPurchaseReceiptId(id); // bulk DELETE (@Modifying) — trigger cho phép khi PENDING
        List<PurchaseReceiptItem> newItems = buildItems(id, request.items());
        BigDecimal total = sumAmounts(newItems);
        items.saveAll(newItems);
        items.flush();
        receipt.setSupplierId(request.supplierId());
        receipt.setTotalAmount(total);
        receipts.saveAndFlush(receipt);
        return detailView(receipt);
    }

    @Transactional
    public PurchaseViews.PurchaseReceiptDetailView complete(Long id, CurrentUser actor) {
        PurchaseReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(() -> missing("phiếu nhập"));
        if (receipt.getStatus() != PurchaseReceiptStatus.PENDING)
            throw new BusinessException("Chỉ hoàn thành phiếu đang PENDING");
        receipt.setStatus(PurchaseReceiptStatus.COMPLETED);
        receipts.saveAndFlush(receipt); // flush để fn_sm_validate thấy COMPLETED
        List<PurchaseReceiptItem> rows = items.findByPurchaseReceiptIdOrderByMaterialIdAsc(id);
        String note = "Nhập NVL theo phiếu nhập #" + id;
        for (PurchaseReceiptItem item : rows) {
            catalog.requireActiveMaterial(item.getMaterialId()); // re-check active trước khi post [P3]
            ledger.post(MovementType.IN, item.getMaterialId(), null, item.getQuantity(),
                    receipt.getId(), null, null, null, actor.userId(), note);
        }
        return detailView(receipt);
    }

    @Transactional
    public PurchaseViews.PurchaseReceiptDetailView cancel(Long id) {
        PurchaseReceipt receipt = receipts.findByIdForUpdate(id).orElseThrow(() -> missing("phiếu nhập"));
        if (receipt.getStatus() != PurchaseReceiptStatus.PENDING)
            throw new BusinessException("Chỉ hủy phiếu đang PENDING");
        receipt.setStatus(PurchaseReceiptStatus.CANCELLED);
        receipts.saveAndFlush(receipt);
        return detailView(receipt);
    }

    // ---- helpers ----

    private List<PurchaseReceiptItem> buildItems(Long receiptId, List<PurchaseRequests.PurchaseReceiptItemRequest> itemReqs) {
        Set<Long> seen = new HashSet<>();
        List<PurchaseReceiptItem> result = new ArrayList<>(itemReqs.size());
        for (PurchaseRequests.PurchaseReceiptItemRequest ir : itemReqs) {
            if (!seen.add(ir.materialId()))
                throw new BusinessException("Nguyên vật liệu " + ir.materialId() + " trùng trong phiếu");
            catalog.requireActiveMaterial(ir.materialId()); // 404 nếu không có, 409 nếu inactive
            BigDecimal amount = computeAmount(ir.quantity(), ir.unitPrice());
            result.add(new PurchaseReceiptItem(receiptId, ir.materialId(), ir.quantity(), ir.unitPrice(), amount));
        }
        return result;
    }

    private static BigDecimal computeAmount(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumAmounts(List<PurchaseReceiptItem> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReceiptItem r : rows) total = total.add(r.getAmount());
        return total;
    }

    private static Specification<PurchaseReceipt> specification(Long supplierId, PurchaseReceiptStatus status,
            LocalDate from, LocalDate to, Long warehouseManagerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (supplierId != null) predicates.add(cb.equal(root.get("supplierId"), supplierId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("receiptDate"), from.atStartOfDay()));
            if (to != null) predicates.add(cb.lessThan(root.<LocalDateTime>get("receiptDate"), to.plusDays(1).atStartOfDay()));
            if (warehouseManagerId != null) predicates.add(cb.equal(root.get("warehouseManagerId"), warehouseManagerId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Page<PurchaseViews.PurchaseReceiptView> enrichList(Page<PurchaseReceipt> page) {
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> managerIds = new HashSet<>();
        for (PurchaseReceipt r : page.getContent()) {
            supplierIds.add(r.getSupplierId());
            managerIds.add(r.getWarehouseManagerId());
        }
        Map<Long, String> supplierNames = supplierIds.isEmpty() ? Map.of()
                : suppliers.findAllById(supplierIds).stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        Map<Long, String> managerNames = managerIds.isEmpty() ? Map.of()
                : managers.findAllById(managerIds).stream().collect(Collectors.toMap(WarehouseManager::getId, WarehouseManager::getFullName));
        return page.map(r -> new PurchaseViews.PurchaseReceiptView(r.getId(), r.getSupplierId(),
                supplierNames.get(r.getSupplierId()), r.getWarehouseManagerId(),
                managerNames.get(r.getWarehouseManagerId()), r.getReceiptDate(), r.getStatus().name(), r.getTotalAmount()));
    }

    private PurchaseViews.PurchaseReceiptDetailView detailView(PurchaseReceipt receipt) {
        Supplier supplier = suppliers.findById(receipt.getSupplierId()).orElse(null);
        WarehouseManager manager = managers.findById(receipt.getWarehouseManagerId()).orElse(null);
        List<PurchaseReceiptItem> rows = items.findByPurchaseReceiptIdOrderByMaterialIdAsc(receipt.getId());
        Set<Long> materialIds = new HashSet<>();
        for (PurchaseReceiptItem i : rows) materialIds.add(i.getMaterialId());
        Map<Long, Material> materialsById = materialIds.isEmpty() ? Map.of()
                : materials.findAllById(materialIds).stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        List<PurchaseViews.PurchaseReceiptItemView> itemViews = new ArrayList<>(rows.size());
        for (PurchaseReceiptItem i : rows) {
            Material m = materialsById.get(i.getMaterialId());
            itemViews.add(new PurchaseViews.PurchaseReceiptItemView(i.getMaterialId(),
                    m == null ? null : m.getCode(), m == null ? null : m.getName(), m == null ? null : m.getUnit(),
                    i.getQuantity(), i.getUnitPrice(), i.getAmount()));
        }
        return new PurchaseViews.PurchaseReceiptDetailView(receipt.getId(), receipt.getSupplierId(),
                supplier == null ? null : supplier.getName(), receipt.getWarehouseManagerId(),
                manager == null ? null : manager.getFullName(), receipt.getReceiptDate(),
                receipt.getStatus().name(), receipt.getTotalAmount(), itemViews);
    }

    private static ResourceNotFoundException missing(String resource) {
        return new ResourceNotFoundException("Không tìm thấy " + resource);
    }
}