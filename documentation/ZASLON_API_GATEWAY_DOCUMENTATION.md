# Zaslon API Gateway — Architecture, API Catalog & Propagation Flow

> **Version:** 1.0.0  
> **Target Audience:** Engineering, DevOps, System Architects & Product Teams  
> **Platform Services:** `api-management-service` (8081), `smart-gateway-service` (8080), `analytics-service` (8082)

---

## 1. Executive Summary & Architecture Overview

**Zaslon API Gateway** (Gatekeeper) is an enterprise-ready, multi-tenant API Gateway platform built on **Spring Boot 3.x** and **Java 21**. It decouples system administration into three specialized planes:

```
+----------------------------------------------------------------------------------------------------+
|                                      ZASLON API GATEWAY ECOSYSTEM                                  |
+----------------------------------------------------------------------------------------------------+
                                                  |
       +------------------------------------------+------------------------------------------+
       |                                          |                                          |
       v                                          v                                          v
+-------------------------------+  +-------------------------------+  +-------------------------------+
|         CONTROL PLANE         |  |          DATA PLANE           |  |       OBSERVATION PLANE       |
|    (api-management-service)   |  |     (smart-gateway-service)   |  |      (analytics-service)      |
|           Port: 8081          |  |           Port: 8080          |  |           Port: 8082          |
+-------------------------------+  +-------------------------------+  +-------------------------------+
| - Tenant & Org Provisioning   |  | - Reverse Proxy & Routing     |  | - Request Telemetry & Events  |
| - Route Lifecycle & Policies  |  | - Tenant Slug Path Matching   |  | - Latency & Status Reporting  |
| - Rate Limiting & Timeouts    |  | - Rate Limiter (RPM)          |  | - Metric Summarization        |
| - Header Mutation Rules       |  | - Response Timeout Handling   |  |                               |
| - Route Version Snapshotting  |  | - Header Mutation Engine      |  |                               |
| - Control Plane Audit Logging |  | - Idempotency Validation      |  |                               |
| - Built-in Admin Web Console  |  | - Dynamic Config Polling      |  |                               |
+-------------------------------+  +-------------------------------+  +-------------------------------+
```

### Core Architecture Principles
1. **Strict Multi-Tenant Isolation:**  
   Every route's public path pattern is strictly prefixed by the tenant's slug (`/{tenant-slug}/**`). Tenants cannot view, alter, or impact routes or audit trails belonging to other tenants.
2. **Configuration Versioning & Snapshots:**  
   Every route creation, modification, status flip, or deletion creates an immutable snapshot stored in `route_configuration_versions` with an auto-incrementing version number.
3. **Comprehensive Control Plane Auditability:**  
   Every state-altering API call records an entry into `control_plane_audit_records` capturing the actor, action type, resource ID, correlation ID, execution duration (ms), and outcome.
4. **Stateless JWT Security & RBAC:**  
   Standard HMAC-SHA256 JWT tokens with role separation: `PLATFORM_ADMIN`, `TENANT_ADMIN`, and `GATEWAY_SERVICE`.

---

## 2. Authentication & Role-Based Access Control (RBAC)

Authentication is handled via JWT tokens issued by `api-management-service`.

| Role | Scope & Permissions | Key Operations |
|---|---|---|
| `PLATFORM_ADMIN` | Global system control | Create & manage Organizations, update status, provision & manage Tenant Administrators. |
| `TENANT_ADMIN` | Organization-scoped control | Manage own Organization name, Create/Update/Delete Routes, inspect Route Versions, inspect Tenant Audit Records. |
| `GATEWAY_SERVICE` | Machine-to-machine control | Internal service token allowing the Data Plane (`smart-gateway-service`) to pull all active, validated routes. |

### JWT Claims Payload
```json
{
  "iss": "zaslon-api-management",
  "sub": "foodfast-admin",
  "role": "TENANT_ADMIN",
  "organizationId": 2,
  "iat": 1729864200,
  "exp": 1729867800
}
```

---

## 3. Complete API Specifications

### 3.1. Authentication APIs (`/api/auth`)

#### `POST /api/auth/login`
- **Access:** Public
- **Description:** Authenticates user credentials and returns a Bearer JWT token.
- **Request Body:**
  ```json
  {
    "username": "platform-admin",
    "password": "platform123"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "role": "PLATFORM_ADMIN",
    "organizationId": null
  }
  ```

---

### 3.2. Platform Administration APIs (`/api/platform/organizations`)
*Requires `ROLE_PLATFORM_ADMIN`*

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/platform/organizations` | Creates a new organization in `PENDING` status. |
| `GET` | `/api/platform/organizations` | Returns all active/pending/suspended organizations (excluding soft-deleted). |
| `GET` | `/api/platform/organizations/{id}` | Returns organization details by ID. |
| `PUT` | `/api/platform/organizations/{id}` | Updates organization `name` and `slug` (enforces uniqueness). |
| `PATCH` | `/api/platform/organizations/{id}/status` | Updates organization status (`ACTIVE`, `PENDING`, `SUSPENDED`). |
| `DELETE`| `/api/platform/organizations/{id}` | Soft-deletes organization (`status = DELETED`, sets `deletedAt`). |
| `POST` | `/api/platform/organizations/{id}/tenant-admin` | Provisions the administrator account for the organization. |
| `GET` | `/api/platform/organizations/{id}/tenant-admin` | Fetches tenant admin user details. |
| `PUT` | `/api/platform/organizations/{id}/tenant-admin` | Updates tenant admin username, password, or enabled flag. |
| `DELETE`| `/api/platform/organizations/{id}/tenant-admin` | Deletes tenant admin user account. |

#### Example: Create Organization (`POST /api/platform/organizations`)
```json
{
  "name": "FoodFast Delivery",
  "slug": "foodfast"
}
```
**Response (201 Created):**
```json
{
  "id": 1,
  "name": "FoodFast Delivery",
  "slug": "foodfast",
  "status": "PENDING",
  "createdAt": "2026-10-30T10:00:00",
  "updatedAt": "2026-10-30T10:00:00"
}
```

#### Example: Provision Tenant Admin (`POST /api/platform/organizations/1/tenant-admin`)
```json
{
  "username": "foodfast-admin",
  "password": "foodfast123",
  "enabled": true
}
```

---

### 3.3. Tenant Organization APIs (`/api/tenant/organization`)
*Requires `ROLE_TENANT_ADMIN`*

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/tenant/organization` | Returns profile details for the authenticated tenant's organization. |
| `PUT` | `/api/tenant/organization` | Allows tenant admin to update their organization's display name. |

---

### 3.4. Tenant Route Management APIs (`/api/tenant`)
*Requires `ROLE_TENANT_ADMIN`*

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/tenant/routes` | Creates a new route with routing rules, policies, and header filters. |
| `GET` | `/api/tenant/routes` | Lists all routes belonging to the authenticated tenant. |
| `GET` | `/api/tenant/routes/{id}` | Retrieves a route by database ID (tenant boundary enforced). |
| `PUT` | `/api/tenant/routes/{id}` | Updates route configuration and increments configuration version. |
| `PATCH` | `/api/tenant/routes/{id}/status` | Activates (`active: true`) or deactivates (`active: false`) a route. |
| `DELETE`| `/api/tenant/routes/{id}` | Deletes route; records final `DELETED` snapshot. |
| `GET` | `/api/tenant/route-versions?routeId={routeId}` | Returns version audit history and JSON configuration snapshots. |
| `GET` | `/api/tenant/audit-records` | Returns chronological control-plane audit events for the tenant. |

#### Example: Create Route (`POST /api/tenant/routes`)
```json
{
  "routeId": "orders-api",
  "name": "Orders Service Routing",
  "pathPattern": "/foodfast/orders/**",
  "targetBaseUrl": "http://orders-backend.internal:8090",
  "targetPathPrefix": "/api/v1/orders",
  "requestsPerMinute": 120,
  "responseTimeoutMs": 3000,
  "idempotencyEnabled": true,
  "active": true,
  "allowedMethods": ["GET", "POST", "PUT"],
  "headerRules": [
    {
      "direction": "REQUEST",
      "action": "ADD",
      "headerName": "X-Forwarded-Tenant",
      "headerValue": "foodfast"
    },
    {
      "direction": "RESPONSE",
      "action": "REMOVE",
      "headerName": "X-Powered-By",
      "headerValue": null
    }
  ]
}
```

**Key Validations Enforced:**
1. `pathPattern` must begin with the tenant slug prefix (e.g. `/foodfast/`).
2. `targetPathPrefix` must start with `/`.
3. `targetBaseUrl` must be a valid `http` or `https` URI.
4. `allowedMethods` are validated against valid HTTP methods (`GET`, `POST`, `PUT`, `DELETE`, etc.).
5. `headerRules`:
   - `direction`: Must be `REQUEST` or `RESPONSE`.
   - `action`: Must be `ADD` or `REMOVE`.
   - `headerValue`: Required when action is `ADD`.

---

### 3.5. Gateway Configuration Synchronization API (`/api/gateway`)
*Requires `ROLE_GATEWAY_SERVICE`*

#### `GET /api/gateway`
- **Description:** Polled or triggered by `smart-gateway-service` to retrieve all active routes across active organizations.
- **Filtering Logic:** `findAllByActiveTrueAndOrganizationStatus(OrganizationStatus.ACTIVE)`
- **Response (200 OK):**
  ```json
  [
    {
      "id": 1,
      "organizationId": 2,
      "routeId": "orders-api",
      "name": "Orders Service Routing",
      "pathPattern": "/foodfast/orders/**",
      "targetBaseUrl": "http://orders-backend.internal:8090",
      "targetPathPrefix": "/api/v1/orders",
      "requestsPerMinute": 120,
      "responseTimeoutMs": 3000,
      "idempotencyEnabled": true,
      "active": true,
      "configurationVersion": 1,
      "allowedMethods": ["GET", "POST", "PUT"],
      "headerRules": [ ... ]
    }
  ]
  ```

---

### 3.6. Service Health Check APIs (`/health`)
*Publicly accessible on all three microservices*

| Service | Port | Endpoint | Sample Response |
|---|---|---|---|
| `api-management-service` | 8081 | `GET /health` | `{"serviceName":"API Management Service","status":"UP"}` |
| `smart-gateway-service` | 8080 | `GET /health` | `{"serviceName":"Smart Gateway Service","status":"UP"}` |
| `analytics-service` | 8082 | `GET /health` | `{"serviceName":"Analytics Service","status":"UP"}` |

---

## 4. End-to-End API Flow & Propagation

### 4.1. Tenant Provisioning & Setup Propagation
```
[ Platform Admin ]
       |
       | 1. POST /api/auth/login
       v
[ api-management-service ] ---> Issues JWT (role: PLATFORM_ADMIN)
       |
       | 2. POST /api/platform/organizations ("FoodFast", slug: "foodfast")
       v
[ api-management-service ] ---> Inserts into `organizations` (status: PENDING)
                               Creates Audit Record ("CREATE_ORGANIZATION")
       |
       | 3. POST /api/platform/organizations/{id}/tenant-admin
       v
[ api-management-service ] ---> BCrypt hashes password, inserts into `management_users`
                               Creates Audit Record ("CREATE_TENANT_ADMIN")
       |
       | 4. PATCH /api/platform/organizations/{id}/status (status: ACTIVE)
       v
[ api-management-service ] ---> Sets organization to ACTIVE
                               Tenant is now eligible for Route Execution!
```

---

### 4.2. Route Lifecycle & Version Snapshot Propagation
```
[ Tenant Admin ]
       |
       | 1. POST /api/auth/login (username: "foodfast-admin")
       v
[ api-management-service ] ---> Validates credentials & checks Org status == ACTIVE
                               Issues JWT (role: TENANT_ADMIN, organizationId: 2)
       |
       | 2. POST /api/tenant/routes
       v
[ TenantContext ] ------------> Resolves organizationId from JWT & checks Organization status
[ Validation Engine ] --------> Checks pathPattern begins with "/foodfast/"
                               Validates targetBaseUrl, allowedMethods, headerRules
       |
       +---> Inserts into `api_routes` (version: 1)
       +---> Inserts into `route_allowed_methods` & `route_header_rules`
       +---> Serializes route to JSON snapshot & saves to `route_configuration_versions`
       +---> Appends entry in `control_plane_audit_records` (actor: "foodfast-admin", durationMs: 12)
```

---

### 4.3. Gateway Synchronization & Data Plane Propagation
```
[ smart-gateway-service ]                         [ api-management-service ]
       |                                                      |
       | 1. Authenticate with GATEWAY_SERVICE credentials    |
       |----------------------------------------------------->|
       |<-----------------------------------------------------|
       |    JWT Token (role: GATEWAY_SERVICE)                 |
       |                                                      |
       | 2. GET /api/gateway                                  |
       |----------------------------------------------------->|
       |<-----------------------------------------------------|
       |    JSON Array of all ACTIVE routes for ACTIVE Orgs   |
       |                                                      |
       v                                                      |
[ In-Memory Gateway Cache ]                                   |
- Path Matcher Tree: /foodfast/orders/**                      |
- Target: http://orders-backend.internal:8090/api/v1/orders    |
- Rate Limit: 120 RPM                                         |
- Timeout: 3000 ms                                            |
- Header Rules: +X-Forwarded-Tenant, -X-Powered-By            |
```

---

### 4.4. Live Client Request Propagation Flow
```
[ Client Application ]
       |
       | 1. HTTP GET http://gateway:8080/foodfast/orders/1234
       v
+-------------------------------------------------------------+
|                 smart-gateway-service (8080)                |
|                                                             |
|  Step A: Path Matching                                      |
|          Matches /foodfast/orders/** -> Route: "orders-api" |
|                                                             |
|  Step B: Method Enforcement                                 |
|          Verifies "GET" is in allowedMethods                |
|                                                             |
|  Step C: Rate Limiting & Throttling                         |
|          Checks RPM counter for tenant/route                |
|                                                             |
|  Step D: Request Transformation                             |
|          Rewrites path: /api/v1/orders/1234                 |
|          Injects Header: X-Forwarded-Tenant: foodfast       |
|                                                             |
|  Step E: Upstream Forwarding (with 3000ms timeout)          |
+-------------------------------------------------------------+
       |
       | 2. Forwarded Request
       v
[ Backend Microservice: Orders Service ]
       |
       | 3. Response: 200 OK + Payload
       v
+-------------------------------------------------------------+
|                 smart-gateway-service (8080)                |
|                                                             |
|  Step F: Response Transformation                            |
|          Strips Header: X-Powered-By                        |
|                                                             |
|  Step G: Telemetry & Asynchronous Event Emit                |
+-------------------------------------------------------------+
       |                                      \
       | 4. Final Response to Client           \ Asynchronous Event Log
       v                                        v
[ Client Application ]                +---------------------------+
                                      | analytics-service (8082)  |
                                      | - Latency: 48ms           |
                                      | - Status: 200             |
                                      | - Route: orders-api       |
                                      | - Tenant: foodfast        |
                                      +---------------------------+
```

---

## 5. Database Schema & Data Modeling

The management plane persistence layer is designed with clean constraints, foreign keys, cascading deletions, and audit retention:

```
+----------------------------------+            +----------------------------------+
|          organizations           | 1        * |         management_users         |
+----------------------------------+------------+----------------------------------+
| id: BIGINT PK                    |            | id: BIGINT PK                    |
| name: VARCHAR(150) UNIQUE        |            | organization_id: BIGINT FK (NULL)|
| slug: VARCHAR(100) UNIQUE        |            | role_id: BIGINT FK               |
| status: VARCHAR(20)              |            | username: VARCHAR(100) UNIQUE    |
| created_at, updated_at           |            | password_hash: VARCHAR(255)      |
| deleted_at: DATETIME (Soft-del)  |            | enabled: BOOLEAN                 |
+----------------------------------+            +----------------------------------+
          | 1
          |
          | *
+----------------------------------+            +----------------------------------+
|            api_routes            | 1        * |      route_allowed_methods       |
+----------------------------------+------------+----------------------------------+
| id: BIGINT PK                    |            | route_pk: BIGINT FK PK           |
| organization_id: BIGINT FK       |            | http_method: VARCHAR(10) PK      |
| route_id: VARCHAR(100)           |            +----------------------------------+
| name: VARCHAR(200)               |
| path_pattern: VARCHAR(500)       |            +----------------------------------+
| target_base_url: VARCHAR(1000)   | 1        * |        route_header_rules        |
| target_path_prefix: VARCHAR(500) |------------+----------------------------------+
| requests_per_minute: INT         |            | id: BIGINT PK                    |
| response_timeout_ms: INT         |            | route_pk: BIGINT FK              |
| idempotency_enabled: BOOLEAN     |            | direction: VARCHAR(20)           |
| active: BOOLEAN                  |            | action: VARCHAR(20)              |
| configuration_version: BIGINT    |            | header_name: VARCHAR(200)        |
+----------------------------------+            | header_value: VARCHAR(2000)      |
          | 1                                   +----------------------------------+
          |
          | *
+-------------------------------------------+   +-------------------------------------------+
|        route_configuration_versions       |   |       control_plane_audit_records         |
+-------------------------------------------+   +-------------------------------------------+
| id: BIGINT PK                             |   | id: BIGINT PK                             |
| organization_id: BIGINT FK                |   | organization_id: BIGINT FK (NULL)         |
| route_id: VARCHAR(100)                    |   | actor: VARCHAR(100)                       |
| version_number: BIGINT                    |   | action: VARCHAR(100)                      |
| change_type: VARCHAR(30)                  |   | resource_type: VARCHAR(100)               |
| configuration_snapshot: LONGTEXT (JSON)   |   | resource_id: VARCHAR(100)                 |
| created_at: DATETIME                      |   | correlation_id: VARCHAR(100)              |
+-------------------------------------------+   | duration_ms: BIGINT                       |
                                                | result: VARCHAR(20)                       |
                                                | details: VARCHAR(2000)                    |
                                                | created_at: DATETIME                      |
                                                +-------------------------------------------+
```

---

## 6. Summary of Key Configuration Properties

| Service | File | Key Properties |
|---|---|---|
| `api-management-service` | `application.properties` | `server.port=8081`<br>`spring.datasource.url=jdbc:mysql://localhost:3306/gatekeeper_management`<br>`spring.flyway.enabled=true`<br>`security.jwt.expiration-seconds=3600`<br>`security.jwt.secret=${JWT_SECRET}` |
| `smart-gateway-service` | `application.properties` | `server.port=8080`<br>`info.app.name=Smart Gateway Service` |
| `analytics-service` | `application.properties` | `server.port=8082`<br>`info.app.name=Analytics Service` |

---
*Generated for Zaslon API Gateway Engineering Documentation.*

