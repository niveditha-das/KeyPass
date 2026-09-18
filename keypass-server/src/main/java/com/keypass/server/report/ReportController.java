package com.keypass.server.report;

import com.keypass.server.common.CurrentUser;
import com.keypass.server.common.NotFoundException;
import com.keypass.server.vehicle.Vehicle;
import com.keypass.server.vehicle.VehicleRepository;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles/{vin}/reports")
public class ReportController {

    private final VehicleRepository vehicles;
    private final ReportService reportService;

    public ReportController(VehicleRepository vehicles, ReportService reportService) {
        this.vehicles = vehicles;
        this.reportService = reportService;
    }

    @GetMapping("/usage")
    public List<UsageReportRow> usage(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String vin,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        Vehicle vehicle = ownedVehicle(jwt, vin);
        var start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return reportService.usageReport(vehicle.getId(), start, end);
    }

    @GetMapping("/denial-reasons")
    public List<DenialReasonRow> denialReasons(@AuthenticationPrincipal Jwt jwt, @PathVariable String vin) {
        Vehicle vehicle = ownedVehicle(jwt, vin);
        return reportService.denialReasons(vehicle.getId());
    }

    private Vehicle ownedVehicle(Jwt jwt, String vin) {
        UUID userId = CurrentUser.idOf(jwt);
        return vehicles.findByVin(vin)
                .filter(v -> v.getOwnerId().equals(userId))
                .orElseThrow(NotFoundException::new);
    }
}
