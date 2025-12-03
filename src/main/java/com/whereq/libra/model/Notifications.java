package com.whereq.libra.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Webhook notification configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notifications implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Webhook URL to notify
     */
    private String webhook;

    /**
     * Job events to trigger notifications
     */
    private List<JobStatus> events;

    /**
     * Check if notifications are enabled
     */
    public boolean isEnabled() {
        return webhook != null && !webhook.isEmpty() && events != null && !events.isEmpty();
    }

    /**
     * Check if should notify for given status
     */
    public boolean shouldNotify(JobStatus status) {
        return isEnabled() && events.contains(status);
    }
}
