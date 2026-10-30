package com.maverick.zaslon.management.dto.requestDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantAdminRequest {
    @NotBlank
    @Size(min = 1, max = 100)
    private String username;

    @Size(max = 100)
    private String password;

    private boolean enabled = true;
}
