package com.maverick.zaslon.management.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Table(name = "api_routes")
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ApiRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String routeId;
    private String name;
    private String pathPattern;
    private String targetBaseUrl;
    private String targetPathPrefix;
    private Integer requestsPerMinute;
    private Integer responseTimeoutMs;
    private boolean idempotencyEnabled;
    private boolean active;
    private Long configurationVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    //  allowedMethods is a configurable whitelist of HTTP methods for each
//  API route, stored in the separate "route_allowed_methods" database table.
    @ElementCollection(fetch = FetchType.EAGER)//Your ApiRoute entity has its own database table
    @CollectionTable(name = "route_allowed_methods", //@CollectionTable in JPA tells Hibernate which database table should store a collection of values belonging to an entity.
            joinColumns = @JoinColumn(name = "route_pk"))
    @Column(name = "http_method")
    private Set<String> allowedMethods = new HashSet<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RouteHeaderRule> headerRules = new ArrayList<>();

}
