$ErrorActionPreference = 'Stop'

$repoRoot = 'C:\Users\ktsvc\Documents\CheapestOilFinder Backend'
$logPath = Join-Path $repoRoot 'logs\OPINET_HOT100_SYNC_2026-05-28.md'
$apiBase = 'http://localhost:18090'

function Invoke-Psql([string]$sql) {
  $args = @('compose','exec','-T','postgres','psql','-U','oil_user','-d','oil_price_db','-A','-F',',','-t','-c',$sql)
  $output = & docker @args
  if ($LASTEXITCODE -ne 0) {
    throw "psql failed: $sql"
  }
  return $output
}

function Get-DbCounts {
  $sql = "select (select count(*) from gas_station) as gas_station_count, (select count(*) from fuel) as fuel_count, (select count(*) from sector_sync_log) as sector_sync_log_count, (select count(*) from sync_log) as sync_log_count;"
  $row = Invoke-Psql $sql
  $obj = $row | ConvertFrom-Csv -Header gas_station_count,fuel_count,sector_sync_log_count,sync_log_count | Select-Object -First 1
  return [pscustomobject]@{
    gas_station_count = [int]$obj.gas_station_count
    fuel_count = [int]$obj.fuel_count
    sector_sync_log_count = [int]$obj.sector_sync_log_count
    sync_log_count = [int]$obj.sync_log_count
  }
}

function Get-CsvRows([string]$sql, [string[]]$headers) {
  $raw = Invoke-Psql $sql
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return @()
  }
  return @($raw | ConvertFrom-Csv -Header $headers)
}

$before = Get-DbCounts

$sectorSql = "select sector_id, sector_code, center_lat, center_lon, katec_x, katec_y from sync_sector where enabled = true and sync_tier = 'HOT' order by sector_id;"
$sectorRows = Get-CsvRows $sectorSql @('sector_id','sector_code','center_lat','center_lon','katec_x','katec_y')
$sectors = @($sectorRows | ForEach-Object {
  [pscustomobject]@{
    SectorId = [int64]$_.sector_id
    SectorCode = $_.sector_code
    CenterLat = [double]$_.center_lat
    CenterLon = [double]$_.center_lon
    KatecX = [double]$_.katec_x
    KatecY = [double]$_.katec_y
  }
})

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# OPINET HOT 100 SYNC 2026-05-28')
$lines.Add('')
$lines.Add('## Scope')
$lines.Add('- Target sectors: ' + $sectors.Count + ' enabled HOT sectors')
$lines.Add('- Sync endpoint: POST /api/admin/sync/sectors/{sectorId}/report')
$lines.Add('- Fuel types per sector: REGULAR_GASOLINE, PREMIUM_GASOLINE, DIESEL')
$lines.Add('- Server: ' + $apiBase)
$lines.Add('- Preconditions: OPINET_ENABLED=true, OPINET_API_KEY_FILE=C:\Users\ktsvc\Documents\CheapestOilFinder Backend\secrets\opinet-api-key.txt')
$lines.Add('')
$lines.Add('## Baseline DB Snapshot')
$lines.Add('- gas_station: ' + $before.gas_station_count)
$lines.Add('- fuel: ' + $before.fuel_count)
$lines.Add('- sector_sync_log: ' + $before.sector_sync_log_count)
$lines.Add('- sync_log: ' + $before.sync_log_count)
$lines.Add('')
$lines.Add('## Target Sectors')
foreach ($sector in $sectors) {
  $lines.Add('- sectorId=' + $sector.SectorId + '; sectorCode=' + $sector.SectorCode + '; centerLat=' + ([math]::Round($sector.CenterLat, 6)) + '; centerLon=' + ([math]::Round($sector.CenterLon, 6)) + '; katecX=' + ([math]::Round($sector.KatecX, 3)) + '; katecY=' + ([math]::Round($sector.KatecY, 3)))
}
$lines.Add('')
$lines.Add('## Per-Sector Results')

$totalStations = 0
$sectorSuccessCount = 0
$fuelTotals = @{ REGULAR_GASOLINE = 0; PREMIUM_GASOLINE = 0; DIESEL = 0 }
$fuelSuccessCounts = @{ REGULAR_GASOLINE = 0; PREMIUM_GASOLINE = 0; DIESEL = 0 }
$sampleReport = $null
$sampleSector = $null
$i = 0

foreach ($sector in $sectors) {
  $i++
  if ($i % 10 -eq 0) {
    Write-Host "Progress: $i/$($sectors.Count) sectors"
  }

  $uri = $apiBase + '/api/admin/sync/sectors/' + $sector.SectorId + '/report'
  try {
    $report = Invoke-RestMethod -Method Post -Uri $uri
  } catch {
    $lines.Add('- sectorId=' + $sector.SectorId + '; sectorCode=' + $sector.SectorCode + '; success=false; stationCount=0; REGULAR=-; PREMIUM=-; DIESEL=-; note=HTTP error: ' + ($_.Exception.Message -replace '\|', '/'))
    continue
  }

  if ($null -eq $sampleReport -and $report.stationCount -gt 0) {
    $sampleReport = $report
    $sampleSector = $sector
  }

  if ($report.success) {
    $sectorSuccessCount++
  }
  $totalStations += [int]$report.stationCount

  $fuelMap = @{}
  $noteParts = New-Object System.Collections.Generic.List[string]
  foreach ($fuelCall in $report.fuelCalls) {
    $fuelType = [string]$fuelCall.fuelType
    $fuelMap[$fuelType] = if ($fuelCall.success) { 'OK/' + $fuelCall.stationCount } else { 'FAIL/' + $fuelCall.stationCount }
    $fuelTotals[$fuelType] += [int]$fuelCall.stationCount
    if ($fuelCall.success) { $fuelSuccessCounts[$fuelType] += 1 }
    if (-not $fuelCall.success -and $fuelCall.errorMessage) {
      $shortMsg = $fuelCall.errorMessage.Split('|')[0].Trim()
      $noteParts.Add($fuelType + ': ' + $shortMsg)
    }
  }
  $note = if ($noteParts.Count -gt 0) { ($noteParts -join ' / ') -replace '\|', '/' } else { 'all good' }
  $lines.Add('- sectorId=' + $sector.SectorId + '; sectorCode=' + $sector.SectorCode + '; success=' + $report.success + '; stationCount=' + $report.stationCount + '; REGULAR=' + $fuelMap['REGULAR_GASOLINE'] + '; PREMIUM=' + $fuelMap['PREMIUM_GASOLINE'] + '; DIESEL=' + $fuelMap['DIESEL'] + '; note=' + $note)
}

$after = Get-DbCounts
$gasSql = "select uni_id, poll_div_cd, replace(os_nm, ',', ';') as os_nm, replace(coalesce(addr,''), ',', ';') as addr, lat, lon, updated_at from gas_station order by updated_at desc, uni_id desc limit 5;"
$fuelSql = "select uni_id, gas_hign, gas_low, disl, lpg, updated_at from fuel order by updated_at desc, uni_id desc limit 5;"
$gasPreview = Get-CsvRows $gasSql @('uni_id','poll_div_cd','os_nm','addr','lat','lon','updated_at')
$fuelPreview = Get-CsvRows $fuelSql @('uni_id','gas_hign','gas_low','disl','lpg','updated_at')

$lines.Add('')
$lines.Add('## Result Summary')
$lines.Add('- sectors attempted: ' + $sectors.Count)
$lines.Add('- sectors fully successful: ' + $sectorSuccessCount)
$lines.Add('- station rows written: ' + $totalStations)
$lines.Add('- REGULAR_GASOLINE: calls success ' + $fuelSuccessCounts.REGULAR_GASOLINE + ', rows ' + $fuelTotals.REGULAR_GASOLINE)
$lines.Add('- PREMIUM_GASOLINE: calls success ' + $fuelSuccessCounts.PREMIUM_GASOLINE + ', rows ' + $fuelTotals.PREMIUM_GASOLINE)
$lines.Add('- DIESEL: calls success ' + $fuelSuccessCounts.DIESEL + ', rows ' + $fuelTotals.DIESEL)
$lines.Add('')
$lines.Add('## Sample Successful Sector')
if ($sampleReport) {
  $lines.Add('- sectorId: ' + $sampleSector.SectorId)
  $lines.Add('- sectorCode: ' + $sampleSector.SectorCode)
  $lines.Add('- success: ' + $sampleReport.success)
  $lines.Add('- stationCount: ' + $sampleReport.stationCount)
  foreach ($fuelCall in $sampleReport.fuelCalls) {
    $lines.Add('- ' + $fuelCall.fuelType + ': success=' + $fuelCall.success + ', stationCount=' + $fuelCall.stationCount)
    if ($fuelCall.preview.Count -gt 0) {
      $lines.Add('  preview:')
      foreach ($preview in $fuelCall.preview | Select-Object -First 5) {
        $lines.Add('    - uniId=' + $preview.uniId + '; stationName=' + $preview.stationName + '; pollDivCd=' + $preview.pollDivCd + '; price=' + $preview.price + '; katecX=' + ([math]::Round($preview.katecX, 3)) + '; katecY=' + ([math]::Round($preview.katecY, 3)))
      }
    }
  }
}

$lines.Add('')
$lines.Add('## DB Snapshot After Sync')
$lines.Add('- gas_station: ' + $after.gas_station_count)
$lines.Add('- fuel: ' + $after.fuel_count)
$lines.Add('- sector_sync_log: ' + $after.sector_sync_log_count)
$lines.Add('- sync_log: ' + $after.sync_log_count)
$lines.Add('')
$lines.Add('## Gas Station Preview (5)')
foreach ($row in $gasPreview) {
  $lines.Add('- uniId=' + $row.uni_id + '; pollDivCd=' + $row.poll_div_cd + '; osNm=' + $row.os_nm + '; addr=' + $row.addr + '; lat=' + $row.lat + '; lon=' + $row.lon + '; updatedAt=' + $row.updated_at)
}
$lines.Add('')
$lines.Add('## Fuel Preview (5)')
foreach ($row in $fuelPreview) {
  $lines.Add('- uniId=' + $row.uni_id + '; gasHign=' + $row.gas_hign + '; gasLow=' + $row.gas_low + '; disl=' + $row.disl + '; lpg=' + $row.lpg + '; updatedAt=' + $row.updated_at)
}

Set-Content -Path $logPath -Value $lines -Encoding UTF8
Write-Host 'Wrote log: ' + $logPath
Write-Host 'Summary: sectors=' + $sectors.Count + ', fullySuccess=' + $sectorSuccessCount + ', stationRows=' + $totalStations
Write-Host 'DB delta: gas_station ' + $before.gas_station_count + ' -> ' + $after.gas_station_count + ', fuel ' + $before.fuel_count + ' -> ' + $after.fuel_count
