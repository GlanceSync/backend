package com.glancesync.modules.report.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ReportRequest(String reason) {
}
