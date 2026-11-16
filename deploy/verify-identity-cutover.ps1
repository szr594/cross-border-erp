$ErrorActionPreference = 'Stop'
$suffix = [guid]::NewGuid().ToString('N').Substring(0,8)
$sourceDb = 'cross_border_erp_' + $suffix
$targetDb = 'cross_erp_identity_' + $suffix
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$runDir = Join-Path $root ('tmp/identity-cutover-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $runDir | Out-Null
$rsa = [System.Security.Cryptography.RSA]::Create(2048)
$privateFile = Join-Path $runDir 'private.pem'
$publicFile = Join-Path $runDir 'public.pem'
[IO.File]::WriteAllText($privateFile, $rsa.ExportPkcs8PrivateKeyPem())
[IO.File]::WriteAllText($publicFile, $rsa.ExportSubjectPublicKeyInfoPem())
$rsa.Dispose()
$password = 'cutover-only-' + [guid]::NewGuid().ToString('N')
$env:JWT_SECRET = 'cutover-only-' + [guid]::NewGuid().ToString('N')
$env:MYSQL_USER = 'root'
$env:MYSQL_PASSWORD = 'identity-it-only-password'
$env:DATABASE_URL = "jdbc:mysql://127.0.0.1:13316/${sourceDb}?connectionTimeZone=UTC"
$env:IDENTITY_DATABASE_URL = "jdbc:mysql://127.0.0.1:13316/${targetDb}?connectionTimeZone=UTC"
$env:IDENTITY_DATABASE_USER = 'root'
$env:IDENTITY_DATABASE_PASSWORD = $env:MYSQL_PASSWORD
$env:IDENTITY_PRIVATE_KEY = 'file:' + $privateFile.Replace('\','/')
$env:IDENTITY_PUBLIC_KEY = 'file:' + $publicFile.Replace('\','/')
$env:IDENTITY_KEY_ID = 'cutover-test'
$env:SECURE_COOKIE = 'false'
$env:IDENTITY_PORT = '19081'
$env:BACKEND_PORT = '19090'
$env:GATEWAY_PORT = '19080'
$env:LEGACY_BACKEND_URL = 'http://127.0.0.1:19090'
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
function Start-ServiceJar($name, $jar, $profile='prod') {
    $process = Start-Process java -ArgumentList @('-jar', $jar, "--spring.profiles.active=$profile") -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDir "$name.out.log") -RedirectStandardError (Join-Path $runDir "$name.err.log")
    $processes.Add($process)
    return $process
}
function Wait-Http($uri) {
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        try { $response=Invoke-WebRequest $uri -NoProxy -SkipHttpErrorCheck -TimeoutSec 2; if ([int]$response.StatusCode -lt 500) { return } } catch {}
        Start-Sleep -Seconds 1
    }
    throw "Service did not become ready: $uri. Logs: $runDir"
}
function Request($path, $method='GET', $token='', $body='') {
    $headers=@{}
    if ($token) { $headers.Authorization='Bearer ' + $token }
    $parameters=@{Uri="http://127.0.0.1:19080$path";Method=$method;Headers=$headers;NoProxy=$true;SkipHttpErrorCheck=$true;TimeoutSec=15}
    if ($body) { $parameters.Body=$body; $parameters.ContentType='application/json' }
    return Invoke-WebRequest @parameters
}
function Assert-Status($response,$status,$label) {
    if ([int]$response.StatusCode -ne $status) { throw "$label expected $status, received $($response.StatusCode)" }
    Write-Output "PASS $label ($status)"
}
$backendJar='backend/target/cross-border-erp-0.1.0-SNAPSHOT.jar'
$identityJar='services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar'
$gatewayJar='services/gateway-service/target/gateway-service-0.1.0-SNAPSHOT.jar'
try {
    # Only the dedicated test container is permitted. Existing schemas are never overwritten.
    docker exec erp-identity-it-mysql mysql -uroot -pidentity-it-only-password -e "CREATE DATABASE $sourceDb; CREATE DATABASE $targetDb;"
    if ($LASTEXITCODE -ne 0) { throw 'Isolated cutover databases must not already exist' }
    $publicKey=$env:IDENTITY_PUBLIC_KEY
    Remove-Item Env:IDENTITY_PUBLIC_KEY
    $env:APP_AUTH_LEGACY_ENABLED='true'
    $env:INITIAL_OWNER_LOGIN='cutover_owner'
    $env:INITIAL_OWNER_PASSWORD=$password
    & java -jar $backendJar --spring.profiles.active=prod,init-owner --server.port=0 *> (Join-Path $runDir 'initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'Legacy initialization failed' }
    Remove-Item Env:INITIAL_OWNER_PASSWORD
    $env:DEMO_OWNER_PASSWORD=$password
    $legacy=Start-ServiceJar 'legacy' $backendJar 'dev'
    Wait-Http 'http://127.0.0.1:19090/api/auth/me'
    $gateway=Start-ServiceJar 'gateway-legacy' $gatewayJar
    Wait-Http 'http://127.0.0.1:19080/actuator/health'
    $body=@{loginName='cutover_owner';password=$password}|ConvertTo-Json -Compress
    $oldLogin=Request '/api/auth/login' 'POST' '' $body
    Assert-Status $oldLogin 200 'legacy login before migration'
    $oldPayload=$oldLogin.Content|ConvertFrom-Json
    $oldToken=$oldPayload.accessToken
    $oldId=$oldPayload.user.id
    $beforeOrders=Request '/api/orders' 'GET' $oldToken
    Assert-Status $beforeOrders 200 'existing demo orders before migration'
    $beforeData=$beforeOrders.Content|ConvertFrom-Json
    if ($beforeData.items.Count -lt 1) { throw 'Existing order fixture missing' }
    $orderId=$beforeData.items[0].id
    $beforeDetail=(Request "/api/orders/$orderId" 'GET' $oldToken).Content|ConvertFrom-Json
    if (!$beforeDetail.responsibleUser.id) { throw 'Order responsible user fixture missing' }
    $gateway.Kill();$gateway.WaitForExit();$legacy.Kill();$legacy.WaitForExit()
    $env:IDENTITY_PUBLIC_KEY=$publicKey
    $identity=Start-ServiceJar 'identity' $identityJar
    Wait-Http 'http://127.0.0.1:19081/api/auth/me'
    $migration=(Get-Content deploy/identity-migrate.sql -Raw).Replace('cross_border_erp.', "$sourceDb.").Replace('cross_erp_identity.', "$targetDb.")
    $migration | docker exec -i erp-identity-it-mysql mysql -uroot -pidentity-it-only-password
    if ($LASTEXITCODE -ne 0) { throw 'Migration failed' }
    $checks=docker exec erp-identity-it-mysql mysql -uroot -pidentity-it-only-password -N -e "SELECT COUNT(*) FROM $sourceDb.app_users s JOIN $targetDb.app_users t ON s.id=t.id AND s.password_hash=t.password_hash;"
    if ($LASTEXITCODE -ne 0 -or [int]$checks -lt 2) { throw 'Migrated ID/hash verification failed' }
    Write-Output 'PASS actual SQL migration preserves user ID and password hash'
    $env:APP_AUTH_LEGACY_ENABLED='false'
    $env:IDENTITY_SERVICE_URL='http://127.0.0.1:19081'
    $legacy=Start-ServiceJar 'backend-rsa' $backendJar
    Wait-Http 'http://127.0.0.1:19090/api/auth/me'
    $gateway=Start-ServiceJar 'gateway-rsa' $gatewayJar
    Wait-Http 'http://127.0.0.1:19080/actuator/health'
    $login=Request '/api/auth/login' 'POST' '' $body
    Assert-Status $login 200 'migrated password login through identity route'
    $payload=$login.Content|ConvertFrom-Json
    if ($payload.user.id -ne $oldId) { throw 'User ID changed' }
    $token=$payload.accessToken
    Assert-Status (Request '/api/auth/me' 'GET' $token) 200 'identity me with RS256'
    Assert-Status (Request '/api/orders' 'GET' $token) 200 'gateway and legacy backend accept RS256'
    $afterDetail=(Request "/api/orders/$orderId" 'GET' $token).Content|ConvertFrom-Json
    if (($beforeDetail.responsibleUser|ConvertTo-Json -Compress) -ne ($afterDetail.responsibleUser|ConvertTo-Json -Compress)) {
        throw 'Existing order responsible user changed'
    }
    Assert-Status (Request '/api/inventory/lots' 'GET' $token) 200 'existing inventory remains queryable'
    Write-Output 'PASS existing order responsible user association preserved'
    Assert-Status (Request '/api/orders' 'GET' $oldToken) 401 'RSA mode rejects old HS256 token'
    $direct=Invoke-WebRequest 'http://127.0.0.1:19090/api/auth/login' -Method Post -ContentType 'application/json' -Body $body -Headers @{Authorization="Bearer $token"} -NoProxy -SkipHttpErrorCheck
    Assert-Status $direct 404 'legacy authentication controller disabled'
    $migration | docker exec -i erp-identity-it-mysql mysql -uroot -pidentity-it-only-password 2> (Join-Path $runDir 'repeat-migration.log')
    if ($LASTEXITCODE -eq 0) { throw 'Repeat migration must fail' }
    Write-Output 'PASS repeat migration refused'
    $gateway.Kill();$gateway.WaitForExit();$legacy.Kill();$legacy.WaitForExit();$identity.Kill();$identity.WaitForExit()
    Remove-Item Env:IDENTITY_PUBLIC_KEY
    Remove-Item Env:IDENTITY_SERVICE_URL
    $env:APP_AUTH_LEGACY_ENABLED='true'
    $legacy=Start-ServiceJar 'backend-rollback' $backendJar
    Wait-Http 'http://127.0.0.1:19090/api/auth/me'
    $gateway=Start-ServiceJar 'gateway-rollback' $gatewayJar
    Wait-Http 'http://127.0.0.1:19080/actuator/health'
    $rollback=Request '/api/auth/login' 'POST' '' $body
    Assert-Status $rollback 200 'rollback login with original password'
    $rollbackToken=($rollback.Content|ConvertFrom-Json).accessToken
    Assert-Status (Request '/api/orders' 'GET' $rollbackToken) 200 'rollback business query'
    Assert-Status (Request '/api/orders' 'GET' $token) 401 'rollback rejects RS256 token'
    Write-Output "ALL CUTOVER CHECKS PASSED; logs=$runDir"
} finally {
    foreach ($process in $processes) { if (!$process.HasExited) { $process.Kill(); $process.WaitForExit() } }
}
