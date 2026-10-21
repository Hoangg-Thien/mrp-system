package mrp.catalog.service;


import mrp.catalog.dto.CatalogQuery;
import mrp.catalog.dto.CatalogRequests;
import mrp.catalog.entity.Category;
import mrp.catalog.entity.Material;
import mrp.catalog.entity.Product;
import mrp.catalog.entity.Supplier;
import mrp.catalog.repository.CategoryRepository;
import mrp.catalog.repository.MaterialRepository;
import mrp.catalog.repository.ProductRepository;
import mrp.catalog.repository.SupplierRepository;
import mrp.common.BadRequestException;
import mrp.common.BusinessException;
import mrp.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static mrp.catalog.dto.CatalogRequests.*;
import static mrp.catalog.dto.CatalogQuery.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final CatalogReferenceChecker references;
    public CatalogService(CategoryRepository categories, ProductRepository products, SupplierRepository suppliers,
                          MaterialRepository materials, CatalogReferenceChecker references) {
        this.categories=categories; this.products=products; this.suppliers=suppliers; this.materials=materials; this.references=references;
    }
    public Page<Category> categories(String keyword, int p, int size, String sort, String direction) {
        return categories.findAll(text(keyword, "name"), page(p, size, sort, direction, Set.of("id", "name")));
    }
    public Page<Supplier> suppliers(String keyword, String name, String phone, String email, int p, int size, String sort, String direction) {
        Specification<Supplier> spec = CatalogQuery.<Supplier>text(keyword, "name", "phone", "email")
                .and(text(name, "name")).and(text(phone, "phone")).and(text(email, "email"));
        return suppliers.findAll(spec, page(p, size, sort, direction, Set.of("id", "name", "phone", "email")));
    }
    public Page<Material> materials(String keyword, Boolean active, int p, int size, String sort, String direction) {
        Specification<Material> spec = CatalogQuery.<Material>text(keyword, "code", "name").and(equal("active", active));
        return materials.findAll(spec, page(p, size, sort, direction, Set.of("id", "code", "name", "standardCost", "createdAt")));
    }
    public Page<Product> products(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                                  Boolean active, int p, int size, String sort, String direction) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new BadRequestException("Khoảng giá không hợp lệ");
        }
        Specification<Product> spec = CatalogQuery.<Product>text(keyword, "name", "description")
                .and(equal("categoryId", categoryId)).and(equal("active", active))
                .and((root, query, cb) -> minPrice == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("sellingPrice"), minPrice))
                .and((root, query, cb) -> maxPrice == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("sellingPrice"), maxPrice));
        return products.findAll(spec, page(p, size, sort, direction, Set.of("id", "name", "sellingPrice", "createdAt")));
    }
    public Category category(Long id) { return categories.findById(id).orElseThrow(() -> missing("danh mục")); }
    public Supplier supplier(Long id) { return suppliers.findById(id).orElseThrow(() -> missing("nhà cung cấp")); }
    public Product product(Long id) { return products.findById(id).orElseThrow(() -> missing("sản phẩm")); }
    public Material material(Long id) { return materials.findById(id).orElseThrow(() -> missing("nguyên vật liệu")); }
    public Product requireActiveProduct(Long id) {
        Product product=product(id);
        if (!product.isActive()) throw new BusinessException("Sản phẩm đã bị vô hiệu hóa");
        return product;
    }
    public Material requireActiveMaterial(Long id) {
        Material material=material(id);
        if (!material.isActive()) throw new BusinessException("Nguyên vật liệu đã bị vô hiệu hóa");
        return material;
    }
    @Transactional public Category createCategory(CategoryRequest r) { return saveCategory(new Category(), r); }
    @Transactional public Category updateCategory(Long id, CategoryRequest r) { return saveCategory(category(id), r); }
    private Category saveCategory(Category c, CategoryRequest r) {
        if (categories.existsByNameAndIdNot(r.name().trim(), c.getId() == null ? -1L : c.getId())) throw new BusinessException("Tên danh mục đã tồn tại");
        c.update(r); return categories.saveAndFlush(c);
    }
    @Transactional public Supplier createSupplier(SupplierRequest r) { Supplier s=new Supplier(); s.update(r); return suppliers.saveAndFlush(s); }
    @Transactional public Supplier updateSupplier(Long id, SupplierRequest r) { Supplier s=supplier(id); s.update(r); return suppliers.saveAndFlush(s); }
    @Transactional public Product createProduct(ProductRequest r) { category(r.categoryId()); Product p=new Product(); p.update(r); return products.saveAndFlush(p); }
    @Transactional public Product updateProduct(Long id, ProductRequest r) { Product p=product(id); category(r.categoryId()); p.update(r); return products.saveAndFlush(p); }
    @Transactional public Material createMaterial(MaterialRequest r) { return saveMaterial(new Material(), r); }
    @Transactional public Material updateMaterial(Long id, MaterialRequest r) {
        Material m=materials.findByIdForUpdate(id).orElseThrow(() -> missing("nguyên vật liệu"));
        if (!m.getUnit().equals(r.unit().trim()) && references.materialUnitLocked(id)) {
            throw new BusinessException("Không thể đổi đơn vị NVL đã có sổ kho hoặc BOM");
        }
        return saveMaterial(m, r);
    }
    private Material saveMaterial(Material m, MaterialRequest r) {
        if (materials.existsByCodeAndIdNot(r.code().trim(), m.getId() == null ? -1L : m.getId())) throw new BusinessException("Mã nguyên vật liệu đã tồn tại");
        m.update(r); return materials.saveAndFlush(m);
    }
    @Transactional public Product productActive(Long id, boolean active) { Product p=product(id); p.setActive(active); return products.saveAndFlush(p); }
    @Transactional public Material materialActive(Long id, boolean active) {
        Material m=materials.findByIdForUpdate(id).orElseThrow(() -> missing("nguyên vật liệu"));
        m.setActive(active); return materials.saveAndFlush(m);
    }
    @Transactional public void deleteCategory(Long id) {
        Category c=category(id); if (references.category(id)) throw new BusinessException("Danh mục còn sản phẩm, không thể xóa");
        categories.delete(c); categories.flush();
    }
    @Transactional public void deleteSupplier(Long id) {
        Supplier s=supplier(id); if (references.supplier(id)) throw new BusinessException("Nhà cung cấp đã có phiếu nhập, không thể xóa");
        suppliers.delete(s); suppliers.flush();
    }
    @Transactional public void deleteProduct(Long id) {
        Product p=product(id); if (references.product(id)) throw new BusinessException("Sản phẩm đã được sử dụng; hãy vô hiệu hóa thay vì xóa");
        products.delete(p); products.flush();
    }
    @Transactional public void deleteMaterial(Long id) {
        Material m=materials.findByIdForUpdate(id).orElseThrow(() -> missing("nguyên vật liệu"));
        if (references.material(id)) throw new BusinessException("Nguyên vật liệu đã được sử dụng; hãy vô hiệu hóa thay vì xóa");
        materials.delete(m); materials.flush();
    }
    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException("Không tìm thấy " + resource); }
}