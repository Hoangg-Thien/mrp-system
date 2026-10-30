package mrp.bom.service;


import mrp.bom.dto.BomQuery;
import mrp.bom.dto.BomRequests;
import mrp.bom.dto.BomViews;
import mrp.bom.entity.Bom;
import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;
import mrp.production.entity.Production;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý công thức sản phẩm BOM (P5).
 * <ul>
 *   <li>Tạo BOM: sản phẩm active, >= 1 dòng, NVL active, không trùng trong BOM; version tự tăng.</li>
 *   <li>Chỉ 1 BOM active cho mỗi sản phẩm: vô hiệu BOM cũ trước khi kích hoạt BOM mới.</li>
 *   <li>Activate / Deactivate: đổi trạng thái BOM, serialize bằng pessimistic lock.</li>
 *   <li>Không có API sửa dòng: BOM đã có production_request thì DB chặn sửa; muốn đổi phải tạo version mới.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class BomService {

    private final BomRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final CatalogService catalogService;

    public BomService(BomRepository bomRepository,
                      BomItemRepository bomItemRepository,
                      ProductRepository productRepository,
                      MaterialRepository materialRepository,
                      CatalogService catalogService) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.productRepository = productRepository;
        this.materialRepository = materialRepository;
        this.catalogService = catalogService;
    }

    @Transactional
    public BomViews.BomDetailView create(BomRequests.CreateBomRequest request) {
        // 1. Kiểm tra sản phẩm tồn tại và đang active
        catalogService.requireActiveProduct(request.productId());

        // 2. Kiểm tra danh sách NVL: không trùng và đều active
        Set<Long> seenMaterials = new HashSet<>();
        for (BomRequests.BomItemRequest item : request.items()) {
            if (!seenMaterials.add(item.materialId())) {
                throw new BusinessException("Nguyên vật liệu trùng trong công thức BOM");
            }
            catalogService.requireActiveMaterial(item.materialId());
        }

        // 3. Tính version mới (max + 1)
        Integer maxVersion = bomRepository.findMaxVersionByProductId(request.productId());
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        // 4. Vô hiệu hóa BOM active cũ trước khi tạo BOM active mới (tránh đụng unique index uq_boms_one_active)
        bomRepository.deactivateAllByProductId(request.productId());
        bomRepository.flush();

        // 5. Lưu BOM mới
        Bom bom = new Bom(request.productId(), nextVersion, true);
        bom = bomRepository.saveAndFlush(bom);

        // 6. Lưu các dòng bom_items
        List<BomItem> items = new ArrayList<>();
        for (BomRequests.BomItemRequest itemReq : request.items()) {
            items.add(new BomItem(bom.getId(), itemReq.materialId(), itemReq.quantity()));
        }
        bomItemRepository.saveAllAndFlush(items);

        return detail(bom.getId());
    }

    public Page<BomViews.BomView> list(Long productId, Boolean active, int page, int size, String sort, String direction) {
        PageRequest pageRequest = BomQuery.page(page, size, sort, direction);

        Specification<Bom> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("productId"), productId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Bom> bomPage = bomRepository.findAll(spec, pageRequest);
        if (bomPage.isEmpty()) {
            return bomPage.map(b -> null);
        }

        // Batch load products để tránh N+1
        Set<Long> productIds = bomPage.getContent().stream()
                .map(Bom::getProductId)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Batch count items để tránh N+1
        List<Long> bomIds = bomPage.getContent().stream().map(Bom::getId).toList();
        List<BomItem> allItems = bomItemRepository.findByBomIdIn(bomIds);
        Map<Long, Long> countMap = allItems.stream()
                .collect(Collectors.groupingBy(BomItem::getBomId, Collectors.counting()));

        return bomPage.map(b -> {
            Product p = productMap.get(b.getProductId());
            String productName = p != null ? p.getName() : null;
            String productUnit = p != null ? p.getUnit() : null;
            int itemCount = countMap.getOrDefault(b.getId(), 0L).intValue();
            return new BomViews.BomView(
                b.getId(),
                b.getProductId(),
                productName,
                productUnit,
                b.getVersion(),
                b.isActive(),
                b.getCreatedAt(),
                itemCount
            );
        });
    }

    public BomViews.BomDetailView detail(Long id) {
        Bom bom = bomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy BOM #" + id));

        Product product = productRepository.findById(bom.getProductId()).orElse(null);
        String productName = product != null ? product.getName() : null;
        String productUnit = product != null ? product.getUnit() : null;

        List<BomItem> items = bomItemRepository.findByBomIdOrderByMaterialIdAsc(bom.getId());

        Set<Long> materialIds = items.stream().map(BomItem::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> materialMap = materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        List<BomViews.BomItemView> itemViews = items.stream().map(item -> {
            Material m = materialMap.get(item.getMaterialId());
            return new BomViews.BomItemView(
                item.getId(),
                item.getMaterialId(),
                m != null ? m.getCode() : null,
                m != null ? m.getName() : null,
                m != null ? m.getUnit() : null,
                item.getQuantity()
            );
        }).toList();

        return new BomViews.BomDetailView(
            bom.getId(),
            bom.getProductId(),
            productName,
            productUnit,
            bom.getVersion(),
            bom.isActive(),
            bom.getCreatedAt(),
            itemViews
        );
    }

    @Transactional
    public BomViews.BomDetailView activate(Long id) {
        Bom bom = bomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy BOM #" + id));

        // Kiểm tra sản phẩm còn active không
        catalogService.requireActiveProduct(bom.getProductId());

        if (!bom.isActive()) {
            // Vô hiệu hóa BOM đang active khác của cùng sản phẩm
            bomRepository.deactivateAllByProductId(bom.getProductId());
            bomRepository.flush();

            bom.setActive(true);
            bomRepository.saveAndFlush(bom);
        }

        return detail(bom.getId());
    }

    @Transactional
    public BomViews.BomDetailView deactivate(Long id) {
        Bom bom = bomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy BOM #" + id));

        if (bom.isActive()) {
            bom.setActive(false);
            bomRepository.saveAndFlush(bom);
        }

        return detail(bom.getId());
    }

    /**
     * Helper cho P6 (MRP / Production Request): lấy BOM active và kiểm tra BOM không rỗng.
     */
    public Bom requireActiveBom(Long productId) {
        Bom bom = bomRepository.findActiveByProductId(productId)
                .orElseThrow(() -> new BusinessException("Sản phẩm #" + productId + " không có BOM đang hoạt động"));
        if (!bomItemRepository.existsByBomId(bom.getId())) {
            throw new BusinessException("BOM #" + bom.getId() + " chưa có nguyên vật liệu");
        }
        return bom;
    }

    /**
     * Helper cho P6: lấy danh sách các dòng chi tiết của BOM.
     */
    public List<BomItem> getBomItems(Long bomId) {
        return bomItemRepository.findByBomIdOrderByMaterialIdAsc(bomId);
    }
}