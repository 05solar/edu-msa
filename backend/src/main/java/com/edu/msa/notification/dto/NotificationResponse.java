package com.edu.msa.notification.dto;

import com.edu.msa.common.NotiKind;

public record NotificationResponse(
        Long id, String to, NotiKind kind, String title, String sub, boolean read, Long pid
) {}
