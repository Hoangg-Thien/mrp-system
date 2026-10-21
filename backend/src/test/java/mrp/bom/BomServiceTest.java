package mrp.bom;


import mrp.bom.dto.BomRequests;
import mrp.bom.dto.BomViews;
import mrp.bom.entity.Bom;
import mrp.bom.entity.BomItem;
import mrp.bom.repository.BomItemRepository;
import mrp.bom.repository.BomRepository;
import mrp.bom.service.BomService;
import mrp.catalog.dto.CatalogRequests;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.service.CatalogService;
import mrp.common.exception.BadRequestException;
import mrp.common.exception.BusinessException;
import mrp.common.exception.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests cho {@link BomService} (P5) theo Mockito pattern của dự án.
 */
@ExtendWith(MockitoExtension.class)
class BomServiceTest {

    @Mock private BomRepository bomRepository;
    @Mock private BomItemRepository bomItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private CatalogService catalogService;

    private BomService service;

    @BeforeEach
    void setUp() {
        service = new BomService(bomRepository, bomItemRepository, productRepository, materialRepository, catalogService);
    }

    private Product createProduct(Long id, String name, String unit, boolean active) {
        try {
            Constructor<Product> c = Product.class.getDeclaredConstructor();
            c.setAccessible(true);
            Product p = c.newInstance();
            p.update(new CatalogRequests.ProductRequest(1L, name, "desc", unit, BigDecimal.valueOf(500000)));
            p.setActive(active);
            Field f = Product.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Material createMaterial(Long id, String code, String name, String unit, boolean active) {
        try {
            Constructor<Material> c = Material.class.getDeclaredConstructor();
            c.setAccessible(true);
            Material m = c.newInstance();
            m.update(new CatalogRequests.MaterialRequest(code, name, unit, BigDecimal.valueOf(50000)));
            m.setActive(active);
            Field f = Material.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
            return m;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Bom createBom(Long id, Long productId, int version, boolean active) {
        Bom b = new Bom(productId, version, active);
        try {
            Field f = Bom.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(b, id);
        } catch (Exception ignored) { }
        return b;
    }

    private BomItem createBomItem(Long id, Long bomId, Long materialId, BigDecimal qty) {
        BomItem item = new BomItem(bomId, materialId, qty);
        try {
            Field f = BomItem.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(item, id);
        } catch (Exception ignored) { }
        return item;
    }

    @Test
    void bom01_createTableBomV1Active() {
        Long productId = 10L;
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);
        Material matMatBan = createMaterial(101L, "MAT-01", "Mặt bàn", "tấm", true);
        Material matChanBan = createMaterial(102L, "CHAN-01", "Chân bàn", "cái", true);

        when(catalogService.requireActiveProduct(productId)).thenReturn(product);
        when(catalogService.requireActiveMaterial(101L)).thenReturn(matMatBan);
        when(catalogService.requireActiveMaterial(102L)).thenReturn(matChanBan);
        when(bomRepository.findMaxVersionByProductId(productId)).thenReturn(null);

        Bom savedBom = createBom(1L, productId, 1, true);
        when(bomRepository.saveAndFlush(any(Bom.class))).thenReturn(savedBom);
        when(bomRepository.findById(1L)).thenReturn(Optional.of(savedBom));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        List<BomItem> savedItems = List.of(
            createBomItem(1001L, 1L, 101L, new BigDecimal("1.000")),
            createBomItem(1002L, 1L, 102L, new BigDecimal("4.000"))
        );
        when(bomItemRepository.findByBomIdOrderByMaterialIdAsc(1L)).thenReturn(savedItems);
        when(materialRepository.findAllById(any())).thenReturn(List.of(matMatBan, matChanBan));

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(101L, new BigDecimal("1.000")),
            new BomRequests.BomItemRequest(102L, new BigDecimal("4.000"))
        ));

        BomViews.BomDetailView view = service.create(req);

        assertNotNull(view);
        assertEquals(1L, view.id());
        assertEquals(productId, view.productId());
        assertEquals("Bàn gỗ", view.productName());
        assertEquals(1, view.version());
        assertTrue(view.active());
        assertEquals(2, view.items().size());
        assertEquals(new BigDecimal("1.000"), view.items().get(0).quantity());
        assertEquals(new BigDecimal("4.000"), view.items().get(1).quantity());

        verify(bomRepository).deactivateAllByProductId(productId);
        verify(bomItemRepository).saveAllAndFlush(anyList());
    }

    @Test
    void bom02_createSecondBomDeactivatesFirstAndIncrementsVersion() {
        Long productId = 10L;
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);
        Material matChanBan = createMaterial(102L, "CHAN-01", "Chân bàn", "cái", true);

        when(catalogService.requireActiveProduct(productId)).thenReturn(product);
        when(catalogService.requireActiveMaterial(102L)).thenReturn(matChanBan);
        when(bomRepository.findMaxVersionByProductId(productId)).thenReturn(1);

        Bom savedBom = createBom(2L, productId, 2, true);
        when(bomRepository.saveAndFlush(any(Bom.class))).thenReturn(savedBom);
        when(bomRepository.findById(2L)).thenReturn(Optional.of(savedBom));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bomItemRepository.findByBomIdOrderByMaterialIdAsc(2L)).thenReturn(List.of(
            createBomItem(2001L, 2L, 102L, new BigDecimal("4.000"))
        ));
        when(materialRepository.findAllById(any())).thenReturn(List.of(matChanBan));

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(102L, new BigDecimal("4.000"))
        ));

        BomViews.BomDetailView view = service.create(req);

        assertEquals(2, view.version());
        assertTrue(view.active());

        ArgumentCaptor<Bom> bomCaptor = ArgumentCaptor.forClass(Bom.class);
        verify(bomRepository).saveAndFlush(bomCaptor.capture());
        assertEquals(2, bomCaptor.getValue().getVersion());
        assertTrue(bomCaptor.getValue().isActive());
        verify(bomRepository).deactivateAllByProductId(productId);
    }

    @Test
    void bom03_duplicateMaterialInBomThrowsBusinessException() {
        Long productId = 10L;
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);
        when(catalogService.requireActiveProduct(productId)).thenReturn(product);

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(101L, new BigDecimal("1.000")),
            new BomRequests.BomItemRequest(101L, new BigDecimal("2.000"))
        ));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("trùng"));
        verify(bomRepository, never()).saveAndFlush(any());
    }

    @Test
    void bom04_productNotFoundThrowsResourceNotFoundException() {
        Long productId = 999L;
        when(catalogService.requireActiveProduct(productId))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(101L, new BigDecimal("1.000"))
        ));

        assertThrows(ResourceNotFoundException.class, () -> service.create(req));
    }

    @Test
    void bom05_inactiveProductThrowsBusinessException() {
        Long productId = 10L;
        when(catalogService.requireActiveProduct(productId))
                .thenThrow(new BusinessException("Sản phẩm đã bị vô hiệu hóa"));

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(101L, new BigDecimal("1.000"))
        ));

        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    void bom06_inactiveMaterialThrowsBusinessException() {
        Long productId = 10L;
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);
        when(catalogService.requireActiveProduct(productId)).thenReturn(product);
        when(catalogService.requireActiveMaterial(101L))
                .thenThrow(new BusinessException("Nguyên vật liệu đã bị vô hiệu hóa"));

        BomRequests.CreateBomRequest req = new BomRequests.CreateBomRequest(productId, List.of(
            new BomRequests.BomItemRequest(101L, new BigDecimal("1.000"))
        ));

        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    void bom07_activateBomDeactivatesCurrentlyActiveBom() {
        Long bomId = 1L;
        Long productId = 10L;
        Bom bomV1 = createBom(bomId, productId, 1, false);
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);

        when(bomRepository.findByIdForUpdate(bomId)).thenReturn(Optional.of(bomV1));
        when(catalogService.requireActiveProduct(productId)).thenReturn(product);
        when(bomRepository.findById(bomId)).thenReturn(Optional.of(bomV1));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bomItemRepository.findByBomIdOrderByMaterialIdAsc(bomId)).thenReturn(List.of());

        BomViews.BomDetailView result = service.activate(bomId);

        assertTrue(result.active());
        assertTrue(bomV1.isActive());
        verify(bomRepository).deactivateAllByProductId(productId);
        verify(bomRepository).saveAndFlush(bomV1);
    }

    @Test
    void bom08_activateNonExistentBomThrows404() {
        when(bomRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.activate(999L));
    }

    @Test
    void bom09_deactivateBomSetsActiveFalse() {
        Long bomId = 1L;
        Long productId = 10L;
        Bom bom = createBom(bomId, productId, 1, true);
        Product product = createProduct(productId, "Bàn gỗ", "cái", true);

        when(bomRepository.findByIdForUpdate(bomId)).thenReturn(Optional.of(bom));
        when(bomRepository.findById(bomId)).thenReturn(Optional.of(bom));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(bomItemRepository.findByBomIdOrderByMaterialIdAsc(bomId)).thenReturn(List.of());

        BomViews.BomDetailView result = service.deactivate(bomId);

        assertFalse(result.active());
        assertFalse(bom.isActive());
        verify(bomRepository).saveAndFlush(bom);
    }

    @Test
    void bom10_detailNonExistentBomThrows404() {
        when(bomRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.detail(999L));
    }

    @Test
    void bom11_listWithInvalidSortThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () ->
            service.list(null, null, 0, 20, "invalidSortField", "desc")
        );
    }

    @Test
    void bom12_requireActiveBomThrowsIfNoActiveBom() {
        Long productId = 10L;
        when(bomRepository.findActiveByProductId(productId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireActiveBom(productId));
        assertTrue(ex.getMessage().contains("không có BOM đang hoạt động"));
    }

    @Test
    void bom13_requireActiveBomThrowsIfBomHasNoItems() {
        Long productId = 10L;
        Bom activeBom = createBom(1L, productId, 1, true);
        when(bomRepository.findActiveByProductId(productId)).thenReturn(Optional.of(activeBom));
        when(bomItemRepository.existsByBomId(1L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireActiveBom(productId));
        assertTrue(ex.getMessage().contains("chưa có nguyên vật liệu"));
    }
}