# 生成 79 个新方块的 blockstate / block model / items 模型定义 / 语言条目。
# 结构模板全部取自本地 26.2 官方 jar（与运行版本格式一致），基块纹理沿用原版。
param(
	[string]$MergedJar = "$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\minecraft-merged.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ns = 'more-things-vanilla'
$colors = [ordered]@{
	black = @('黑色', 'Black'); white = @('白色', 'White'); gray = @('灰色', 'Gray')
	light_gray = @('淡灰色', 'Light Gray'); red = @('红色', 'Red'); orange = @('橙色', 'Orange')
	yellow = @('黄色', 'Yellow'); lime = @('黄绿色', 'Lime'); green = @('绿色', 'Green')
	cyan = @('青色', 'Cyan'); light_blue = @('浅蓝色', 'Light Blue'); blue = @('蓝色', 'Blue')
	purple = @('紫色', 'Purple'); magenta = @('品红色', 'Magenta'); pink = @('粉红色', 'Pink')
	brown = @('棕色', 'Brown')
}
$minerals = [ordered]@{
	iron_block = @('铁块', 'Iron Block'); gold_block = @('金块', 'Gold Block')
	redstone_block = @('红石块', 'Redstone Block'); emerald_block = @('绿宝石块', 'Emerald Block')
	lapis_block = @('青金石块', 'Lapis Block'); diamond_block = @('钻石块', 'Diamond Block')
	netherite_block = @('下界合金块', 'Netherite Block')
}

# ---- 构建 79 条目表：id / 基块纹理路径 / kind / zh / en ----
$entries = @()
foreach ($k in $minerals.Keys) {
	$entries += @{ id = "${k}_slab";     base = $k; kind = 'slab';   zh = "$($minerals[$k][0])半砖"; en = "$($minerals[$k][1]) Slab" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_wool_slab";     base = "${c}_wool";     kind = 'slab';   zh = "$($colors[$c][0])羊毛半砖"; en = "$($colors[$c][1]) Wool Slab" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_concrete_slab"; base = "${c}_concrete"; kind = 'slab';   zh = "$($colors[$c][0])混凝土半砖"; en = "$($colors[$c][1]) Concrete Slab" }
}
$entries += @{ id = 'smooth_stone_stairs'; base = 'smooth_stone'; kind = 'stairs'; zh = '平滑石头楼梯'; en = 'Smooth Stone Stairs' }
foreach ($k in $minerals.Keys) {
	$entries += @{ id = "${k}_stairs";     base = $k; kind = 'stairs'; zh = "$($minerals[$k][0])楼梯"; en = "$($minerals[$k][1]) Stairs" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_wool_stairs";     base = "${c}_wool";     kind = 'stairs'; zh = "$($colors[$c][0])羊毛楼梯"; en = "$($colors[$c][1]) Wool Stairs" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_concrete_stairs"; base = "${c}_concrete"; kind = 'stairs'; zh = "$($colors[$c][0])混凝土楼梯"; en = "$($colors[$c][1]) Concrete Stairs" }
}
if ($entries.Count -ne 79) { throw "entry count $($entries.Count) != 79" }

# ---- 从 26.2 jar 抽模板 ----
$zip = [System.IO.Compression.ZipFile]::OpenRead($MergedJar)
function Read-JarEntry([string]$name) {
	$e = $zip.Entries | Where-Object FullName -eq $name
	if (-not $e) { throw "template missing in jar: $name" }
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { $sr.ReadToEnd() } finally { $sr.Close() }
}
$tplSlabBlockstate = Read-JarEntry 'assets/minecraft/blockstates/smooth_stone_slab.json'
$tplSlabModel      = Read-JarEntry 'assets/minecraft/models/block/smooth_stone_slab.json'
$tplSlabTopModel   = Read-JarEntry 'assets/minecraft/models/block/smooth_stone_slab_top.json'
$tplSlabItem       = Read-JarEntry 'assets/minecraft/items/smooth_stone_slab.json'
$tplStairsBlockstate = Read-JarEntry 'assets/minecraft/blockstates/stone_stairs.json'
$tplStairsModel      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs.json'
$tplStairsInner      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs_inner.json'
$tplStairsOuter      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs_outer.json'
$tplStairsItem       = Read-JarEntry 'assets/minecraft/items/stone_stairs.json'
$zip.Dispose()

# 替换：字面 .Replace（无正则元字符风险），带引号精确匹配，最长优先。
# 模板中模型引用形如 "minecraft:block/smooth_stone_slab_top"（单冒号命名空间），输出 "more-things-vanilla:block/<id>_top"。
function Convert-Template([string]$text, [string]$tplId, [string]$tplBase, [string]$id, [string]$base) {
	$text = $text.Replace('"minecraft:block/' + $tplId + '_top"',   '"more-things-vanilla:block/' + $id + '_top"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '_inner"', '"more-things-vanilla:block/' + $id + '_inner"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '_outer"', '"more-things-vanilla:block/' + $id + '_outer"')
	# 26.2 的 smooth_stone_slab 模板 double 变体为 "minecraft:block/smooth_stone_slab_double"（非计划假设的基块 id），转成基块完整方块模型；slab 模型另有独立 side 纹理 "_slab_side"，一并转成基块纹理。
	$text = $text.Replace('"minecraft:block/' + $tplId + '_double"', '"minecraft:block/' + $base + '"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '_side"',   '"minecraft:block/' + $base + '"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '"',       '"more-things-vanilla:block/' + $id + '"')
	$text = $text.Replace('"minecraft:' + $tplId + '"',             '"more-things-vanilla:' + $id + '"')
	$text = $text.Replace('"minecraft:block/' + $tplBase + '"',     '"minecraft:block/' + $base + '"')
	return $text
}

# ---- 写出 + 自校验 ----
$outRoot = Join-Path (Join-Path $ProjectRoot 'src\main\resources\assets') $ns
$counts = @{ blockstate = 0; model = 0; item = 0 }
function Write-JsonFile([string]$relPath, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	$p = Join-Path $outRoot $relPath
	New-Item -ItemType Directory -Force -Path (Split-Path $p) | Out-Null
	[System.IO.File]::WriteAllText($p, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
}
foreach ($e in $entries) {
	$id = $e.id; $base = $e.base
	if ($e.kind -eq 'slab') {
		Write-JsonFile "blockstates\$id.json" (Convert-Template $tplSlabBlockstate 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "models\block\$id.json" (Convert-Template $tplSlabModel 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "models\block\${id}_top.json" (Convert-Template $tplSlabTopModel 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "items\$id.json" (Convert-Template $tplSlabItem 'smooth_stone_slab' 'smooth_stone' $id $base)
		$counts.blockstate++; $counts.model += 2; $counts.item++
	} else {
		Write-JsonFile "blockstates\$id.json" (Convert-Template $tplStairsBlockstate 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\$id.json" (Convert-Template $tplStairsModel 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_inner.json" (Convert-Template $tplStairsInner 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_outer.json" (Convert-Template $tplStairsOuter 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "items\$id.json" (Convert-Template $tplStairsItem 'stone_stairs' 'stone' $id $base)
		$counts.blockstate++; $counts.model += 3; $counts.item++
	}
}
if ($counts.blockstate -ne 79 -or $counts.model -ne 198 -or $counts.item -ne 79) {
	throw "count mismatch: $($counts | ConvertTo-Json -Compress)"
}

# ---- 语言条目（全量确定性重写，含计划 1 的标签页名称）----
$tabZh = '更多内容-原版'; $tabEn = 'More Things Vanilla'
foreach ($lang in @('zh_cn', 'en_us')) {
	$idx = if ($lang -eq 'zh_cn') { 0 } else { 1 }
	$sb = [System.Text.StringBuilder]::new()
	[void]$sb.Append("{`n`t`"itemGroup.more_things_vanilla`": `"" + $(if ($lang -eq 'zh_cn') { $tabZh } else { $tabEn }) + "`"")
	foreach ($e in $entries) {
		$name = if ($idx -eq 0) { $e.zh } else { $e.en }
		[void]$sb.Append(",`n`t`"block.${ns}.$($e.id)`": `"$name`"")
	}
	[void]$sb.Append("`n}`n")
	$text = $sb.ToString()
	[void](ConvertFrom-Json $text)
	$p = Join-Path $outRoot "lang\$lang.json"
	[System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding $false))
	$parsed = ConvertFrom-Json $text
	$blockKeys = $parsed.PSObject.Properties.Name | Where-Object { $_ -like "block.${ns}.*" }
	if ($blockKeys.Count -ne 79) { throw "$lang block entries $($blockKeys.Count) != 79" }
}
Write-Output "OK: blockstates=$($counts.blockstate) models=$($counts.model) items=$($counts.item) lang=79x2"
