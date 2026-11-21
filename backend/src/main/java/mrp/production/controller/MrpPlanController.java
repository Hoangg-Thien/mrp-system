package mrp.production.controller;


import mrp.production.service.MrpCalculator;
import mrp.production.service.ProductionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mrp")
public class MrpPlanController {

    private final ProductionService service;

    public MrpPlanController(ProductionService service) {
        this.service = service;
    }

    @GetMapping("/plan")
    @PreAuthorize("hasRole('WAREHOUSE_MANAGER')")
    public MrpCalculator.PlanResult getPlan(
            @RequestParam Long productId,
            @RequestParam Integer quantity) {
        return service.getPlan(productId, quantity);
    }
}