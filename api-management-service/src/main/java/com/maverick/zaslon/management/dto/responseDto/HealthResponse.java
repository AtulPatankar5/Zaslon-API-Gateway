package com.maverick.zaslon.management.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Setter
@Getter
public class HealthResponse {
    private String serviceStatus;
    private String dbStatus;
    private LocalDateTime timeStamp;
}
