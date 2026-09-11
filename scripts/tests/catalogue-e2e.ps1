param(
    [string]$Workspace = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
    [string]$ConfigRepository = (Join-Path (Split-Path $Workspace -Parent) 'ecommerce-config-repo'),
    [string]$MongoImage = 'mongo:7.0',
    [string]$PostgresImage = 'postgres:16-alpine',
    [string]$KafkaImage = 'apache/kafka:4.3.0',
    [string]$RedisImage = 'redis:7.2-alpine'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http
$runId = [Guid]::NewGuid().ToString('N')
$runDirectory = Join-Path $Workspace ('target/catalogue-e2e/' + $runId)
$null = New-Item -ItemType Directory -Path $runDirectory -Force
$containers = [System.Collections.Generic.List[string]]::new()
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$checks = [System.Collections.Generic.List[string]]::new()
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(12)
$testPassword = 'Catalogue!E2e1234'
$serviceToken = 'catalogue-e2e-service-' + $runId
$databasePassword = 'catalogue_e2e_fixture'
$failure = $null

function Invoke-Docker([string[]]$Arguments) {
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Docker command failed: ' + ($output -join "`n")) }
    return ($output -join "`n").Trim()
}

function Free-Port {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}

function Start-Container([string]$Kind, [string[]]$Arguments) {
    $name = 'pepekart-catalogue-e2e-' + $runId + '-' + $Kind
    $id = Invoke-Docker (@('run', '--detach', '--pull', 'never', '--name', $name,
            '--label', ('com.pepekart.catalogue-e2e.run=' + $runId)) + $Arguments)
    $containers.Add($id)
    return $id
}

function Wait-Until([string]$Description, [scriptblock]$Condition, [int]$Seconds = 90) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    $lastError = ''
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if (& $Condition) { return } } catch { $lastError = $_.Exception.Message }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Description. $lastError"
}

function Start-ServiceJar([string]$Name, [int]$Port, [string[]]$Properties) {
    $jar = Join-Path $Workspace ($Name + '/target/' + $Name + '-1.0.0.jar')
    if (!(Test-Path -LiteralPath $jar)) { throw "Package $Name before running this test: $jar" }
    $arguments = @('-Xms64m', '-Xmx384m', '-jar', $jar,
        "--server.port=$Port", '--server.address=127.0.0.1', '--spring.profiles.active=test', '--spring.cloud.config.enabled=false',
        '--spring.config.import=', '--management.endpoint.health.probes.enabled=true',
        '--management.tracing.sampling.probability=0', '--logging.level.root=WARN',
        '--logging.level.org.apache.kafka=ERROR', '--logging.level.org.springframework.kafka=ERROR',
        ('--logging.file.name=' + (Join-Path $runDirectory ($Name + '.json'))),
        "--spring.data.redis.url=redis://localhost:$redisPort", '--spring.data.redis.password=', '--spring.data.redis.username=',
        '--spring.data.redis.ssl.enabled=false', '--spring.data.redis.host=localhost', "--spring.data.redis.port=$redisPort") + $Properties
    $quotedArguments = $arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList $quotedArguments -WindowStyle Hidden -PassThru -WorkingDirectory $runDirectory -RedirectStandardOutput (Join-Path $runDirectory ($Name + '.stdout.log')) -RedirectStandardError (Join-Path $runDirectory ($Name + '.stderr.log'))
    $processes.Add($process)
    Write-Host "Starting $Name on isolated port $Port (PID $($process.Id))"
    Wait-Until "$Name HTTP readiness" {
        $process.Refresh()
        if ($process.HasExited) { throw "$Name exited with $($process.ExitCode); see $runDirectory" }
        $response = $http.GetAsync("http://localhost:$Port/actuator/health").GetAwaiter().GetResult()
        try {
            if (!$response.IsSuccessStatusCode) { throw "$Name health returned HTTP $([int]$response.StatusCode)" }
            return $true
        } finally { $response.Dispose() }
    } 120
    return $process
}

function Invoke-TestApi([string]$Path, [string]$Method = 'GET', [string]$Token = '',
        $Body = $null, [int]$ExpectedStatus = 200, [string]$Base = $gatewayBase,
        [hashtable]$Headers = @{}) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Base + $Path)
    if ($Token) { $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    foreach ($key in $Headers.Keys) { $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key]) }
    if ($null -ne $Body) {
        $request.Content = [System.Net.Http.StringContent]::new((ConvertTo-Json -InputObject $Body -Depth 12 -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    try {
        $response = $http.SendAsync($request).GetAwaiter().GetResult()
        try {
            $content = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ([int]$response.StatusCode -ne $ExpectedStatus) {
                throw "$Method $Path expected $ExpectedStatus but returned $([int]$response.StatusCode): $content"
            }
            if ($content) { $parsed = $content | ConvertFrom-Json; return $parsed }
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}

function Assert-Check([bool]$Condition, [string]$Description) {
    if (!$Condition) { throw "Acceptance check failed: $Description" }
    $checks.Add($Description)
    Write-Host "PASS $Description"
}

function Inventory-State([string]$ProductId) {
    $sql = "select product_version || '|' || product_active || '|' || available_stock || '|' || reserved_stock from inventory where product_id='$ProductId'"
    return Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'inventory_e2e', '-tAc', $sql)
}

try {
    Write-Host "Isolated catalogue E2E run: $runId"
    Write-Host "Logs: $runDirectory"
    foreach ($image in @($MongoImage, $PostgresImage, $KafkaImage, $RedisImage)) {
        $null = Invoke-Docker @('image', 'inspect', '--format', '{{.Id}}', $image)
    }
    $gatewayConfig = Join-Path $ConfigRepository 'dev/gateway-service-dev.yml'
    if (!(Test-Path -LiteralPath $gatewayConfig)) { throw "Gateway config not found: $gatewayConfig" }
    foreach ($name in @('auth-service', 'product-service', 'inventory-service', 'gateway-service')) {
        if (!(Test-Path -LiteralPath (Join-Path $Workspace "$name/target/$name-1.0.0.jar"))) { throw "Missing packaged $name jar" }
    }
    $postgresPort = Free-Port; $mongoPort = Free-Port; $kafkaPort = Free-Port; $redisPort = Free-Port
    $authPort = Free-Port; $productPort = Free-Port; $inventoryPort = Free-Port; $gatewayPort = Free-Port
    $authBase = "http://localhost:$authPort"; $gatewayBase = "http://localhost:$gatewayPort"

    $postgres = Start-Container 'postgres' @('-p', "127.0.0.1:${postgresPort}:5432", '-e', 'POSTGRES_USER=catalogue',
        '-e', "POSTGRES_PASSWORD=$databasePassword", $PostgresImage)
    $mongo = Start-Container 'mongo' @('-p', "127.0.0.1:${mongoPort}:27017", $MongoImage, '--replSet', 'rs0', '--bind_ip_all')
    $redis = Start-Container 'redis' @('-p', "127.0.0.1:${redisPort}:6379", $RedisImage)
    $kafka = Start-Container 'kafka' @('-p', "127.0.0.1:${kafkaPort}:${kafkaPort}",
        '-e', 'KAFKA_NODE_ID=1', '-e', 'KAFKA_PROCESS_ROLES=broker,controller',
        '-e', "KAFKA_LISTENERS=PLAINTEXT://:${kafkaPort},CONTROLLER://:9093",
        '-e', "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:${kafkaPort}",
        '-e', 'KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093', '-e', 'KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER',
        '-e', 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
        '-e', 'KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT', '-e', 'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
        '-e', 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1', '-e', 'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1',
        '-e', 'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0', $KafkaImage)

    # The image starts a temporary Unix-socket-only server during init; wait for final TCP readiness.
    Wait-Until 'PostgreSQL' { (Invoke-Docker @('exec', $postgres, 'pg_isready', '-h', '127.0.0.1', '-U', 'catalogue')).Contains('accepting connections') }
    $null = Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'postgres', '-c', 'CREATE DATABASE auth_e2e')
    $null = Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'postgres', '-c', 'CREATE DATABASE inventory_e2e')
    Wait-Until 'MongoDB' { (Invoke-Docker @('exec', $mongo, 'mongosh', '--quiet', '--eval', 'db.runCommand({ping:1}).ok')) -eq '1' }
    $null = Invoke-Docker @('exec', $mongo, 'mongosh', '--quiet', '--eval', "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})")
    Wait-Until 'MongoDB replica primary' { (Invoke-Docker @('exec', $mongo, 'mongosh', '--quiet', '--eval', 'db.hello().isWritablePrimary')) -eq 'true' }
    Wait-Until 'Kafka' { (Invoke-Docker @('exec', $kafka, '/opt/kafka/bin/kafka-topics.sh', '--bootstrap-server', "localhost:$kafkaPort", '--list')) -ne $null }
    $null = Invoke-Docker @('exec', $kafka, '/opt/kafka/bin/kafka-topics.sh', '--bootstrap-server', "localhost:$kafkaPort", '--create', '--if-not-exists', '--topic', 'product.lifecycle.v1', '--partitions', '1', '--replication-factor', '1')

    $security = @("--spring.security.oauth2.resourceserver.jwt.issuer-uri=$authBase")
    $producer = @("--spring.kafka.bootstrap-servers=localhost:$kafkaPort",
        '--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer',
        '--spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer')
    $database = @('--spring.datasource.username=catalogue', "--spring.datasource.password=$databasePassword", '--spring.jpa.hibernate.ddl-auto=validate')
    $auth = Start-ServiceJar 'auth-service' $authPort ($database + @(
        "--spring.datasource.url=jdbc:postgresql://localhost:$postgresPort/auth_e2e",
        "--spring.kafka.bootstrap-servers=localhost:$kafkaPort", '--auth.outbox.poll-delay-ms=3600000',
        '--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer',
        '--spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer',
        "--auth.authorization-server.issuer=$authBase", '--auth.signing-key.source=GENERATED',
        '--auth.signing-key.allow-ephemeral=true', '--auth.action-token.signing-secret=catalogue-e2e-action-secret-at-least-32-bytes',
        "--auth.internal.service-token=$serviceToken", "--AUTH_INTERNAL_SERVICE_TOKEN=$serviceToken"))
    $productProcess = Start-ServiceJar 'product-service' $productPort ($security + $producer + @(
        "--spring.data.mongodb.uri=mongodb://localhost:$mongoPort/product_e2e?replicaSet=rs0&directConnection=true",
        '--spring.data.mongodb.uuid-representation=standard', '--product.images.allowed-hosts=cdn.example.com',
        "--auth.internal.base-url=$authBase", "--auth.internal.service-token=$serviceToken", "--AUTH_INTERNAL_SERVICE_TOKEN=$serviceToken",
        '--product.outbox.poll-delay-ms=250', '--product.outbox.initial-delay-ms=1000'))
    $inventoryProcess = Start-ServiceJar 'inventory-service' $inventoryPort ($security + $producer + $database + @(
        "--spring.datasource.url=jdbc:postgresql://localhost:$postgresPort/inventory_e2e", '--grpc.server.port=-1',
        "--product-service.base-url=http://localhost:$productPort", '--spring.kafka.consumer.auto-offset-reset=earliest',
        '--spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer',
        '--spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer',
        '--spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer',
        '--spring.kafka.consumer.properties.spring.json.trusted.packages=com.ecommerce.common.events.product,com.ecommerce.common.events.core'))
    $gatewayProcess = Start-ServiceJar 'gateway-service' $gatewayPort ($security + @(
        ('--spring.config.additional-location=file:' + $gatewayConfig.Replace('\', '/')),
        "--AUTH_SERVICE_URI=$authBase", "--PRODUCT_SERVICE_URI=http://localhost:$productPort",
        "--INVENTORY_SERVICE_URI=http://localhost:$inventoryPort", '--GATEWAY_AUTH_RATE_LIMIT_PER_SECOND=100',
        '--GATEWAY_AUTH_RATE_LIMIT_BURST=100'))

    # Only isolated fixture accounts are promoted in the disposable PostgreSQL database.
    foreach ($email in @('admin@catalogue.test', 'seller@catalogue.test', 'other@catalogue.test')) {
        $null = Invoke-TestApi '/api/v1/auth/register' 'POST' '' @{ name = 'Catalogue fixture'; email = $email; password = $testPassword } 202
    }
    $seed = "update users set status='ACTIVE', email_verified_at=now(); insert into user_roles(user_id,role_id) select u.id,r.id from users u cross join roles r where (u.email='admin@catalogue.test' and r.code='ADMIN') or (u.email in ('seller@catalogue.test','other@catalogue.test') and r.code='SELLER') on conflict do nothing;"
    $null = Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'auth_e2e', '-v', 'ON_ERROR_STOP=1', '-c', $seed)
    $adminSession = Invoke-TestApi '/api/v1/auth/login' 'POST' '' @{ email = 'admin@catalogue.test'; password = $testPassword }
    $sellerSession = Invoke-TestApi '/api/v1/auth/login' 'POST' '' @{ email = 'seller@catalogue.test'; password = $testPassword }
    $otherSession = Invoke-TestApi '/api/v1/auth/login' 'POST' '' @{ email = 'other@catalogue.test'; password = $testPassword }
    $adminToken = $adminSession.accessToken; $sellerToken = $sellerSession.accessToken; $otherToken = $otherSession.accessToken
    $sellerId = $sellerSession.user.id
    Assert-Check ($adminToken.Length -gt 0 -and $sellerToken.Length -gt 0) 'Real Auth registration/login through Gateway'
    $null = Invoke-TestApi "/internal/auth/sellers/$sellerId/eligibility" 'GET' '' $null 403 $authBase
    $eligible = Invoke-TestApi "/internal/auth/sellers/$sellerId/eligibility" 'GET' '' $null 200 $authBase @{ 'X-Internal-Auth' = $serviceToken }
    Assert-Check $eligible.eligible 'Auth eligibility requires the shared service credential'

    $draft = @{ name = 'E2E Wireless Phone'; description = 'Searchable catalogue fixture'; price = 499.00; currency = 'INR'; category = 'Electronics'; brand = 'Pepekart'; imageUrls = @('https://cdn.example.com/e2e/phone.jpg') }
    $product = Invoke-TestApi "/api/v1/admin/products?sellerId=$sellerId" 'POST' $adminToken $draft 201
    $productId = $product.id
    Assert-Check ($product.sellerId -eq $sellerId -and $product.currency -eq 'INR') 'Gateway admin create validates real Auth seller eligibility'
    Wait-Until 'Kafka lifecycle provisioning' { (Inventory-State $productId) -eq '1|true|0|0' }
    $stock = Invoke-TestApi "/api/v1/admin/inventory/$productId" 'GET' $adminToken
    Assert-Check ($stock.availableStock -eq 0) 'Product Mongo transaction -> Kafka -> Inventory PostgreSQL'

    $null = Invoke-TestApi "/api/v1/seller/products/$productId" 'GET' $otherToken $null 404
    $owned = Invoke-TestApi "/api/v1/seller/products/$productId" 'GET' $sellerToken
    Assert-Check ($owned.id -eq $productId) 'Seller detail ownership enforced through Gateway'
    $detail = Invoke-TestApi "/api/v1/products/$productId"
    $facets = Invoke-TestApi '/api/v1/products/facets'
    $page = Invoke-TestApi '/api/v1/products?q=phone&category=Electronics&brand=Pepekart&minPrice=400&maxPrice=600&sort=relevance&page=0&size=1'
    Assert-Check ($detail.id -eq $productId -and @($page.content).Count -eq 1 -and @($facets.categories).Count -gt 0) 'Anonymous Gateway detail, facets, search/filter/sort and pagination'
    foreach ($query in @('minPrice=400', 'maxPrice=600')) {
        $page = Invoke-TestApi ("/api/v1/products?$query")
        Assert-Check (@($page.content).Count -eq 1) "Anonymous one-sided price filter $query"
    }
    foreach ($query in @('minPrice=600', 'maxPrice=400')) {
        $page = Invoke-TestApi ("/api/v1/products?$query")
        Assert-Check (@($page.content).Count -eq 0) "One-sided price filter excludes nonmatching product: $query"
    }
    foreach ($query in @('minPrice=600&maxPrice=400', 'page=-1', 'size=101', 'sort=unsupported')) {
        $null = Invoke-TestApi ("/api/v1/products?$query") 'GET' '' $null 400
    }
    Assert-Check $true 'Invalid filter range, page, size and sort return 400 through Gateway'
    $invalidImage = $draft.Clone(); $invalidImage.imageUrls = @('https://unapproved.example/e2e.jpg')
    $null = Invoke-TestApi "/api/v1/admin/products?sellerId=$sellerId" 'POST' $adminToken $invalidImage 400
    Assert-Check $true 'Unapproved image host is rejected'

    $bulkDraft = $draft.Clone(); $bulkDraft.name = 'E2E Bulk Product'
    $bulk = @(Invoke-TestApi '/api/v1/seller/products/bulk' 'POST' $sellerToken @($bulkDraft, $bulkDraft) 201)
    Assert-Check ($bulk.Count -eq 2 -and $bulk[0].sellerId -eq $sellerId) 'Seller bulk creation works through the configured Gateway route'
    foreach ($entry in $bulk) { Wait-Until 'bulk inventory provisioning' { (Inventory-State $entry.id) -eq '1|true|0|0' } }
    $firstPage = Invoke-TestApi '/api/v1/products?page=0&size=1&sort=name_asc'
    $secondPage = Invoke-TestApi '/api/v1/products?page=1&size=1&sort=name_asc'
    Assert-Check (@($firstPage.content).Count -eq 1 -and @($secondPage.content).Count -eq 1 -and $firstPage.content[0].id -ne $secondPage.content[0].id) 'Deterministic anonymous pagination returns distinct consecutive products'
    $null = Invoke-TestApi "/api/v1/admin/inventory/$productId" 'PUT' $adminToken @{ availableStock = 17 }
    $updated = $draft.Clone(); $updated.price = 549.00; $updated.active = $true
    $null = Invoke-TestApi "/api/v1/seller/products/$productId" 'PUT' $sellerToken $updated
    Wait-Until 'update lifecycle consumption' { (Inventory-State $productId) -eq '2|true|17|0' }
    $null = Invoke-TestApi "/api/v1/seller/products/$productId/deactivate" 'POST' $sellerToken
    Wait-Until 'deactivate lifecycle consumption' { (Inventory-State $productId) -eq '3|false|17|0' }
    $null = Invoke-TestApi "/api/v1/products/$productId" 'GET' '' $null 404
    $null = Invoke-TestApi "/api/v1/seller/products/$productId/reactivate" 'POST' $sellerToken
    Wait-Until 'seller reactivate lifecycle consumption' { (Inventory-State $productId) -eq '4|true|17|0' }
    Assert-Check $true 'Updated, deactivated, and reactivated snapshots reach Inventory without resetting stock'
    $null = Invoke-TestApi "/api/v1/admin/products/$productId" 'DELETE' $adminToken $null 204
    Wait-Until 'archive lifecycle consumption' { (Inventory-State $productId) -eq '5|false|17|0' }
    $null = Invoke-TestApi "/api/v1/products/$productId" 'GET' '' $null 404
    $hiddenPage = Invoke-TestApi '/api/v1/products?q=Wireless'
    $hidden = Invoke-TestApi "/api/v1/admin/products/$productId" 'GET' $adminToken
    Assert-Check (!$hidden.active -and @($hiddenPage.content).Count -eq 0) 'Archival hides public list/detail and preserves management history and stock'
    $null = Invoke-TestApi "/api/v1/admin/products/$productId/reactivate" 'POST' $adminToken
    Wait-Until 'reactivation lifecycle consumption' { (Inventory-State $productId) -eq '6|true|17|0' }
    $null = Invoke-TestApi "/api/v1/products/$productId"
    # Simulate a missed create only for one validated, zero-stock fixture row in the disposable DB.
    $missingProductId = [Guid]::Parse([string]$bulk[0].id).ToString()
    $null = Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'inventory_e2e', '-v', 'ON_ERROR_STOP=1', '-c', "delete from inventory where product_id='$missingProductId'")
    Assert-Check ([string]::IsNullOrWhiteSpace((Inventory-State $missingProductId))) 'Isolated missing-inventory fixture prepared for reconciliation'
    $reconciled = Invoke-TestApi '/api/v1/admin/products/outbox/reconcile?size=100' 'POST' $adminToken
    Assert-Check ($reconciled.enqueued -eq 3) 'Admin reconciliation republishes current product snapshots through Gateway'
    Wait-Until 'reconciliation recreates the missing fixture row' { (Inventory-State $missingProductId) -eq '1|true|0|0' }
    Assert-Check ((Inventory-State $productId) -eq '6|true|17|0') 'Reconciliation heals missing Inventory and preserves stocked product quantities'
    $null = Invoke-TestApi '/api/v1/admin/products/outbox/replay-dead-letters' 'POST' $adminToken $null 204

    $null = Invoke-Docker @('exec', $postgres, 'psql', '-U', 'catalogue', '-d', 'auth_e2e', '-c', "update users set status='SUSPENDED' where id='$sellerId'")
    $null = Invoke-TestApi "/api/v1/admin/products?sellerId=$sellerId" 'POST' $adminToken $draft 400
    $null = Invoke-TestApi '/api/v1/seller/products' 'POST' $sellerToken $draft 400
    Assert-Check $true 'Suspended seller is rejected by real Auth eligibility for admin and seller creates'
    # Stop only this fixture Auth process: Product must fail closed when eligibility is unavailable.
    $auth.Kill(); $auth.WaitForExit(10000)
    $null = Invoke-TestApi "/api/v1/admin/products?sellerId=$sellerId" 'POST' $adminToken $draft 503
    Assert-Check $true 'Product returns 503 and fails closed when Auth eligibility is unavailable'
} catch {
    $failure = $_.Exception.Message
    Write-Error -Message $failure -ErrorAction Continue
} finally {
    foreach ($process in $processes) {
        try { $process.Refresh(); if (!$process.HasExited) { $process.Kill(); $null = $process.WaitForExit(10000) } } catch { Write-Warning $_.Exception.Message }
    }
    foreach ($id in $containers) {
        try {
            $labels = (Invoke-Docker @('inspect', '--format', '{{json .Config.Labels}}', $id)) | ConvertFrom-Json
            $label = $labels.'com.pepekart.catalogue-e2e.run'
            if ($label -ne $runId) { throw "Refusing to remove unowned container $id" }
            $previousErrorAction = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            $containerLogs = & docker logs $id 2>&1
            $ErrorActionPreference = $previousErrorAction
            $containerLogs | Out-File -LiteralPath (Join-Path $runDirectory ($id.Substring(0, 12) + '.container.log')) -Encoding utf8
            $null = Invoke-Docker @('rm', '--force', '--volumes', $id)
        } catch { Write-Warning $_.Exception.Message }
    }
    $http.Dispose()
    @{ runId = $runId; passed = ($null -eq $failure); checks = $checks.ToArray(); failure = $failure; completedAt = [DateTime]::UtcNow.ToString('o') } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $runDirectory 'result.json') -Encoding UTF8
}
if ($failure) { throw "Catalogue E2E failed. Details: $runDirectory/result.json" }
Write-Host "PASS: $($checks.Count) cross-service acceptance checks. Results: $runDirectory/result.json"
