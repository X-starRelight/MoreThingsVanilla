param(
	[string]$MergedJar = "$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\minecraft-merged.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
# 用途：生成 79 个战利品表 JSON（39 半砖 + 40 楼梯）与 8 个方块标签 JSON，全部逐字落盘。
# 格式：战利品表沿用 jar 模板的 2 空格缩进；标签由 Write-Tag 生成，使用 TAB 缩进。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ns = 'more-things-vanilla'
$root = Join-Path $ProjectRoot 'src\main\resources'

# id 表与 gen-block-assets.ps1 共享：直接枚举已生成的 blockstate 文件名
$ids = @(Get-ChildItem (Join-Path $root "assets\$ns\blockstates") -Filter *.json | ForEach-Object BaseName)
if ($ids.Count -ne 79) { throw "blockstate ids $($ids.Count) != 79" }
$slabs  = @($ids | Where-Object { $_ -like '*_slab' })
$stairs = @($ids | Where-Object { $_ -like '*_stairs' })
if ($slabs.Count -ne 39 -or $stairs.Count -ne 40) { throw "slab=$($slabs.Count) stair=$($stairs.Count) != 39/40" }

$zip = [System.IO.Compression.ZipFile]::OpenRead($MergedJar)
try {
	function Read-JarEntry([string]$name) {
		$e = $zip.Entries | Where-Object FullName -eq $name
		if (-not $e) { throw "template missing in jar: $name" }
		$sr = New-Object System.IO.StreamReader($e.Open())
		try { return $sr.ReadToEnd() } finally { $sr.Close() }
	}
	function Write-Utf8Json([string]$path, [string]$text) {
		[void](ConvertFrom-Json $text)   # 结构自校验
		New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
		[System.IO.File]::WriteAllText($path, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
	}

	# ---- 战利品表：只能用 26.2 模板（26.4 是 modifier/state 新格式，26.2 不识别）----
	# 26.2 slab 模板内引用：'"minecraft:smooth_stone_slab"'（block 与 name 两处）+ '"minecraft:blocks/smooth_stone_slab"'（random_sequence）
	# 26.2 stairs 模板内引用：'"minecraft:stone_stairs"' + '"minecraft:blocks/stone_stairs"'
	$slabTpl   = Read-JarEntry 'data/minecraft/loot_table/blocks/smooth_stone_slab.json'
	$stairsTpl = Read-JarEntry 'data/minecraft/loot_table/blocks/stone_stairs.json'
	$lootDir = Join-Path $root "data\$ns\loot_table\blocks"
	foreach ($id in $slabs) {
		$t = $slabTpl.Replace('"minecraft:blocks/smooth_stone_slab"', '"' + $ns + ':blocks/' + $id + '"')
		$t = $t.Replace('"minecraft:smooth_stone_slab"', '"' + $ns + ':' + $id + '"')
		Write-Utf8Json (Join-Path $lootDir "$id.json") $t
	}
	foreach ($id in $stairs) {
		$t = $stairsTpl.Replace('"minecraft:blocks/stone_stairs"', '"' + $ns + ':blocks/' + $id + '"')
		$t = $t.Replace('"minecraft:stone_stairs"', '"' + $ns + ':' + $id + '"')
		Write-Utf8Json (Join-Path $lootDir "$id.json") $t
	}
	if ((Get-ChildItem $lootDir -Filter *.json).Count -ne 79) { throw 'loot count != 79' }

	# ---- 标签（$zip 保持打开，下面要读 jar 内原版标签）----
	function Write-Tag([string]$path, [string[]]$values) {
		$body = ($values | ForEach-Object { "`t`t`"" + $ns + ':' + $_ + '"' }) -join ",`n"
		$text = "{`n`t`"replace`": false,`n`t`"values`": [`n" + $body + "`n`t]`n}"
		Write-Utf8Json $path $text
	}
	function Get-BaseId([string]$id) {
		if ($id.EndsWith('_slab')) { return $id.Substring(0, $id.Length - 5) }
		if ($id.EndsWith('_stairs')) { return $id.Substring(0, $id.Length - 7) }
		return $id
	}
	$tagDir = Join-Path $root 'data\minecraft\tags\block'
	New-Item -ItemType Directory -Force -Path $tagDir | Out-Null

	# 1) 族标签（用户拍板 A：忠于 26.4——26.4 实测有 wool_slabs/wool_stairs/concrete_slabs/concrete_stairs 四个家族标签，
	#    成员只含对应 *_slab/*_stairs，不进 wool/concrete 原标签）
	$familyTags = [ordered]@{
		'wool_slabs'     = @($ids | Where-Object { $_ -like '*_wool_slab' })
		'wool_stairs'    = @($ids | Where-Object { $_ -like '*_wool_stairs' })
		'concrete_slabs' = @($ids | Where-Object { $_ -like '*_concrete_slab' })
		'concrete_stairs'= @($ids | Where-Object { $_ -like '*_concrete_stairs' })
	}
	foreach ($name in $familyTags.Keys) {
		$members = $familyTags[$name]
		if ($members.Count -ne 16) { throw "family tag $name has $($members.Count) != 16" }
		Write-Tag (Join-Path $tagDir "$name.json") $members
	}

	# 2) 挖掘/工具标签继承：枚举 jar 内 mineable/*.json 与 needs_*_tool.json，
	#    若基块 id（ours 去掉 _slab/_stairs 后缀）在原版标签值列表中 → 我方同名标签文件加入我方 id。
	#    我方文件只含本 mod id（replace=false 与原版文件自动并集）。
	$vanillaTagFiles = @($zip.Entries | Where-Object {
		$_.FullName -like 'data/minecraft/tags/block/mineable/*.json' -or
		$_.FullName -like 'data/minecraft/tags/block/needs_*_tool.json'
	})
	foreach ($vf in $vanillaTagFiles) {
		$sr = New-Object System.IO.StreamReader($vf.Open())
		try { $vanilla = ConvertFrom-Json $sr.ReadToEnd() } finally { $sr.Close() }
		$baseSet = @{}
		foreach ($v in $vanilla.values) { if ($v -is [string]) { $baseSet[$v] = $true } }
		$rel = $vf.FullName.Replace('data/minecraft/tags/block/', '')   # mineable/pickaxe.json 或 needs_stone_tool.json
		$ours = @($ids | Where-Object { $baseSet.ContainsKey('minecraft:' + (Get-BaseId $_)) })
		if ($ours.Count -eq 0) { continue }
		Write-Tag (Join-Path $tagDir $rel) $ours
		Write-Output ("tag {0}: +{1}" -f $rel, $ours.Count)
	}
}
finally {
	$zip.Dispose()
}

# 3) 打印每个标签文件条数（人工核对）
$tagFiles = @(Get-ChildItem $tagDir -Recurse -Filter *.json)
if ($tagFiles.Count -ne 8) { throw "tag file count $($tagFiles.Count) != 8" }
foreach ($f in $tagFiles) {
	$n = (ConvertFrom-Json (Get-Content $f.FullName -Raw)).values.Count
	Write-Output ("tag {0}: {1}" -f $f.Name, $n)
}
Write-Output 'OK: loot=79 tags written'
