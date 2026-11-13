param(
	[string]$RefJar = "D:\minecraft\.minecraft\versions\26.4-snapshot-6-Fabric 0.20.0\26.4-snapshot-6-Fabric 0.20.0.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ns = 'more-things-vanilla'
$root = Join-Path $ProjectRoot 'src\main\resources'

# id 表与前两个脚本共享：枚举已生成的 blockstate 文件名（79 条）
$ids = @(Get-ChildItem (Join-Path $root "assets\$ns\blockstates") -Filter *.json | ForEach-Object BaseName)
if ($ids.Count -ne 79) { throw "blockstate ids $($ids.Count) != 79" }
$idSet = @{}
foreach ($i in $ids) { $idSet[$i] = $true }

$recipeDir = Join-Path $root "data\$ns\recipe"
New-Item -ItemType Directory -Force -Path $recipeDir | Out-Null

function Write-Utf8Json([string]$path, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	[System.IO.File]::WriteAllText($path, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
}

# ---- 1) 从 26.4 抽取（羊毛/混凝土系的合成+染色+切石机；矿物系/平滑石楼梯 26.4 没有，天然不撞）----
$zip = [System.IO.Compression.ZipFile]::OpenRead($RefJar)
$extracted = 0
foreach ($e in $zip.Entries) {
	if ($e.FullName -notlike 'data/minecraft/recipe/*.json') { continue }
	if ($e.FullName.Split('/').Count -ne 4) { continue }   # 只取直接子文件
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { $t = $sr.ReadToEnd() } finally { $sr.Close() }
	try { $j = ConvertFrom-Json $t } catch { continue }
	$resultId = $null
	if ($j.result -is [string]) { $resultId = $j.result }
	elseif ($j.result -and $j.result.id) { $resultId = $j.result.id }
	if (-not $resultId -or $resultId -notlike 'minecraft:*') { continue }
	$pathPart = ($resultId -split ':', 2)[1]
	if (-not $idSet.ContainsKey($pathPart)) { continue }   # result id 不在 79 表内 → 不是我们的方块
	# 命中：79 个 id 逐个带引号精确替换（基块如 white_wool 不在表内，天然不动）
	foreach ($id in $ids) {
		$t = $t.Replace('"minecraft:' + $id + '"', '"' + $ns + ':' + $id + '"')
	}
	[void](ConvertFrom-Json $t)
	Write-Utf8Json (Join-Path $recipeDir (Split-Path $e.FullName -Leaf)) $t
	$extracted++
}
$zip.Dispose()

# ---- 2) 手写矿物系 + 平滑石楼梯（26.4 没有；格式 = 26.2 实测模板）----
$slabTpl = @'
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": {
		"#": "minecraft:@BASE@"
	},
	"pattern": [
		"###"
	],
	"result": {
		"count": 6,
		"id": "@NS@:@BASE@_slab"
	}
}
'@
$stairsTpl = @'
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": {
		"#": "minecraft:@BASE@"
	},
	"pattern": [
		"#  ",
		"## ",
		"###"
	],
	"result": {
		"count": 4,
		"id": "@NS@:@BASE@_stairs"
	}
}
'@
$minerals = @('iron_block', 'gold_block', 'redstone_block', 'emerald_block', 'lapis_block', 'diamond_block', 'netherite_block')
foreach ($base in $minerals) {
	Write-Utf8Json (Join-Path $recipeDir "$base`_slab.json")   ($slabTpl.Replace('@BASE@', $base).Replace('@NS@', $ns))
	Write-Utf8Json (Join-Path $recipeDir "$base`_stairs.json") ($stairsTpl.Replace('@BASE@', $base).Replace('@NS@', $ns))
}
# 平滑石头楼梯：合成（同原版 stone_stairs）+ 切石机 1:1（设计.md）
$smoothTpl = $stairsTpl.Replace('@BASE@', 'smooth_stone').Replace('@NS@', $ns)
Write-Utf8Json (Join-Path $recipeDir 'smooth_stone_stairs.json') $smoothTpl
$smoothSc = @'
{
	"type": "minecraft:stonecutting",
	"ingredient": "minecraft:smooth_stone",
	"result": {
		"count": 1,
		"id": "@NS@:smooth_stone_stairs"
	}
}
'@
Write-Utf8Json (Join-Path $recipeDir 'smooth_stone_stairs_from_smooth_stone_stonecutting.json') ($smoothSc.Replace('@NS@', $ns))

# ---- 3) 自校验：79 个方块每个都存在 result=我方 id 的配方（可合成性契约）----
$covered = @{}
foreach ($f in Get-ChildItem $recipeDir -Filter *.json) {
	$j = ConvertFrom-Json (Get-Content $f.FullName -Raw)
	$rid = $null
	if ($j.result -is [string]) { $rid = $j.result }
	elseif ($j.result -and $j.result.id) { $rid = $j.result.id }
	if ($rid -and $rid -like "${ns}:*") { $covered[($rid -split ':', 2)[1]] = $true }
}
$missing = @($ids | Where-Object { -not $covered.ContainsKey($_) })
if ($missing.Count -gt 0) { throw "no recipe producing: $($missing -join ', ')" }
$total = (Get-ChildItem $recipeDir -Filter *.json).Count
Write-Output ("OK: manual=16 extracted={0} total={1}" -f $extracted, $total)
