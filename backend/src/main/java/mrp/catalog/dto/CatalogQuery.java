package mrp.catalog.dto;


import mrp.common.BadRequestException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public final class CatalogQuery {
    private CatalogQuery() { }
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
    public static <T> Specification<T> text(String keyword, String... fields) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return cb.conjunction();
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(Arrays.stream(fields).map(field -> cb.like(cb.lower(root.get(field)), pattern, '\\'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    public static <T> Specification<T> equal(String field, Object value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
    }
}