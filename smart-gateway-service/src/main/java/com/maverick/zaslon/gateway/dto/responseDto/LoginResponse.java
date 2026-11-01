package com.maverick.zaslon.gateway.dto.responseDto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long expiresInSeconds;
    private String role;
    private Long organizationId;
}
