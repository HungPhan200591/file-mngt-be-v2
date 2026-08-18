package com.filemngt.v2.scan;

import com.filemngt.v2.scan.config.ApprovalOperationProperties;
import com.filemngt.v2.scan.config.CatalogClientProperties;
import com.filemngt.v2.scan.config.OutboxDrainProperties;
import com.filemngt.v2.scan.config.OutboxPressureProperties;
import com.filemngt.v2.scan.config.ScanProperties;
import com.filemngt.v2.scan.config.ScanSseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
    ScanProperties.class,
    ScanSseProperties.class,
    CatalogClientProperties.class,
    ApprovalOperationProperties.class,
    OutboxDrainProperties.class,
    OutboxPressureProperties.class
})
@EnableScheduling
/** Điểm khởi động Scan Service và kích hoạt cấu hình/scheduler thuộc phạm vi service này. */
public class ScanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScanApplication.class, args);
    }
}
