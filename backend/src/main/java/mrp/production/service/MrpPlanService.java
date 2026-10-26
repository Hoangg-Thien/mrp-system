package mrp.production.service;

import mrp.bom.entity.Bom;
import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BusinessException;
import mrp.inventory.service.StockLedger;
import mrp.production.entity.Production;
import mrp.production.entity.ProductionRequest;
import mrp.production.entity.ProductionRequestStatus;
import mrp.production.entity.ProductionStatus;
import mrp.production.repository.ProductionRepository;
import mrp.production.repository.ProductionRequestRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dịch vụ tính toán kế hoạch nhu cầu nguyên vật liệu (MRP Plan) và lượng cam kết (committed).
 */
@Service
public class MrpPlanService {

    private final CatalogService catalogService;
    private final BomRepository bomRepo;
    private final BomItemRepository bomItemRepo;
    private final StockLedger stockLedger;
    private final ProductionRequestRepository prRepo;
    private final ProductionRepository prodRepo;
    private final MaterialRepository materialRepo;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public MrpPlanService(
            CatalogService catalogService,
            BomRepository bomRepo,
            BomItemRepository bomItemRepo,
            StockLedger stockLedger,
            ProductionRequestRepository prRepo,
            ProductionRepository prodRepo,
            MaterialRepository materialRepo,
            JdbcTemplate jdbc) {
        this.catalogService = catalogService;
        this.bomRepo = bomRepo;
        this.bomItemRepo = bomItemRepo;
        this.stockLedger = stockLedger;
        this.prRepo = prRepo;
        this.prodRepo = prodRepo;
        this.materialRepo = materialRepo;
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public MrpCalculator.PlanResult getPlan(Long productId, Integer quantity) {
        if (productId == null) throw new BusinessException("productId không được để trống");
        if (quantity == null || quantity <= 0) throw new BusinessException("quantity phải lớn hơn 0");

        // Kiểm tra sản phẩm tồn tại
        Product product = catalogService.product(productId);

        // Kiểm tra BOM active
        Bom bom = bomRepo.findActiveByProductId(productId)
                .orElseThrow(() -> new BusinessException("Sản phẩm #" + productId + " không có BOM đang hoạt động"));
        List<BomItem> bomItems = bomItemRepo.findByBomIdOrderByMaterialIdAsc(bom.getId());
        if (bomItems.isEmpty()) {
            throw new BusinessException("BOM #" + bom.getId() + " chưa có nguyên vật liệu");
        }

        // Tồn thành phẩm hiện tại
        BigDecimal fgOnHand = stockLedger.productBalance(productId);

        // fgReserved: tổng số lượng phiếu xuất thành phẩm PENDING
        BigDecimal fgReserved = jdbc.queryForObject("""
            SELECT COALESCE(SUM(i.quantity), 0)
              FROM finished_goods_issue_request_items i
              JOIN finished_goods_issue_requests r ON r.id = i.request_id
             WHERE r.status = 'PENDING' AND i.product_id = ?
            """, BigDecimal.class, productId);
        if (fgReserved == null) fgReserved = BigDecimal.ZERO;

        // fgIncoming: tổng (số lượng yêu cầu - đã hoàn thành) của các PR đang mở (PENDING / IN_PROGRESS) của sản phẩm
        List<ProductionRequest> openPrsForProduct = prRepo.findByProductIdAndStatusIn(
                productId, List.of(ProductionRequestStatus.PENDING, ProductionRequestStatus.IN_PROGRESS));
        int incomingTotal = 0;
        for (ProductionRequest pr : openPrsForProduct) {
            int completedQty = prodRepo.findByProductionRequestIdAndStatus(pr.getId(), ProductionStatus.COMPLETED)
                    .stream().mapToInt(Production::getQuantity).sum();
            incomingTotal += Math.max(0, pr.getQuantity() - completedQty);
        }
        BigDecimal fgIncoming = BigDecimal.valueOf(incomingTotal);

        // Danh sách các PR đang mở trên toàn hệ thống (để tính committed của NVL)
        List<ProductionRequest> allOpenPrs = prRepo.findByStatusIn(
                List.of(ProductionRequestStatus.PENDING, ProductionRequestStatus.IN_PROGRESS));

        List<MrpCalculator.MaterialDemandInput> materialInputs = new ArrayList<>();
        Set<Long> matIds = bomItems.stream().map(BomItem::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> matMap = materialRepo.findAllById(matIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        Map<Long, BigDecimal> committedMap = calculateCommittedBatch(allOpenPrs, matIds);

        for (BomItem item : bomItems) {
            Material m = matMap.get(item.getMaterialId());
            String code = m != null ? m.getCode() : "";
            String name = m != null ? m.getName() : "";
            String unit = m != null ? m.getUnit() : "";

            BigDecimal onHand = stockLedger.materialBalance(item.getMaterialId());

            // committed: tổng max(0, BOM_item_qty * pr.qty - đã xuất APPROVED) của các PR đang mở
            BigDecimal committed = committedMap.getOrDefault(item.getMaterialId(), BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));

            materialInputs.add(new MrpCalculator.MaterialDemandInput(
                item.getMaterialId(),
                code,
                name,
                unit,
                item.getQuantity(),
                onHand,
                committed
            ));
        }

        return MrpCalculator.calculate(productId, quantity, fgOnHand, fgReserved, fgIncoming, materialInputs);
    }

    public Map<Long, BigDecimal> calculateCommittedBatch(Collection<ProductionRequest> openPrs, Collection<Long> materialIds) {
        if (openPrs == null || openPrs.isEmpty() || materialIds == null || materialIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> prIds = openPrs.stream().map(ProductionRequest::getId).filter(Objects::nonNull).distinct().toList();
        List<Long> matIds = materialIds.stream().filter(Objects::nonNull).distinct().toList();
        if (prIds.isEmpty() || matIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = """
            SELECT
                bi.material_id,
                SUM(CASE
                    WHEN (bi.quantity * pr.quantity - COALESCE(issued.issued_qty, 0)) > 0
                    THEN (bi.quantity * pr.quantity - COALESCE(issued.issued_qty, 0))
                    ELSE 0
                END) AS committed_qty
            FROM production_requests pr
            JOIN bom_items bi ON bi.bom_id = pr.bom_id
            LEFT JOIN (
                SELECT mir.production_request_id, miri.material_id, SUM(miri.quantity) AS issued_qty
                FROM material_issue_requests mir
                JOIN material_issue_request_items miri ON miri.request_id = mir.id
                WHERE mir.status = 'APPROVED'
                  AND mir.production_request_id IN (:prIds)
                GROUP BY mir.production_request_id, miri.material_id
            ) issued ON issued.production_request_id = pr.id AND issued.material_id = bi.material_id
            WHERE pr.id IN (:prIds)
              AND bi.material_id IN (:matIds)
            GROUP BY bi.material_id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("prIds", prIds)
                .addValue("matIds", matIds);

        Map<Long, BigDecimal> result = new HashMap<>();
        namedJdbc.query(sql, params, rs -> {
            Long matId = rs.getLong("material_id");
            BigDecimal qty = rs.getBigDecimal("committed_qty");
            result.put(matId, (qty != null ? qty : BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
        });

        return result;
    }

    public BigDecimal calculateCommitted(List<ProductionRequest> openPrs, Long materialId) {
        if (materialId == null) return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        return calculateCommittedBatch(openPrs, Set.of(materialId))
                .getOrDefault(materialId, BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
    }
}
