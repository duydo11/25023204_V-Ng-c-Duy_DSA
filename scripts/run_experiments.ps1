# End-to-end experiment runner for Mergesort CUTOFF study
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "=== Step 1: Generating deterministic datasets ===" -ForegroundColor Cyan
python scripts/generate_datasets.py

Write-Host "`n=== Step 2: Compiling Java sources ===" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path build | Out-Null
javac -d build src/*.java

Write-Host "`n=== Step 3: Running correctness & stability tests ===" -ForegroundColor Cyan
java -cp build SortTest

Write-Host "`n=== Step 4: Running empirical benchmarks (JVM -Xms4g -Xmx4g -XX:G1HeapRegionSize=16m) ===" -ForegroundColor Cyan
java -Xms4g -Xmx4g -XX:G1HeapRegionSize=16m -cp build BenchmarkRunner all

Write-Host "`n=== Step 5: Statistical analysis & plotting ===" -ForegroundColor Cyan
python scripts/analyze_and_plot.py

Write-Host "`nAll steps completed successfully! Check results/ and report/REPORT.md." -ForegroundColor Green
