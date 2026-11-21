package mrp.fgissue.repository;


import mrp.fgissue.entity.FinishedGoodsIssueRequestItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinishedGoodsIssueRequestItemRepository extends JpaRepository<FinishedGoodsIssueRequestItem, Long> {

    List<FinishedGoodsIssueRequestItem> findByRequestId(Long requestId);

    List<FinishedGoodsIssueRequestItem> findByRequestIdIn(Collection<Long> requestIds);

    boolean existsByProductId(Long productId);
}