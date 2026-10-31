    package com.maverick.zaslon.management.service;

    import com.maverick.zaslon.management.entity.ManagementUser;
    import com.maverick.zaslon.management.entity.OrganizationStatus;
    import com.maverick.zaslon.management.Exception.ApiException;
    import com.maverick.zaslon.management.dto.requestDto.LoginRequest;
    import com.maverick.zaslon.management.dto.responseDto.LoginResponse;
    import com.maverick.zaslon.management.repository.ManagementUserRepository;
    import jakarta.validation.Valid;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.http.HttpStatus;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
    import org.springframework.security.oauth2.jwt.JwsHeader;
    import org.springframework.security.oauth2.jwt.JwtClaimsSet;
    import org.springframework.security.oauth2.jwt.JwtEncoder;
    import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.time.Instant;

    @Service
    public class AuthenticationService {

        private final ManagementUserRepository managementUserRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtEncoder jwtEncoder;
        private final long expirationSeconds;

        public AuthenticationService(ManagementUserRepository managementUserRepository,
                                     PasswordEncoder passwordEncoder,
                                     JwtEncoder jwtEncoder,
                                     @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
            this.managementUserRepository = managementUserRepository;
            this.passwordEncoder = passwordEncoder;
            this.jwtEncoder = jwtEncoder;
            this.expirationSeconds = expirationSeconds;
        }

        @Transactional(readOnly = true)
        public LoginResponse login(@Valid LoginRequest loginRequest) {
            ManagementUser user = managementUserRepository.findByUsername(loginRequest.getUsername()).
                    orElseThrow(() -> invalidCredentials());

            if (!user.isEnabled() || !passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
                throw invalidCredentials();
            }
            if (user.getOrganization() != null && user.getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Organization is not active");
            }

            Instant issuedAt = Instant.now();
            String role = user.getRole().getName();
            Long organizationId = user.getOrganization() == null ? null : user.getOrganization().getId();

            JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                    .issuer("zaslon-api-management")
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plusSeconds(expirationSeconds))
                    .subject(user.getUsername())
                    .claim("role", role);

            if (organizationId != null) {
                claims.claim("organizationId", organizationId);
            }

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            String token = jwtEncoder.encode(
                    JwtEncoderParameters.from(header, claims.build())).getTokenValue();

            return new LoginResponse(token, "Bearer", expirationSeconds, role, organizationId);
        }


        private RuntimeException invalidCredentials() {
            return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }
