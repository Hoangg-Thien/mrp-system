package mrp.fgissue;

import java.util.Set;
import mrp.common.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

final class FgIssueQuery {
    static final Set<String> ALLOWED_SORTS = Set.of("id", "requestedAt", "status", "requestedBy");

    private FgIssueQuery() { }

    static PageRequest page(int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page phải >= 0; size từ 1 đến 100");
        }
        if (!ALLOWED_SORTS.contains(sort)) {
            throw new BadRequestException("Trường sắp xếp không hợp lệ: " + sort);
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
