package mrp.purchase.dto;


import mrp.catalog.dto.CatalogQuery;
import mrp.common.exception.BadRequestException;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Phân trang chuẩn cho phiếu nhập (theo {@code CatalogQuery.page}): {@code page ≥ 0}, {@code size 1..100},
 * {@code sort} theo whitelist, {@code direction} asc/desc. Sort theo {@code id} luôn đi kèm để ổn định thứ tự.
 */
public final class PurchaseQuery {
    private PurchaseQuery() { }
    public static PageRequest page(int page, int size, String sort, String direction, Set<String> allowed) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("page phải >= 0; size từ 1 đến 100");
        if (!allowed.contains(sort)) throw new BadRequestException("Trường sắp xếp không hợp lệ");
        if (!"asc".equalsIgnoreCase(direction) && !"desc".equalsIgnoreCase(direction)) {
            throw new BadRequestException("direction phải là asc hoặc desc");
        }
        Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
        if (!"id".equals(sort)) ordering = ordering.and(Sort.by("id"));
        return PageRequest.of(page, size, ordering);
    }
}