# 生成 82 个新方块的 blockstate / block model / items 模型定义 / 语言条目。
# 结构模板全部取自本地 26.2 官方 jar（与运行版本格式一致），基块纹理沿用原版。
# 计划 4：竖直红石三件套（blockstate/model/贴图按最终路径脚本生成，贴图为脚本绘制的占位图）。
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

# ---- 构建 82 条目表：id / 基块纹理路径 / kind / zh / en ----
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
# 计划 4：竖直红石三件套（原创；贴图脚本绘制，基块字段仅作文档）
$entries += @{ id = 'vertical_redstone';   base = 'redstone_block'; kind = 'vertical_redstone';   zh = '竖直红石';      en = 'Vertical Redstone' }
$entries += @{ id = 'vertical_repeater';   base = 'repeater';       kind = 'vertical_repeater';   zh = '竖直红石中继器'; en = 'Vertical Redstone Repeater' }
$entries += @{ id = 'vertical_comparator'; base = 'comparator';     kind = 'vertical_comparator'; zh = '竖直红石比较器'; en = 'Vertical Redstone Comparator' }
if ($entries.Count -ne 82) { throw "entry count $($entries.Count) != 82" }

# ---- 从 26.2 jar 抽模板 ----
$zip = [System.IO.Compression.ZipFile]::OpenRead($MergedJar)
try {
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
}
finally {
	$zip.Dispose()
}

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
	} elseif ($e.kind -eq 'vertical_redstone') {
		# 16 个等级模型（六面统一数字贴图）+ power=0..15 的 blockstate 变体 + 物品模型
		$sb = [System.Text.StringBuilder]::new()
		[void]$sb.Append("{`n`t`"variants`": {`n")
		for ($p = 0; $p -le 15; $p++) {
			$comma = if ($p -lt 15) { ',' } else { '' }
			[void]$sb.Append("`t`t`"power=$p`": {`"model`": `"more-things-vanilla:block/${id}_$p`"}$comma`n")
			Write-JsonFile "models\block\${id}_$p.json" ("{`n`t`"parent`": `"minecraft:block/cube_all`",`n`t`"textures`": {`n`t`t`"all`": `"more-things-vanilla:block/${id}_$p`"`n`t}`n}")
		}
		[void]$sb.Append("`t}`n}`n")
		Write-JsonFile "blockstates\$id.json" $sb.ToString()
		Write-JsonFile "items\$id.json" "{`n`t`"model`": {`n`t`t`"type`": `"minecraft:model`",`n`t`t`"model`": `"more-things-vanilla:block/${id}_0`"`n`t}`n}"
		$counts.blockstate++; $counts.model += 16; $counts.item++
	} elseif ($e.kind -eq 'vertical_repeater' -or $e.kind -eq 'vertical_comparator') {
		# 亮/灭两张面模型 + 全属性交叉 blockstate（变体一律不旋转——立方体六面同图）
		Write-JsonFile "models\block\$id.json"    ("{`n`t`"parent`": `"minecraft:block/cube_all`",`n`t`"textures`": {`n`t`t`"all`": `"more-things-vanilla:block/$id`"`n`t}`n}")
		Write-JsonFile "models\block\${id}_on.json" ("{`n`t`"parent`": `"minecraft:block/cube_all`",`n`t`"textures`": {`n`t`t`"all`": `"more-things-vanilla:block/${id}_on`"`n`t}`n}")
		$facings = @('down', 'east', 'north', 'south', 'up', 'west')
		$sb = [System.Text.StringBuilder]::new()
		[void]$sb.Append("{`n`t`"variants`": {`n")
		$keys = @()
		if ($e.kind -eq 'vertical_repeater') {
			foreach ($d in 1..4) { foreach ($f in $facings) { foreach ($l in @($false, $true)) { foreach ($pw in @($false, $true)) {
				$keys += "delay=$d,facing=$f,locked=$(([string]$l).ToLower()),powered=$(([string]$pw).ToLower())"
			} } } }
		} else {
			foreach ($f in $facings) { foreach ($m in @('compare', 'subtract')) { foreach ($pw in @($false, $true)) {
				$keys += "facing=$f,mode=$m,powered=$(([string]$pw).ToLower())"
			} } }
		}
		for ($i = 0; $i -lt $keys.Count; $i++) {
			$comma = if ($i -lt $keys.Count - 1) { ',' } else { '' }
			$model = if ($keys[$i] -match 'powered=true') { "${id}_on" } else { $id }
			[void]$sb.Append("`t`t`"$($keys[$i])`": {`"model`": `"more-things-vanilla:block/$model`"}$comma`n")
		}
		[void]$sb.Append("`t}`n}`n")
		Write-JsonFile "blockstates\$id.json" $sb.ToString()
		Write-JsonFile "items\$id.json" "{`n`t`"model`": {`n`t`t`"type`": `"minecraft:model`",`n`t`t`"model`": `"more-things-vanilla:block/$id`"`n`t}`n}"
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
if ($counts.blockstate -ne 82 -or $counts.model -ne 218 -or $counts.item -ne 82) {
	throw "count mismatch: $($counts | ConvertTo-Json -Compress)"
}

# ---- 计划 4 占位贴图（System.Drawing 脚本绘制，16 张等级数字图 + 中继器/比较器 4 张面图）----
Add-Type -AssemblyName System.Drawing
$blockTexDir = Join-Path $outRoot 'textures\block'
New-Item -ItemType Directory -Force -Path $blockTexDir | Out-Null
function Save-Bitmap([System.Drawing.Bitmap]$bmp, [string]$path) {
	$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}
function Draw-LevelTexture([string]$path, [int]$level) {
	# 底色随电平由暗红渐变到亮红，白色粗体数字居中（每面标注当前能量等级）
	$bmp = New-Object System.Drawing.Bitmap 16, 16
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
	$t = $level / 15.0
	$r = 70 + [int](185 * $t)
	$gr = [int](70 * $t)
	$b = [int](70 * $t)
	$g.Clear([System.Drawing.Color]::FromArgb(255, $r, $gr, $b))
	$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, [Math]::Max(0, $r - 50), 0, 0))
	$g.DrawRectangle($border, 0, 0, 15, 15)
	$border.Dispose()
	$size = 12.0
	$font = New-Object System.Drawing.Font('Consolas', $size, [System.Drawing.FontStyle]::Bold)
	$s = [string]$level
	$sz = $g.MeasureString($s, $font)
	while (($sz.Width -gt 14.0 -or $sz.Height -gt 15.0) -and $size -gt 5.0) {
		$size -= 0.5
		$font.Dispose()
		$font = New-Object System.Drawing.Font('Consolas', $size, [System.Drawing.FontStyle]::Bold)
		$sz = $g.MeasureString($s, $font)
	}
	$g.DrawString($s, $font, [System.Drawing.Brushes]::White, ((16.0 - $sz.Width) / 2.0), ((16.0 - $sz.Height) / 2.0))
	$font.Dispose()
	$g.Dispose()
	Save-Bitmap $bmp $path
}
function Draw-ComponentTexture([string]$path, [System.Drawing.Color]$bg, [bool]$on, [bool]$triangular) {
	# 中继器/比较器顶视占位面图：石/石英底 + 红色导线与火把圆点（点亮时变亮红）
	$wire = if ($on) { [System.Drawing.Color]::FromArgb(255, 255, 60, 60) } else { [System.Drawing.Color]::FromArgb(255, 110, 0, 0) }
	$bmp = New-Object System.Drawing.Bitmap 16, 16
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
	$g.Clear($bg)
	$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 90, 90, 90))
	$g.DrawRectangle($border, 0, 0, 15, 15)
	$border.Dispose()
	$pen = New-Object System.Drawing.Pen ($wire, 2.0)
	if ($triangular) {
		[System.Drawing.PointF[]]$pts = @(
			(New-Object System.Drawing.PointF(8, 4)),
			(New-Object System.Drawing.PointF(4, 12)),
			(New-Object System.Drawing.PointF(12, 12))
		)
		$g.DrawPolygon($pen, $pts)
	} else {
		$g.DrawLine($pen, 8.0, 5.0, 8.0, 12.0)
	}
	$pen.Dispose()
	$brush = New-Object System.Drawing.SolidBrush ($wire)
	$g.FillEllipse($brush, 6, 2, 4, 4)
	$g.FillEllipse($brush, 6, 11, 4, 4)
	$brush.Dispose()
	$g.Dispose()
	Save-Bitmap $bmp $path
}
for ($p = 0; $p -le 15; $p++) {
	Draw-LevelTexture (Join-Path $blockTexDir "vertical_redstone_$p.png") $p
}
$stoneBg = [System.Drawing.Color]::FromArgb(255, 128, 128, 128)
$quartzBg = [System.Drawing.Color]::FromArgb(255, 220, 218, 210)
Draw-ComponentTexture (Join-Path $blockTexDir 'vertical_repeater.png') $stoneBg $false $false
Draw-ComponentTexture (Join-Path $blockTexDir 'vertical_repeater_on.png') $stoneBg $true $false
Draw-ComponentTexture (Join-Path $blockTexDir 'vertical_comparator.png') $quartzBg $false $true
Draw-ComponentTexture (Join-Path $blockTexDir 'vertical_comparator_on.png') $quartzBg $true $true
$blockTexCount = @(Get-ChildItem $blockTexDir -Filter *.png).Count
if ($blockTexCount -ne 20) { throw "block texture count $blockTexCount != 20" }

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
	if ($blockKeys.Count -ne 82) { throw "$lang block entries $($blockKeys.Count) != 82" }
}
Write-Output "OK: blockstates=$($counts.blockstate) models=$($counts.model) items=$($counts.item) textures=20 lang=82x2"
