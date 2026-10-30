package com.maverick.zaslon.management.controller;

import com.maverick.zaslon.management.dto.requestDto.RouteRequest;
import com.maverick.zaslon.management.dto.requestDto.RouteStatusRequest;
import com.maverick.zaslon.management.dto.responseDto.AuditRecordResponse;
import com.maverick.zaslon.management.dto.responseDto.RouteResponse;
import com.maverick.zaslon.management.dto.responseDto.RouteVersionResponse;
import com.maverick.zaslon.management.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant")
public class TenantRouteController {

    private final RouteService routeService;

    public TenantRouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    //CRUD for routes
//    Create
    @PostMapping("/routes")
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody RouteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(request));
    }

    //   get all
    @GetMapping("/routes")
    public ResponseEntity<List<RouteResponse>> findAllRoutes() {
        return ResponseEntity.ok(routeService.findAllTenant());
    }

    //    Get
    @GetMapping("/routes/{id}")
    public ResponseEntity<RouteResponse> findRoutesById(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.findById(id));
    }

    //    Put
    @PutMapping("/routes/{id}")
    public ResponseEntity<RouteResponse> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteRequest request) {
        return ResponseEntity.ok(routeService.update(id, request));
    }

    //    Patch
    @PatchMapping("/routes/{id}/status")
    public ResponseEntity<RouteResponse> changeRouteStatus(@PathVariable Long id,
                                                      @Valid @RequestBody RouteStatusRequest request) {
        return ResponseEntity.ok(routeService.changeStatus(id, request));
    }

    //    Delete
    @DeleteMapping("/routes/{id}")
    public ResponseEntity<RouteResponse> deleteRoute(@PathVariable Long id) {
        routeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/route-versions")
    public ResponseEntity<List<RouteVersionResponse>> getRouteVersions(@RequestParam String routeId) {
        return ResponseEntity.ok(routeService.getVersions(routeId));
    }

    @GetMapping("/audit-records")
    public ResponseEntity<List<AuditRecordResponse>> getAuditRecords(){
        return ResponseEntity.ok(routeService.getAuditRecords());
    }
}
