# 生成 83 个新方块的 blockstate / block model / items 模型定义 / 语言条目。
# 结构模板全部取自本地 26.2 官方 jar（与运行版本格式一致），基块纹理沿用原版。
# 计划 4/5：竖直红石三件套 + 单向传递器（blockstate/model/贴图按最终路径脚本生成，贴图为脚本绘制的占位图）。
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

# ---- 构建 83 条目表：id / 基块纹理路径 / kind / zh / en ----
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
# 计划 5：单向传递器（原创）
$entries += @{ id = 'one_way_transmitter'; base = 'repeater'; kind = 'one_way_transmitter'; zh = '单向传递器'; en = 'One Way Transmitter' }
if ($entries.Count -ne 83) { throw "entry count $($entries.Count) != 83" }

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

# 清理历史命名的竖直三件套/单向传递器生成物（旧 *_on 残留会使计数断言失败）
foreach ($sub in @('models\block', 'textures\block')) {
	$dir = Join-Path $outRoot $sub
	if (Test-Path $dir) {
		Get-ChildItem $dir -Filter 'vertical_*.json' -ErrorAction SilentlyContinue | Remove-Item -Force
		Get-ChildItem $dir -Filter 'vertical_*.png' -ErrorAction SilentlyContinue | Remove-Item -Force
		Get-ChildItem $dir -Filter 'one_way_transmitter_*.json' -ErrorAction SilentlyContinue | Remove-Item -Force
		Get-ChildItem $dir -Filter 'one_way_transmitter_*.png' -ErrorAction SilentlyContinue | Remove-Item -Force
	}
}

# ---- 竖直二极管族共享父模型（6 朝向；面贴图变量 side/input/output 由子模型提供）----
# rotation = 贴图在面内按外视屏幕顺时针旋转的度数（由 26.2 FaceBakery/Quadrant 源码推导），
# 使无旋转时指向贴图上方的箭头转到 FACING 方向。
$facings = @('down', 'east', 'north', 'south', 'up', 'west')
$opposites = @{ down = 'up'; up = 'down'; north = 'south'; south = 'north'; east = 'west'; west = 'east' }
$sideRotations = [ordered]@{
	north = @{ west = 270; east = 90; up = 0; down = 180 }
	south = @{ west = 90; east = 270; up = 180; down = 0 }
	east  = @{ north = 270; south = 90; up = 90; down = 90 }
	west  = @{ north = 90; south = 270; up = 270; down = 270 }
	up    = @{ north = 0; south = 0; east = 0; west = 0 }
	down  = @{ north = 180; south = 180; east = 180; west = 180 }
}
$faceOrder = @('down', 'up', 'north', 'east', 'south', 'west')
foreach ($f in $facings) {
	$sb = [System.Text.StringBuilder]::new()
	[void]$sb.Append("{`n`t`"parent`": `"minecraft:block/block`",`n`t`"elements`": [`n`t`t{`n`t`t`t`"from`": [0, 0, 0],`n`t`t`t`"to`": [16, 16, 16],`n`t`t`t`"faces`": {`n")
	for ($i = 0; $i -lt $faceOrder.Count; $i++) {
		$face = $faceOrder[$i]
		$comma = if ($i -lt $faceOrder.Count - 1) { ',' } else { '' }
		if ($face -eq $f) {
			$line = '"' + $face + '": {"texture": "#output", "cullface": "' + $face + '"}'
		} elseif ($face -eq $opposites[$f]) {
			$line = '"' + $face + '": {"texture": "#input", "cullface": "' + $face + '"}'
		} else {
			$line = '"' + $face + '": {"texture": "#side", "cullface": "' + $face + '", "rotation": ' + $sideRotations[$f][$face] + '}'
		}
		[void]$sb.Append("`t`t`t`t" + $line + $comma + "`n")
	}
	[void]$sb.Append("`t`t`t}`n`t`t}`n`t]`n}`n")
	Write-JsonFile "models\block\vertical_diode_facing_$f.json" $sb.ToString()
	$counts.model++
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
		# 状态全区分 + 方向箭头：每个 (状态 × facing) 一个完整立方体模型。
		# 几何与面 rotation 在共享父模型 vertical_diode_facing_<f>，子模型仅提供三张贴图。
		$states = @()
		if ($e.kind -eq 'vertical_repeater') {
			foreach ($d in 1..4) { foreach ($l in @($false, $true)) { foreach ($pw in @($false, $true)) {
				$states += "vertical_repeater_d${d}_$([int]$l)$([int]$pw)"
			} } }
		} else {
			foreach ($m in @('compare', 'subtract')) { foreach ($pw in @($false, $true)) {
				$states += "vertical_comparator_${m}_$([int]$pw)"
			} }
		}
		$sb = [System.Text.StringBuilder]::new()
		[void]$sb.Append("{`n`t`"variants`": {`n")
		$keys = @(); $models = @()
		if ($e.kind -eq 'vertical_repeater') {
			foreach ($d in 1..4) { foreach ($f in $facings) { foreach ($l in @($false, $true)) { foreach ($pw in @($false, $true)) {
				$keys += "delay=$d,facing=$f,locked=$(([string]$l).ToLower()),powered=$(([string]$pw).ToLower())"
				$models += "vertical_repeater_d${d}_$([int]$l)$([int]$pw)_$f"
			} } } }
		} else {
			foreach ($f in $facings) { foreach ($m in @('compare', 'subtract')) { foreach ($pw in @($false, $true)) {
				$keys += "facing=$f,mode=$m,powered=$(([string]$pw).ToLower())"
				$models += "vertical_comparator_${m}_$([int]$pw)_$f"
			} } }
		}
		for ($i = 0; $i -lt $keys.Count; $i++) {
			$comma = if ($i -lt $keys.Count - 1) { ',' } else { '' }
			[void]$sb.Append("`t`t`"$($keys[$i])`": {`"model`": `"more-things-vanilla:block/$($models[$i])`"}$comma`n")
		}
		[void]$sb.Append("`t}`n}`n")
		Write-JsonFile "blockstates\$id.json" $sb.ToString()
		foreach ($state in $states) { foreach ($f in $facings) {
			$json = "{`n`t`"parent`": `"more-things-vanilla:block/vertical_diode_facing_$f`",`n`t`"textures`": {`n`t`t`"side`": `"more-things-vanilla:block/${state}_side`",`n`t`t`"input`": `"more-things-vanilla:block/${state}_in`",`n`t`t`"output`": `"more-things-vanilla:block/${state}_out`",`n`t`t`"particle`": `"#side`"`n`t}`n}"
			Write-JsonFile "models\block\${state}_$f.json" $json
			$counts.model++
		} }
		$defaultModel = if ($e.kind -eq 'vertical_repeater') { 'vertical_repeater_d1_00_north' } else { 'vertical_comparator_compare_0_north' }
		Write-JsonFile "items\$id.json" "{`n`t`"model`": {`n`t`t`"type`": `"minecraft:model`",`n`t`t`"model`": `"more-things-vanilla:block/$defaultModel`"`n`t}`n}"
		$counts.blockstate++; $counts.item++
	} elseif ($e.kind -eq 'one_way_transmitter') {
		# off：六面中性；power>0：按 INPUT 方向复用共享父模型 vertical_diode_facing_<opposite(INPUT)>。
		# 输入面 = #input（空心箭头），INPUT 对面 = #output（实心箭头），其余 4 面 = #side（点亮线）。
		$offJson = "{`n`t`"parent`": `"minecraft:block/cube_all`",`n`t`"textures`": {`n`t`t`"all`": `"more-things-vanilla:block/${id}_off`"`n`t}`n}"
		Write-JsonFile "models\block\${id}_off.json" $offJson
		$counts.model++
		foreach ($input in $facings) {
			$parent = "vertical_diode_facing_" + $opposites[$input]
			$json = "{`n`t`"parent`": `"more-things-vanilla:block/$parent`",`n`t`"textures`": {`n`t`t`"input`": `"more-things-vanilla:block/${id}_in`",`n`t`t`"output`": `"more-things-vanilla:block/${id}_out`",`n`t`t`"side`": `"more-things-vanilla:block/${id}_side`",`n`t`t`"particle`": `"#side`"`n`t}`n}"
			Write-JsonFile "models\block\${id}_in_$input.json" $json
			$counts.model++
		}
		$sb = [System.Text.StringBuilder]::new()
		[void]$sb.Append("{`n`t`"variants`": {`n")
		$keys = @(); $models = @()
		foreach ($input in $facings) {
			$keys += "input=$input,power=0"
			$models += "${id}_off"
			foreach ($p in 1..15) {
				$keys += "input=$input,power=$p"
				$models += "${id}_in_$input"
			}
		}
		for ($i = 0; $i -lt $keys.Count; $i++) {
			$comma = if ($i -lt $keys.Count - 1) { ',' } else { '' }
			[void]$sb.Append("`t`t`"$($keys[$i])`": {`"model`": `"more-things-vanilla:block/$($models[$i])`"}$comma`n")
		}
		[void]$sb.Append("`t}`n}`n")
		Write-JsonFile "blockstates\$id.json" $sb.ToString()
		Write-JsonFile "items\$id.json" "{`n`t`"model`": {`n`t`t`"type`": `"minecraft:model`",`n`t`t`"model`": `"more-things-vanilla:block/${id}_off`"`n`t}`n}"
		$counts.blockstate++; $counts.item++
	} else {
		Write-JsonFile "blockstates\$id.json" (Convert-Template $tplStairsBlockstate 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\$id.json" (Convert-Template $tplStairsModel 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_inner.json" (Convert-Template $tplStairsInner 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_outer.json" (Convert-Template $tplStairsOuter 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "items\$id.json" (Convert-Template $tplStairsItem 'stone_stairs' 'stone' $id $base)
		$counts.blockstate++; $counts.model += 3; $counts.item++
	}
}
if ($counts.blockstate -ne 83 -or $counts.model -ne 347 -or $counts.item -ne 83) {
	throw "count mismatch: $($counts | ConvertTo-Json -Compress)"
}

# ---- 计划 4 占位贴图（System.Drawing 绘制：16 张等级数字图 + 中继器 16 状态×3 面 + 比较器 4 状态×3 面，状态全区分+方向箭头）----
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
function Draw-FitLabel([System.Drawing.Graphics]$g, [string]$text, [double]$cx, [double]$top, [double]$maxW, [double]$maxH) {
	$size = 9.0
	$font = New-Object System.Drawing.Font('Consolas', $size, [System.Drawing.FontStyle]::Bold)
	$sz = $g.MeasureString($text, $font)
	while (($sz.Width -gt $maxW -or $sz.Height -gt $maxH) -and $size -gt 4.0) {
		$size -= 0.5
		$font.Dispose()
		$font = New-Object System.Drawing.Font('Consolas', $size, [System.Drawing.FontStyle]::Bold)
		$sz = $g.MeasureString($text, $font)
	}
	$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 25, 25, 25))
	$g.DrawString($text, $font, $brush, ($cx - $sz.Width / 2.0), ($top + ($maxH - $sz.Height) / 2.0))
	$brush.Dispose()
	$font.Dispose()
}
function Get-WireColor([bool]$on) {
	if ($on) { return [System.Drawing.Color]::FromArgb(255, 255, 60, 60) }
	return [System.Drawing.Color]::FromArgb(255, 110, 0, 0)
}
function Draw-DiodeSideTexture([string]$path, [System.Drawing.Color]$bg, [bool]$on, [string]$label, [int]$pips, [bool]$locked) {
	# 侧面：状态区（中继器=delay 圆点+锁定横条；比较器=模式文字）+ 朝上的方向箭头（点亮变亮红）
	$wire = Get-WireColor $on
	$bmp = New-Object System.Drawing.Bitmap 16, 16
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
	$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
	$g.Clear($bg)
	$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 90, 90, 90))
	$g.DrawRectangle($border, 0, 0, 15, 15)
	$border.Dispose()
	if ($label -ne '') {
		Draw-FitLabel $g $label 8.0 0.0 14.0 7.0
	} elseif ($pips -gt 0) {
		$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 35, 35, 35))
		$w = $pips * 3 - 1
		$x0 = [Math]::Floor((16 - $w) / 2.0)
		for ($i = 0; $i -lt $pips; $i++) { $g.FillRectangle($brush, ($x0 + $i * 3), 1, 2, 2) }
		$brush.Dispose()
	}
	$arrowTop = if ($label -ne '') { 8.0 } else { 5.0 }
	$arrowBase = if ($label -ne '') { 14.0 } else { 12.0 }
	$halfW = if ($label -ne '') { 3.0 } else { 4.0 }
	$brush = New-Object System.Drawing.SolidBrush ($wire)
	[System.Drawing.PointF[]]$arrow = @(
		([System.Drawing.PointF]::new(8.0, $arrowTop)),
		([System.Drawing.PointF]::new(8.0 - $halfW, $arrowBase)),
		([System.Drawing.PointF]::new(8.0 + $halfW, $arrowBase))
	)
	$g.FillPolygon($brush, $arrow)
	$brush.Dispose()
	if ($locked) {
		$bar = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 35, 35, 35))
		$g.FillRectangle($bar, 1, 14, 14, 2)
		$bar.Dispose()
	}
	$g.Dispose()
	Save-Bitmap $bmp $path
}
function Draw-DiodeEndTexture([string]$path, [System.Drawing.Color]$bg, [bool]$on, [string]$label, [bool]$isOutput) {
	# 输入/输出面：顶部状态文字 + 底部方向标记（输出=实心朝上箭头，输入=空心朝下箭头）
	$wire = Get-WireColor $on
	$bmp = New-Object System.Drawing.Bitmap 16, 16
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
	$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
	$g.Clear($bg)
	$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 90, 90, 90))
	$g.DrawRectangle($border, 0, 0, 15, 15)
	$border.Dispose()
	Draw-FitLabel $g $label 8.0 0.0 14.0 7.0
	if ($isOutput) {
		$brush = New-Object System.Drawing.SolidBrush ($wire)
		[System.Drawing.PointF[]]$arrow = @(
			([System.Drawing.PointF]::new(8.0, 8.0)),
			([System.Drawing.PointF]::new(4.0, 14.0)),
			([System.Drawing.PointF]::new(12.0, 14.0))
		)
		$g.FillPolygon($brush, $arrow)
		$brush.Dispose()
	} else {
		$pen = New-Object System.Drawing.Pen ($wire, 2.0)
		[System.Drawing.PointF[]]$arrow = @(
			([System.Drawing.PointF]::new(8.0, 14.0)),
			([System.Drawing.PointF]::new(4.0, 8.0)),
			([System.Drawing.PointF]::new(12.0, 8.0))
		)
		$g.DrawPolygon($pen, $arrow)
		$pen.Dispose()
	}
	$g.Dispose()
	Save-Bitmap $bmp $path
}
function Draw-TransmitterLineTexture([string]$path, [System.Drawing.Color]$bg, [bool]$on) {
	# 单向传递器的中性/侧面：石质底 + 横向红石线（点亮=亮红，熄灭=暗红）
	$wire = Get-WireColor $on
	$bmp = New-Object System.Drawing.Bitmap 16, 16
	$g = [System.Drawing.Graphics]::FromImage($bmp)
	$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
	$g.Clear($bg)
	$border = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 80, 80, 88))
	$g.DrawRectangle($border, 0, 0, 15, 15)
	$border.Dispose()
	$brush = New-Object System.Drawing.SolidBrush ($wire)
	$g.FillRectangle($brush, 2, 7, 12, 2)
	$g.FillRectangle($brush, 6, 4, 4, 8)
	$brush.Dispose()
	$g.Dispose()
	Save-Bitmap $bmp $path
}
for ($p = 0; $p -le 15; $p++) {
	Draw-LevelTexture (Join-Path $blockTexDir "vertical_redstone_$p.png") $p
}
$stoneBg = [System.Drawing.Color]::FromArgb(255, 128, 128, 128)
$quartzBg = [System.Drawing.Color]::FromArgb(255, 220, 218, 210)
foreach ($d in 1..4) { foreach ($l in @($false, $true)) { foreach ($pw in @($false, $true)) {
	$state = "vertical_repeater_d${d}_$([int]$l)$([int]$pw)"
	$label = "$d" + $(if ($l) { 'L' } else { '' })
	Draw-DiodeSideTexture (Join-Path $blockTexDir "$($state)_side.png") $stoneBg $pw '' $d $l
	Draw-DiodeEndTexture (Join-Path $blockTexDir "$($state)_in.png") $stoneBg $pw $label $false
	Draw-DiodeEndTexture (Join-Path $blockTexDir "$($state)_out.png") $stoneBg $pw $label $true
} } }
foreach ($m in @('compare', 'subtract')) { foreach ($pw in @($false, $true)) {
	$state = "vertical_comparator_${m}_$([int]$pw)"
	$label = if ($m -eq 'compare') { 'C' } else { 'S' }
	Draw-DiodeSideTexture (Join-Path $blockTexDir "$($state)_side.png") $quartzBg $pw $label 0 $false
	Draw-DiodeEndTexture (Join-Path $blockTexDir "$($state)_in.png") $quartzBg $pw $label $false
	Draw-DiodeEndTexture (Join-Path $blockTexDir "$($state)_out.png") $quartzBg $pw $label $true
} }
# 计划 5：单向传递器 4 张贴图（off 中性 / side 侧面点亮 / in 空心箭头 / out 实心箭头）
$transmitterBg = [System.Drawing.Color]::FromArgb(255, 116, 118, 126)
Draw-TransmitterLineTexture (Join-Path $blockTexDir 'one_way_transmitter_off.png') $transmitterBg $false
Draw-TransmitterLineTexture (Join-Path $blockTexDir 'one_way_transmitter_side.png') $transmitterBg $true
Draw-DiodeEndTexture (Join-Path $blockTexDir 'one_way_transmitter_in.png') $transmitterBg $true '' $false
Draw-DiodeEndTexture (Join-Path $blockTexDir 'one_way_transmitter_out.png') $transmitterBg $true '' $true
$blockTexCount = @(Get-ChildItem $blockTexDir -Filter *.png).Count
if ($blockTexCount -ne 80) { throw "block texture count $blockTexCount != 80" }

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
	if ($blockKeys.Count -ne 83) { throw "$lang block entries $($blockKeys.Count) != 83" }
}
Write-Output "OK: blockstates=$($counts.blockstate) models=$($counts.model) items=$($counts.item) textures=$blockTexCount lang=83x2"
