package com.filemngt.v2.catalog.masterdata.adapter.in.web;

import com.filemngt.v2.catalog.masterdata.application.MasterDataScanRegistryService;
import com.filemngt.v2.catalog.masterdata.application.dto.RegistrySnapshotView;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint dùng bởi scan-service trước khi tạo scan_run.
 * Không route qua Gateway và không dành cho UI quản trị.
 */
@CrossOrigin(origins = "*")
@Validated
@RestController
@RequestMapping("/api/v2/master-data")
public class MasterDataScanRegistryController {

    private final MasterDataScanRegistryService registryService;

    public MasterDataScanRegistryController(MasterDataScanRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping("/scan-registry")
    public RegistrySnapshotView getRegistry(
            @RequestParam @Pattern(regexp = "JOKE|USE", message = "region must be JOKE or USE") String region) {
        return registryService.snapshot(region);
    }
}
