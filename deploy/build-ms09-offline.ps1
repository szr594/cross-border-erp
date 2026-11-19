param([Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9._-]+$')][string]$Tag)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$release=Join-Path $root "output/ms09-$Tag"
$context=Join-Path $root "tmp/ms09-image-$Tag"
if(Test-Path $release){throw 'Release directory already exists; use a new tag'}
New-Item -ItemType Directory -Path $release,$context | Out-Null
$images=@()
foreach($service in @('backend','identity','gateway','order','procurement','frontend')){
 $folder=Join-Path $context $service
 New-Item -ItemType Directory -Path $folder | Out-Null
 if($service -eq 'frontend'){
  Copy-Item -LiteralPath frontend/dist -Destination (Join-Path $folder 'dist') -Recurse
  Copy-Item -LiteralPath deploy/nginx/default.conf -Destination (Join-Path $folder 'default.conf')
  $dockerfile="FROM nginx:stable-alpine`nCOPY default.conf /etc/nginx/conf.d/default.conf`nCOPY dist /usr/share/nginx/html`nEXPOSE 80`n"
 }else{
  $jar=if($service -eq 'backend'){'backend/target/cross-border-erp-0.1.0-SNAPSHOT.jar'}else{"services/$service-service/target/$service-service-0.1.0-SNAPSHOT.jar"}
  Copy-Item -LiteralPath $jar -Destination (Join-Path $folder 'app.jar')
  $dockerfile="FROM eclipse-temurin:17-jre-alpine`nRUN addgroup -S erp && adduser -S erp -G erp`nWORKDIR /app`nCOPY app.jar app.jar`nUSER erp`nENTRYPOINT [`"java`",`"-jar`",`"app.jar`"]`n"
 }
 [IO.File]::WriteAllText((Join-Path $folder 'Dockerfile'),$dockerfile.Replace('\"','"'),[Text.UTF8Encoding]::new($false))
 $image="cross-border-erp-production-${service}:$Tag"
 & docker build --pull=false -t $image $folder
 if($LASTEXITCODE -ne 0){throw "Image build failed: $service"}
 $images+=$image
}
$images+=@('mysql:8.4','redis:7.4','rabbitmq:3.13-management')
& docker save -o (Join-Path $release 'ms09-images.tar') @images
if($LASTEXITCODE -ne 0){throw 'docker save failed'}
foreach($file in @('compose.production.yml','compose.identity.yml','compose.order.yml','compose.procurement.yml','deploy/compose.ms09-runtime.yml','deploy/compose.ms09-cutover.yml','deploy/compose.ms09-rollback.yml','deploy/verify-ms09-vm-reboot.sh','deploy/ms09-preflight.sh','deploy/ms09.env.example','docs/ms09-release-runbook.md')){
 Copy-Item -LiteralPath $file -Destination $release
}
$manifest=[ordered]@{tag=$Tag;commit=(& git rev-parse HEAD);workingTreeDirty=[bool](& git status --porcelain);images=@()}
foreach($image in $images){$manifest.images+=@{name=$image;id=(& docker image inspect --format '{{.Id}}' $image)}}
[IO.File]::WriteAllText((Join-Path $release 'build-manifest.json'),($manifest | ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
$lines=Get-ChildItem -LiteralPath $release -File | Sort-Object Name | ForEach-Object {((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower())+'  '+$_.Name}
[IO.File]::WriteAllText((Join-Path $release 'SHA256SUMS'),(($lines -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
Write-Output "Prepared $release"
