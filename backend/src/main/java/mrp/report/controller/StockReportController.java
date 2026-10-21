package mrp.report.controller;


import mrp.report.dto.StockReportItemView;
import mrp.report.service.StockReportService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
public class StockReportController {

    private final StockReportService service;

    public StockReportController(StockReportService service) {
        this.service = service;
    }

    @GetMapping("/stock")
    public List<StockReportItemView> getStockReport(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer period) {
        return service.getStockReport(kind, granularity, year, period);
    }
}