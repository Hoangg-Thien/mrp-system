package mrp.production;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialIssueRequestItemRepository extends JpaRepository<MaterialIssueRequestItem, Long> {

    List<MaterialIssueRequestItem> findByRequestId(Long requestId);

    List<MaterialIssueRequestItem> findByRequestIdIn(Collection<Long> requestIds);
}
