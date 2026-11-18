param(
	[string]$RefJar = "D:\minecraft\.minecraft\versions\26.4-snapshot-6-Fabric 0.20.0\26.4-snapshot-6-Fabric 0.20.0.jar",
	[string]$AssetsIndex = "D:\minecraft\.minecraft\assets\indexes\36.json",
	[string]$AssetsObjects = "D:\minecraft\.minecraft\assets\objects",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
# 用途：生成坐垫（计划 3）全部资源与数据（复刻 26.4）：
#   - 物品定义 16 + 模型 16 + 物品贴图 16 + 实体贴图 16（26.4 jar 抽取，命名空间替换）
#   - 配方 32（shaped：# = 我方羊毛半砖；dye：染料保留原版，坐垫改我方命名空间）
#   - 标签 2（data/minecraft 命名空间与 26.4 平行，计划 2 同模式）
#   - lang 21 键 × 2 语言（assets 索引对象抽取；entity/item 键改命名空间，字幕键保留原样）
#   - ogg 15 + sounds.json 4 键（assets 索引对象抽取；音效事件 id 与原版一致 entity.cushion.*）
# 不生成：配方解锁 advancement（用户拍板：非目标）。
# 格式：抽取文件沿用原版 2 空格缩进；lang / sounds.json 新增部分 TAB 缩进（与现文件一致）。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ns = 'more-things-vanilla'
$root = Join-Path $ProjectRoot 'src\main\resources'
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
$colors = @('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
	'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black')
$soundKeys = @('entity.cushion.break', 'entity.cushion.get_up', 'entity.cushion.place', 'entity.cushion.sit')

function Write-Utf8Json([string]$path, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	[System.IO.File]::WriteAllText($path, $text + "`n", $utf8NoBom)
}

function New-Dir([string]$path) {
	New-Item -ItemType Directory -Force -Path $path | Out-Null
}

# ---- assets 索引对象读取 ----
$index = Get-Content $AssetsIndex -Raw -Encoding UTF8 | ConvertFrom-Json
$objectsRoot = $AssetsObjects

function Get-IndexObjectPath([string]$key) {
	$hash = $index.objects.$key.hash
	if (-not $hash) { throw "index key not found: $key" }
	$p = Join-Path $objectsRoot $hash.Substring(0, 2)
	return Join-Path $p $hash
}

function Copy-FromIndex([string]$key, [string]$dest) {
	New-Dir (Split-Path $dest -Parent)
	[System.IO.File]::Copy((Get-IndexObjectPath $key), $dest, $true)
}

function Get-IndexText([string]$key) {
	return [System.IO.File]::ReadAllText((Get-IndexObjectPath $key), $utf8NoBom)
}

# ---- 26.4 jar 读取 ----
function Get-JarText($zip, [string]$name) {
	$e = $zip.Entries | Where-Object FullName -eq $name
	if (-not $e) { throw "jar entry not found: $name" }
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { return $sr.ReadToEnd() } finally { $sr.Close() }
}

function Copy-JarEntry($zip, [string]$name, [string]$dest) {
	$e = $zip.Entries | Where-Object FullName -eq $name
	if (-not $e) { throw "jar entry not found: $name" }
	New-Dir (Split-Path $dest -Parent)
	$fs = [System.IO.File]::Create($dest)
	$s = $e.Open()
	try { $s.CopyTo($fs) } finally { $s.Close(); $fs.Close() }
}

# 命名空间替换（逐色字面 Replace，带引号锚定避免 blue/light_blue 等误伤）：
#   物品定义/模型："minecraft:item/<c>_cushion" → 我方
#   配方/标签："minecraft:<c>_cushion" → 我方；羊毛半砖 "minecraft:<c>_wool_slab" → 我方
#   染料（minecraft:<c>_dye）与模型 parent（minecraft:item/generated）天然不动
function Convert-CushionNamespace([string]$t) {
	foreach ($c in $colors) {
		$t = $t.Replace('"minecraft:item/' + $c + '_cushion"', '"' + $ns + ':item/' + $c + '_cushion"')
		$t = $t.Replace('"minecraft:' + $c + '_cushion"', '"' + $ns + ':' + $c + '_cushion"')
		$t = $t.Replace('"minecraft:' + $c + '_wool_slab"', '"' + $ns + ':' + $c + '_wool_slab"')
	}
	return $t
}

function Escape-JsonString([string]$s) {
	return $s.Replace('\', '\\').Replace('"', '\"')
}

# ===================== 1) 26.4 jar：物品定义 / 模型 / 贴图 =====================
$itemDir = Join-Path $root "assets\$ns\items"
$modelDir = Join-Path $root "assets\$ns\models\item"
$itemTexDir = Join-Path $root "assets\$ns\textures\item"
$entityTexDir = Join-Path $root "assets\$ns\textures\entity\cushion"
New-Dir $itemDir
New-Dir $modelDir
New-Dir $itemTexDir
New-Dir $entityTexDir

$zip = [System.IO.Compression.ZipFile]::OpenRead($RefJar)
try {
	foreach ($c in $colors) {
		# 物品定义 → assets/<ns>/items/<c>_cushion.json
		$t = Convert-CushionNamespace (Get-JarText $zip "assets/minecraft/items/$c`_cushion.json")
		Write-Utf8Json (Join-Path $itemDir "$c`_cushion.json") $t
		# 模型 → assets/<ns>/models/item/<c>_cushion.json（layer0 改我方贴图）
		$t = Convert-CushionNamespace (Get-JarText $zip "assets/minecraft/models/item/$c`_cushion.json")
		Write-Utf8Json (Join-Path $modelDir "$c`_cushion.json") $t
		# 贴图（二进制）
		Copy-JarEntry $zip "assets/minecraft/textures/item/$c`_cushion.png" (Join-Path $itemTexDir "$c`_cushion.png")
		Copy-JarEntry $zip "assets/minecraft/textures/entity/cushion/$c`_cushion.png" (Join-Path $entityTexDir "$c`_cushion.png")
	}
}
finally {
	$zip.Dispose()
}

# ===================== 2) 26.4 jar：配方 32 + 标签 2 =====================
$recipeDir = Join-Path $root "data\$ns\recipe"
New-Dir $recipeDir

$zip = [System.IO.Compression.ZipFile]::OpenRead($RefJar)
try {
	foreach ($c in $colors) {
		foreach ($name in @("$c`_cushion", "dye_$c`_cushion")) {
			$t = Convert-CushionNamespace (Get-JarText $zip "data/minecraft/recipe/$name.json")
			Write-Utf8Json (Join-Path $recipeDir "$name.json") $t
		}
	}
	# 物品标签（values 改我方 id，文件仍在 data/minecraft 下 = 写入 minecraft:cushions）
	$t = Convert-CushionNamespace (Get-JarText $zip 'data/minecraft/tags/item/cushions.json')
	$tagItemDir = Join-Path $root 'data\minecraft\tags\item'
	New-Dir $tagItemDir
	Write-Utf8Json (Join-Path $tagItemDir 'cushions.json') $t
	# 方块标签（纯原版条目：#minecraft:cauldrons / hopper / composter，原样复制）
	$t = Get-JarText $zip 'data/minecraft/tags/block/cushion_uses_collision_shape.json'
	$tagBlockDir = Join-Path $root 'data\minecraft\tags\block'
	New-Dir $tagBlockDir
	Write-Utf8Json (Join-Path $tagBlockDir 'cushion_uses_collision_shape.json') $t
}
finally {
	$zip.Dispose()
}

# ===================== 3) assets 索引：sounds.json 4 键 + ogg 15 =====================
$soundsObj = Get-IndexText 'minecraft/sounds.json' | ConvertFrom-Json
$parts = @()
foreach ($k in $soundKeys) {
	$entry = $soundsObj.$k
	if (-not $entry) { throw "vanilla sounds.json missing key: $k" }
	$soundList = ($entry.sounds | ForEach-Object { '"' + $_ + '"' }) -join ', '
	$parts += "`t`"$k`": {`n`t`t`"sounds`": [$soundList],`n`t`t`"subtitle`": `"$($entry.subtitle)`"`n`t}"
}
$soundsJson = "{`n" + ($parts -join ",`n") + "`n}`n"
Write-Utf8Json (Join-Path $root "assets\$ns\sounds.json") $soundsJson

$oggKeys = @($index.objects.PSObject.Properties.Name | Where-Object { $_ -like 'minecraft/sounds/entity/cushion/*.ogg' })
if ($oggKeys.Count -ne 15) { throw "ogg keys $($oggKeys.Count) != 15" }
foreach ($key in $oggKeys) {
	$rel = $key -replace '^minecraft/', ''   # sounds/entity/cushion/<name>.ogg
	Copy-FromIndex $key (Join-Path $root "assets\$ns\$rel")
}

# ===================== 4) lang 21 键合并 × 2 语言 =====================
# 21 键 = entity.minecraft.cushion + 16 item.minecraft.*_cushion + 4 subtitles.entity.cushion.*
# 注意：en_us.json 是原版基础语言，打包在版本 jar 而非 assets 索引；zh_cn 等翻译在 assets 索引。
function Get-CushionLang([string]$jsonText, [string]$label) {
	$src = $jsonText | ConvertFrom-Json
	$entries = @()
	foreach ($p in $src.PSObject.Properties) {
		if ($p.Name -notmatch 'cushion') { continue }
		$name = $p.Name
		if ($name -eq 'entity.minecraft.cushion') { $name = "entity.$ns.cushion" }
		elseif ($name.StartsWith('item.minecraft.')) { $name = $name.Replace('item.minecraft.', "item.$ns.") }
		# subtitles.* 键不带命名空间，原样保留（sounds.json 的 subtitle 字段引用同名键）
		$entries += ,@($name, [string]$p.Value)
	}
	if ($entries.Count -ne 21) { throw "$label cushion keys $($entries.Count) != 21" }
	return $entries
}

function Merge-Lang([string]$langPath, $entries) {
	$text = [System.IO.File]::ReadAllText($langPath, $utf8NoBom)
	$existing = $text | ConvertFrom-Json
	$have = @{}
	foreach ($p in $existing.PSObject.Properties) { $have[$p.Name] = $true }
	$toAdd = @()
	foreach ($pair in $entries) {
		if ($have.ContainsKey($pair[0])) { continue }
		$toAdd += ,@($pair[0], $pair[1])
	}
	if ($toAdd.Count -eq 0) { return 0 }
	$trimmed = $text.TrimEnd()
	$lastBrace = $trimmed.LastIndexOf('}')
	if ($lastBrace -lt 0) { throw "lang file has no closing brace: $langPath" }
	$head = $trimmed.Substring(0, $lastBrace).TrimEnd()
	$blockLines = @()
	foreach ($pair in $toAdd) {
		$blockLines += "`t`"" + $pair[0] + "`": `"" + (Escape-JsonString $pair[1]) + "`""
	}
	$newText = $head + ",`n" + ($blockLines -join ",`n") + "`n}"
	if ($text.EndsWith("`n")) { $newText += "`n" }
	[void](ConvertFrom-Json $newText)   # 结构自校验
	[System.IO.File]::WriteAllText($langPath, $newText, $utf8NoBom)
	return $toAdd.Count
}

$langTexts = @{}
$zip = [System.IO.Compression.ZipFile]::OpenRead($RefJar)
try {
	$langTexts['en_us.json'] = Get-JarText $zip 'assets/minecraft/lang/en_us.json'
} finally {
	$zip.Dispose()
}
$langTexts['zh_cn.json'] = Get-IndexText 'minecraft/lang/zh_cn.json'

$added = 0
foreach ($langFile in @('en_us.json', 'zh_cn.json')) {
	$entries = Get-CushionLang $langTexts[$langFile] $langFile
	$langPath = Join-Path $root "assets\$ns\lang\$langFile"
	if (-not (Test-Path $langPath)) { throw "lang file missing: $langPath" }
	$added += Merge-Lang $langPath $entries
}

# ===================== 5) 自校验 =====================
function Assert-Count([string]$what, [int]$actual, [int]$expected) {
	if ($actual -ne $expected) { throw "$what count $actual != $expected" }
}

Assert-Count 'items'        (Get-ChildItem $itemDir -Filter *_cushion.json).Count 16
Assert-Count 'models'       (Get-ChildItem $modelDir -Filter *_cushion.json).Count 16
Assert-Count 'item textures' (Get-ChildItem $itemTexDir -Filter *_cushion.png).Count 16
Assert-Count 'entity textures' (Get-ChildItem $entityTexDir -Filter *_cushion.png).Count 16
$cushionRecipes = @(Get-ChildItem $recipeDir -Filter *cushion*.json)
Assert-Count 'recipes' $cushionRecipes.Count 32
Assert-Count 'ogg' (Get-ChildItem (Join-Path $root "assets\$ns\sounds\entity\cushion") -Filter *.ogg).Count 15

# 配方结果与合成材料必须指向我方命名空间（dye 配方的染料保持原版）
foreach ($c in $colors) {
	$shaped = ConvertFrom-Json (Get-Content (Join-Path $recipeDir "$c`_cushion.json") -Raw -Encoding UTF8)
	if ($shaped.result.id -ne "${ns}:$c`_cushion") { throw "shaped result wrong: $c" }
	if ($shaped.key.'#' -ne "${ns}:$c`_wool_slab") { throw "shaped ingredient wrong: $c" }
	$dye = ConvertFrom-Json (Get-Content (Join-Path $recipeDir "dye_$c`_cushion.json") -Raw -Encoding UTF8)
	if ($dye.result.id -ne "${ns}:$c`_cushion") { throw "dye result wrong: $c" }
}

# sounds.json 4 键齐全
$soundsCheck = ConvertFrom-Json (Get-Content (Join-Path $root "assets\$ns\sounds.json") -Raw -Encoding UTF8)
foreach ($k in $soundKeys) {
	if (-not ($soundsCheck.PSObject.Properties.Name -contains $k)) { throw "sounds.json missing: $k" }
}

# lang 21 键 × 2：全部存在
foreach ($langFile in @('en_us.json', 'zh_cn.json')) {
	$langText = [System.IO.File]::ReadAllText((Join-Path $root "assets\$ns\lang\$langFile"), $utf8NoBom)
	foreach ($pair in (Get-CushionLang $langTexts[$langFile] $langFile)) {
		if (-not $langText.Contains('"' + $pair[0] + '":')) { throw "$langFile missing key: $($pair[0])" }
	}
}

Write-Output ("OK: items=16 models=16 tex=32 recipes=32 tags=2 ogg=15 sounds=4 lang=21x2 (merged {0})" -f $added)
