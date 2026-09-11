# Catalogue cross-service acceptance test

`catalogue-e2e.ps1` runs real packaged Auth, Product, Inventory, and Gateway services against disposable MongoDB (replica set), PostgreSQL, Kafka, and Redis containers. It uses the dev Gateway route definitions from the adjacent `ecommerce-config-repo` and overrides service addresses with isolated local ports. No Compose stack or existing database is used.

From the platform root on Windows, with JDK 21, Maven, and Docker available:

```powershell
mvn -pl product-service,inventory-service,auth-service,gateway-service -am package -DskipTests
./scripts/tests/catalogue-e2e.ps1
```

The script requires locally cached `mongo:7.0`, `postgres:16-alpine`, `apache/kafka:4.3.0`, and `redis:7.2-alpine` images. Image names, workspace, and config repository can be supplied as script parameters. It never automatically pulls an image.

The test performs real registration/login over HTTP. Only fixture accounts in its disposable Auth database are marked verified and assigned seller/admin roles. All business requests then use actual Auth-issued JWTs and the normal Redis token-version checks. Product calls the real secured Auth seller-eligibility endpoint.

Coverage includes anonymous Gateway browse, detail, facets, combined and one-sided price filters, search, sorting, pagination, invalid inputs, image validation, seller ownership, bulk creation, all product lifecycle event types, inventory provisioning and stock preservation, archival and reactivation, snapshot recovery of a missing fixture inventory row, suspended-seller rejection, and fail-closed behavior when the fixture Auth process stops. The explicit fixture-row deletion simulates missed consumer state; it never targets the stocked fixture or external data.

Results and per-service/container logs remain in `target/catalogue-e2e/<run-id>/`. `result.json` records the exact checks and failure, and the script exits nonzero on failure. Cleanup stops only captured child processes and removes only newly created containers whose run label matches, including their anonymous test volumes. No logs or workspace files are deleted.

This is HTTP and messaging end-to-end acceptance coverage across backend services. Frontend render/API contract tests and browser usability verification remain separate checks.
