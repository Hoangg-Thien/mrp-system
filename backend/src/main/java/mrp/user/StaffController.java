package mrp.user;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffController {
    private final StaffRepository staff;
    public StaffController(StaffRepository staff) { this.staff=staff; }
    @GetMapping("/api/staff")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public List<StaffView> list() { return staff.findActiveStaff(); }
}