package mrp.bom;

import java.util.Set;
import mrp.common.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Phân trang chuẩn cho BOM: {@code page >= 0}, {@code size 1..100},
 * {@code sort} theo whitelist, {@code direction} asc/desc. Sort theo {@code id} luôn đi kèm để ổn định thứ tự.
 */
final class BomQuery {
    static final Set<String> ALLOWED_SORTS = Set.of("id", "version", "createdAt", "productId");

    private BomQuery() { }

    static PageRequest page(int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page phải >= 0; size từ 1 đến 100");
        }
        if (!ALLOWED_SORTS.contains(sort)) {
            throw new BadRequestException("Trường sắp xếp không hợp lệ");
        }
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("direction phải là asc hoặc desc");
        }
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!"id".equals(sort)) {
            ordering = ordering.and(Sort.by(Sort.Direction.fromString(direction), "id"));
        }
        return PageRequest.of(page, size, ordering);
    }
}
