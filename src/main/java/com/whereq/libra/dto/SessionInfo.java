package com.whereq.libra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Spark session information.
 *
 * @author WhereQ Inc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String sessionId;
    private String appId;
    private String state;
    private String master;
    private String sparkVersion;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
}
