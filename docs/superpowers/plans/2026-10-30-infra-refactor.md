# MoreThingsVanilla 计划 1：模板改造与注册基础设施 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Fabric 示例模板改造为 MoreThingsVanilla 正式工程（modid/包名/资源全面改名），建立 gametest 测试基建，并注册独立创造标签页。

**Architecture:** 一次性完成命名改造（由编译验证），随后用「失败的 gametest → 实现 → 通过的 gametest」红绿循环落地创造标签页。测试用 fabric-gametest-api-v1 注解式 API，通过 loom 自定义 `gametest` 运行配置（server 模式 + `fabric-api.gametest` 系统属性）无头执行，结果写入 JUnit 风格 XML 报告作为判定依据。

**Tech Stack:** Java 25、Mojang 官方映射（`net.minecraft.world.level.*` 包布局 + `Identifier` 类名）、fabric-loom 1.18、fabric-api 0.161.0+26.2、fabric-creative-tab-api-v1、fabric-gametest-api-v1、Git。

**范围说明（相对 设计.md 实现顺序第 1 步）：** 设计里的「方块注册框架」不在本计划内——注册辅助方法将随计划 2 的第一个方块一起落地（与其 gametest 一起做红绿循环），避免产生没有调用者的死代码。本计划交付：命名改造、gametest 基建、创造标签页 + 语言文件骨架。

**工作分支：** Task 1 Step 4 创建 `feature/infra-refactor`，全部实现任务在该分支提交，完成后合回 `main`（用户已确认「原地功能分支」方案）。

---

## 已验证的环境事实（写入代码前已用 javap/源码核实，可直接信任）

- 基线 `.\gradlew.bat compileJava` 通过（EXIT=0）；项目**不是** git 仓库；`git` 命令可用。
- `run/` 目录存在但为空；`.gitignore` 已忽略 `.gradle/`、`build/`、`run/`。
- 注册 API：`Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey<V>, T)`；`BuiltInRegistries.CREATIVE_MODE_TAB / BLOCK / ITEM` 均存在；`Registries.XXX` 是 ResourceKey（用于 `ResourceKey.create`）。
- `ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier)`；`Identifier.fromNamespaceAndPath(ns, path)`。
- 查找：`Registry.containsKey(ResourceKey<T>)`、`getOptional(ResourceKey<T>) → Optional<T>`。
- 标签页：`FabricCreativeModeTab.builder()`（`net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab`，官方 javadoc 示例即本计划写法），Builder 方法 `.title(Component)` / `.icon(Supplier<ItemStack>)` / `.displayItems(DisplayItemsGenerator)` / `.build()`；`DisplayItemsGenerator.accept(ItemDisplayParameters, Output)`；`Output` 有 `accept(ItemLike)` 重载。
- gametest：注解 `net.fabricmc.fabric.api.gametest.v1.GameTest`；方法须 **public、非 static、void、单参数 `GameTestHelper`**；fabric.mod.json entrypoint 键为 `"fabric-gametest"`（任意实例类）；测试 ID 自动为 `<modid>:<类名snake>_<方法名snake>`；默认结构 `fabric-gametest-api-v1:empty`。
- `GameTestHelper`：`assertTrue(boolean, String)`、`succeed()`、`fail(String)`、`setBlock`、`assertBlockPresent` 等均存在。
- 启动机制：系统属性 `fabric-api.gametest`（值任意）使 server run 变为无头测试服务器；`fabric-api.gametest.report-file` 指定 JUnit 风格 XML 报告路径（reporter 会自动创建父目录）。
- loom 1.18.4 `RunConfigSettings`：有 `server()`、`property(String, String)`、`runDir(String)`；在 `loom.runs` 容器新增命名配置会自动生成 `run<配置名>` 任务（即 `runGametest`）。当前项目只有 `runClient`/`runServer`，`runGametest` 尚不存在（已核实）。
- 模板 mixin 目标方法在 26.2 仍存在：`MinecraftServer.loadLevel()`（protected 无参）、`Minecraft.run()`（public 无参）——模板的 `method = "loadLevel"` / `method = "run"` 字符串可直接沿用。
- `gradle.properties` 已是 Fabric API `0.161.0+26.2`、loader `0.19.5`；`fabric.mod.json` depends 为 `fabricloader >=0.19.5`、`minecraft ~26.2`（保持不变）。

## 文件结构（本计划涉及的全部文件）

```
创建: src/main/java/com/tt23xrstudio/MoreThingsVanilla.java        （主初始化器 + 创造标签页）
创建: src/main/java/com/tt23xrstudio/mixin/MtvMixin.java
创建: src/main/java/com/tt23xrstudio/test/MtvGameTests.java        （gametest 入口类）
创建: src/client/java/com/tt23xrstudio/client/MoreThingsVanillaClient.java
创建: src/client/java/com/tt23xrstudio/client/mixin/MtvClientMixin.java
创建: src/main/resources/more_things_vanilla.mixins.json
创建: src/client/resources/more_things_vanilla.client.mixins.json
创建: src/main/resources/assets/more-things-vanilla/lang/en_us.json
创建: src/main/resources/assets/more-things-vanilla/lang/zh_cn.json
移动: src/main/resources/assets/modid/icon.png → assets/more-things-vanilla/icon.png
删除: src/main/java/com/example/、src/client/java/com/example/
删除: src/main/resources/modid.mixins.json、src/client/resources/modid.client.mixins.json
修改: settings.gradle（rootProject.name）
修改: gradle.properties（group）
修改: build.gradle（mods 键名 + gametest 运行配置）
修改: src/main/resources/fabric.mod.json（id/name/entrypoints/mixins/icon 全面改名）
修改: run/gametest/eula.txt（新建，被 .gitignore 忽略）
```

---

### Task 1: git 初始化与模板基线提交

**Files:** 无源码改动（新建 `.git/`）

- [ ] **Step 1: 初始化仓库并命名主分支**

```powershell
git init
git branch -M main
```

Expected: 无报错（git 已确认可用；若提示 already a git repository 则继续）

- [ ] **Step 2: 提交模板基线**

```powershell
git add -A
git commit -m "chore: fabric example template baseline"
```

Expected: 提交成功，输出含文件变更统计（.gitignore 已排除 `.gradle/`、`build/`、`run/`，不应出现在提交里）

- [ ] **Step 3: 验证仓库状态**

```powershell
git status --short
git log --oneline
```

Expected: status 无输出（干净）；log 显示 1 条提交 `chore: fabric example template baseline`

- [ ] **Step 4: 创建功能分支（controller 追加的 workspace 设置）**

```powershell
git checkout -b feature/infra-refactor
```

Expected: 已切换到 `feature/infra-refactor` 分支；baseline 提交留在 `main`，后续全部任务的提交都落在本分支。

---

### Task 2: 全面改名（modid → more-things-vanilla，com.example → com.tt23xrstudio）

**Files:**
- Modify: `gradle.properties:14`（group 行）
- Modify: `settings.gradle`（rootProject.name）
- Modify: `build.gradle:18`（mods 键）
- Modify: `src/main/resources/fabric.mod.json`（全文重写）
- Create: 上方文件结构清单中的 6 个新 Java/JSON 文件
- Move/Delete: 上方文件结构清单中的移动与删除项

- [ ] **Step 1: 改 gradle 三处命名**

`gradle.properties`：

```properties
group=com.tt23xrstudio
```

`settings.gradle`：

```groovy
rootProject.name = 'more-things-vanilla'
```

`build.gradle` 的 `loom.mods` 块：

```groovy
	mods {
		"more-things-vanilla" {
			sourceSet sourceSets.main
			sourceSet sourceSets.client
		}
	}
```

- [ ] **Step 2: 目录搬迁与旧文件清理（一条命令序列）**

```powershell
New-Item -ItemType Directory -Force -Path src\main\java\com\tt23xrstudio\mixin, src\main\java\com\tt23xrstudio\test, src\client\java\com\tt23xrstudio\client\mixin, src\main\resources\assets\more-things-vanilla | Out-Null
Move-Item src\main\resources\assets\modid\icon.png src\main\resources\assets\more-things-vanilla\icon.png
Remove-Item -Recurse -Force src\main\java\com\example, src\client\java\com\example
Remove-Item -Recurse -Force src\main\resources\assets\modid
Remove-Item src\main\resources\modid.mixins.json, src\client\resources\modid.client.mixins.json
```

Expected: 无报错

- [ ] **Step 3: 写主初始化器（此阶段不含创造标签页）**

`src/main/java/com/tt23xrstudio/MoreThingsVanilla.java`：

```java
package com.tt23xrstudio;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoreThingsVanilla implements ModInitializer {
	public static final String MOD_ID = "more-things-vanilla";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("MoreThingsVanilla loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
```

- [ ] **Step 4: 写客户端入口与两个 mixin（内容来自模板，仅改包名/类名）**

`src/client/java/com/tt23xrstudio/client/MoreThingsVanillaClient.java`：

```java
package com.tt23xrstudio.client;

import net.fabricmc.api.ClientModInitializer;

public final class MoreThingsVanillaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 客户端逻辑（渲染、模型等）放这里
	}
}
```

`src/main/java/com/tt23xrstudio/mixin/MtvMixin.java`：

```java
package com.tt23xrstudio.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MtvMixin {
	@Inject(at = @At("HEAD"), method = "loadLevel")
	private void init(CallbackInfo info) {
		// Injected into the head of MinecraftServer.loadLevel()
	}
}
```

`src/client/java/com/tt23xrstudio/client/mixin/MtvClientMixin.java`：

```java
package com.tt23xrstudio.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MtvClientMixin {
	@Inject(at = @At("HEAD"), method = "run")
	private void init(CallbackInfo info) {
		// Injected into the head of Minecraft.run()
	}
}
```

- [ ] **Step 5: 写两个 mixin 配置（改包名与类名）**

`src/main/resources/more_things_vanilla.mixins.json`：

```json
{
	"required": true,
	"package": "com.tt23xrstudio.mixin",
	"compatibilityLevel": "JAVA_25",
	"mixins": [
		"MtvMixin"
	],
	"injectors": {
		"defaultRequire": 1
	},
	"overwrites": {
		"requireAnnotations": true
	}
}
```

`src/client/resources/more_things_vanilla.client.mixins.json`：

```json
{
	"required": true,
	"package": "com.tt23xrstudio.client.mixin",
	"compatibilityLevel": "JAVA_25",
	"client": [
		"MtvClientMixin"
	],
	"injectors": {
		"defaultRequire": 1
	},
	"overwrites": {
		"requireAnnotations": true
	}
}
```

- [ ] **Step 6: 重写 fabric.mod.json**

`src/main/resources/fabric.mod.json`（注意保留 `${version}` 占位符供 processResources 展开；本任务不加 fabric-gametest 入口，Task 3 加）：

```json
{
	"schemaVersion": 1,
	"id": "more-things-vanilla",
	"version": "${version}",
	"name": "MoreThingsVanilla",
	"description": "Adds vertical redstone components, a seat cushion, and the missing slabs and stairs.",
	"authors": [
		"tt23xrstudio"
	],
	"license": "CC0-1.0",
	"icon": "assets/more-things-vanilla/icon.png",
	"environment": "*",
	"entrypoints": {
		"main": [
			"com.tt23xrstudio.MoreThingsVanilla"
		],
		"client": [
			"com.tt23xrstudio.client.MoreThingsVanillaClient"
		]
	},
	"mixins": [
		"more_things_vanilla.mixins.json",
		{
			"config": "more_things_vanilla.client.mixins.json",
			"environment": "client"
		}
	],
	"depends": {
		"fabricloader": ">=0.19.5",
		"minecraft": "~26.2",
		"java": ">=25",
		"fabric-api": "*"
	}
}
```

- [ ] **Step 7: 全量构建验证改名无破坏**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`（若失败：先看编译错误是否指向残留的 `com.example` 引用或漏改的资源路径，逐个修正后重跑）

- [ ] **Step 8: 提交改名**

```powershell
git add -A
git commit -m "refactor: rename modid to more-things-vanilla, package to com.tt23xrstudio"
git status --short
```

Expected: 提交成功；status 干净

---

### Task 3: gametest 测试基建 + 第一个失败测试（红）

**Files:**
- Modify: `build.gradle`（`loom` 块加 `runs` 配置）
- Modify: `src/main/resources/fabric.mod.json`（entrypoints 加 `fabric-gametest`）
- Create: `run/gametest/eula.txt`（本地运行文件，不入库）
- Create: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`

- [ ] **Step 1: build.gradle 增加 gametest 运行配置**

在 `build.gradle` 的 `loom { ... }` 块内（`mods { ... }` 之后）加入：

```groovy
	runs {
		gametest {
			server()
			property "fabric-api.gametest", "true"
			property "fabric-api.gametest.report-file", "${projectDir}/build/gametest/report.xml"
			runDir "run/gametest"
		}
	}
```

- [ ] **Step 2: 准备测试运行目录与 EULA**

```powershell
New-Item -ItemType Directory -Force -Path run\gametest | Out-Null
Set-Content run\gametest\eula.txt 'eula=true'
```

Expected: 无报错（`run/` 被 .gitignore 忽略，不会入库；GameTestServer 可能不需要 eula，写上无害）

- [ ] **Step 3: fabric.mod.json 加 gametest 入口**

`entrypoints` 块改为：

```json
	"entrypoints": {
		"main": [
			"com.tt23xrstudio.MoreThingsVanilla"
		],
		"client": [
			"com.tt23xrstudio.client.MoreThingsVanillaClient"
		],
		"fabric-gametest": [
			"com.tt23xrstudio.test.MtvGameTests"
		]
	},
```

- [ ] **Step 4: 写失败测试（此刻标签页尚未注册 → 运行时红）**

`src/main/java/com/tt23xrstudio/test/MtvGameTests.java`：

```java
package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

public final class MtvGameTests {

	@GameTest
	public void creativeTabRegistered(GameTestHelper helper) {
		ResourceKey<CreativeModeTab> key = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB,
				MoreThingsVanilla.id("more_things_vanilla")
		);
		helper.assertTrue(
				BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(key),
				"creative tab not registered: " + key
		);
		helper.succeed();
	}
}
```

- [ ] **Step 5: 先编译，确认能过（红是运行时红，不是编译错误）**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 运行 gametest，确认红**

```powershell
.\gradlew.bat runGametest *> build\gametest-run.log
$exit = $LASTEXITCODE
$r = Get-Content build\gametest\report.xml -Raw -ErrorAction SilentlyContinue
if (-not $r) { Get-Content build\gametest-run.log -Tail 60; throw '报告未生成，见 build\gametest-run.log 尾部输出' }
if ($r -notmatch 'mtv_game_tests_creative_tab_registered') { Write-Output $r; throw '测试未被发现' }
if ($r -notmatch '<failure') { Write-Output $r; throw 'RED 未达成：报告中没有失败记录' }
Write-Output "RED confirmed (gradle exit=$exit)"
```

注意（Task 3 实测修正）：报告 XML 的实际格式是 `<testcase>...<failure message="..."/></testcase>` 元素，**没有** `failures="N"` 属性——判定必须检查 `<failure>` 元素是否存在。

Expected: 输出 `RED confirmed`（gradle 退出码可能是 0 也可能是 1，以报告为准；报告缺失或无失败都算基建问题，先排查再继续。首次运行需生成测试世界，可能耗时 1-3 分钟，工具超时设 600000ms）

- [ ] **Step 7: 提交测试基建与红测试**

```powershell
git add -A
git commit -m "test(gametest): add gametest run config and failing creative tab test"
```

Expected: 提交成功（`build/`、`run/` 不入库）

---

### Task 4: 创造标签页 + 语言文件（绿）

**Files:**
- Modify: `src/main/java/com/tt23xrstudio/MoreThingsVanilla.java`（全文替换）
- Modify: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`（改用共享常量）
- Create: `src/main/resources/assets/more-things-vanilla/lang/en_us.json`
- Create: `src/main/resources/assets/more-things-vanilla/lang/zh_cn.json`

- [ ] **Step 1: 主初始化器全文替换（注册标签页）**

`src/main/java/com/tt23xrstudio/MoreThingsVanilla.java`：

```java
package com.tt23xrstudio;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoreThingsVanilla implements ModInitializer {
	public static final String MOD_ID = "more-things-vanilla";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 创造标签页物品清单。计划 2 起，各方块/物品注册时向此列表添加条目。 */
	public static final List<Item> TAB_ITEMS = new ArrayList<>();

	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("more_things_vanilla"));

	@Override
	public void onInitialize() {
		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				CREATIVE_TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.more_things_vanilla"))
						// 图标暂用铁块；计划 4 竖直红石完成后可换为竖直红石物品
						.icon(() -> new ItemStack(Blocks.IRON_BLOCK))
						.displayItems((context, output) -> TAB_ITEMS.forEach(output::accept))
						.build()
		);
		LOGGER.info("MoreThingsVanilla loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
```

- [ ] **Step 2: 语言文件（名称暂沿用英文品牌名，方块条目在计划 2 填入）**

`src/main/resources/assets/more-things-vanilla/lang/en_us.json`：

```json
{
	"itemGroup.more_things_vanilla": "More Things Vanilla"
}
```

`src/main/resources/assets/more-things-vanilla/lang/zh_cn.json`：

```json
{
	"itemGroup.more_things_vanilla": "More Things Vanilla"
}
```

（zh_cn 若想要中文名，直接改值即可，例如 `"更多原版之物"`——命名不阻塞本计划验收）

- [ ] **Step 3: 测试改用共享常量（行为不变）**

`src/main/java/com/tt23xrstudio/test/MtvGameTests.java` 全文替换为：

```java
package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

public final class MtvGameTests {

	@GameTest
	public void creativeTabRegistered(GameTestHelper helper) {
		helper.assertTrue(
				BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(MoreThingsVanilla.CREATIVE_TAB_KEY),
				"creative tab not registered: " + MoreThingsVanilla.CREATIVE_TAB_KEY
		);
		helper.succeed();
	}
}
```

- [ ] **Step 4: 运行 gametest，确认绿**

```powershell
Remove-Item build\gametest\report.xml -ErrorAction SilentlyContinue
.\gradlew.bat runGametest *> build\gametest-run.log
$r = Get-Content build\gametest\report.xml -Raw -ErrorAction SilentlyContinue
if (-not $r) { Get-Content build\gametest-run.log -Tail 60; throw '报告未生成，见 build\gametest-run.log 尾部输出' }
if ($r -notmatch 'mtv_game_tests_creative_tab_registered') { Write-Output $r; throw '测试未被发现' }
if ($r -match '<failure') { Write-Output $r; throw 'GREEN 未达成：报告中存在失败记录' }
Write-Output "GREEN confirmed (gradle exit=$LASTEXITCODE)"
```

注意（Task 3 实测修正）：报告 XML 的实际格式是 `<testcase>...<failure message="..."/></testcase>` 元素，**没有** `failures="N"` 属性——判定必须检查 `<failure>` 元素是否存在。先删除旧报告再运行，避免读到上一轮的 RED 报告造成假 GREEN。gradle 退出码仅作参考，以报告为准。

Expected: `GREEN confirmed`（首次构建含 runGametest 任务生成，超时设 600000ms）

- [ ] **Step 5: 提交**

```powershell
git add -A
git commit -m "feat: register creative tab with lang entries"
```

Expected: 提交成功

---

### Task 5: 全量验证与交付

**Files:**
- Modify: `README.md`（模板示例文档改为本 Mod 说明——Task 2 质量评审补充：原计划无任务覆盖 README）
- Modify: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`（补字面量 ID 断言——Task 4 质量评审补充：测试改用共享常量后失去独立钉死 ID 的能力，CREATIVE_TAB_KEY 路径若被改而 lang 键不跟，会静默显示原始键）

- [ ] **Step 1: 改名回归检查（评审补充）**

```powershell
git grep -nE 'modid|com\.example' -- src *.gradle
```

Expected: 无命中（`settings.gradle` 注释里的 "modid" 字样除外；若命中，顺手把该注释改为 `// Should match the mod id` 并随 Step 3 一起提交）

- [ ] **Step 2: 测试补字面量 ID 断言（Task 4 评审补充）**

在 `src/main/java/com/tt23xrstudio/test/MtvGameTests.java` 的 `creativeTabRegistered` 方法开头（`helper.assertTrue(...)` 之前）插入：

```java
		helper.assertValueEqual("more-things-vanilla:more_things_vanilla",
				MoreThingsVanilla.CREATIVE_TAB_KEY.location().toString(), "creative tab id changed");
```

然后重跑 gametest 确认仍绿（先删旧报告）：

```powershell
Remove-Item build\gametest\report.xml -ErrorAction SilentlyContinue
.\gradlew.bat runGametest *> build\gametest-run.log
$r = Get-Content build\gametest\report.xml -Raw -ErrorAction SilentlyContinue
if (-not $r) { Get-Content build\gametest-run.log -Tail 60; throw '报告未生成' }
if ($r -notmatch 'mtv_game_tests_creative_tab_registered') { Write-Output $r; throw '测试未被发现' }
if ($r -match '<failure') { Write-Output $r; throw 'GREEN 未达成：报告中存在失败记录' }
Write-Output "GREEN confirmed (gradle exit=$LASTEXITCODE)"
```

Expected: `GREEN confirmed`（超时 900000ms）

```powershell
git add src/main/java/com/tt23xrstudio/test/MtvGameTests.java
git commit -m "test(gametest): pin creative tab id literal"
```

- [ ] **Step 3: 重写 README（评审补充）**

`README.md` 全文替换为：

````markdown
# MoreThingsVanilla

Fabric Mod：在原版基础上添加竖直红石（粉/中继器/比较器）、坐垫，以及缺失材质的半砖与楼梯。

- 设计文档：[`设计.md`](设计.md)
- 实现计划：`docs/superpowers/plans/`

## 构建

```powershell
.\gradlew.bat build
```

产物位于 `build/libs/`。运行开发客户端：`.\gradlew.bat runClient`；运行 gametest：`.\gradlew.bat runGametest`。

## License

CC0-1.0（见 [`LICENSE`](LICENSE)）
````

```powershell
git add README.md
git commit -m "docs: rewrite readme for MoreThingsVanilla"
```

- [ ] **Step 4: 全量构建**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 客户端冒烟（必做——评审补充：`more_things_vanilla.client.mixins.json` 只有客户端启动时才会加载，构建期验证不到；写错包名/类名会运行时崩溃）**

```powershell
.\gradlew.bat runClient
```

Expected: 客户端能启动到主菜单、无崩溃；开发日志出现 `MoreThingsVanilla loaded`。进入创造模式可确认独立标签页「More Things Vanilla」（铁块图标，暂无物品），确认后正常关闭游戏。若客户端崩溃，优先检查 `more_things_vanilla.client.mixins.json` 的 package/类名与 `MtvClientMixin` 的注入目标。标签页在标签栏中的位置若不理想，记录下来在后续计划调整（需人眼确认，由控制器转达用户）。

- [ ] **Step 6: 仓库状态复核**

```powershell
git status --short
git log --oneline
```

Expected: status 干净；log 共 10 条提交：`chore: fabric example template baseline` → `docs: record feature branch step ...` → `refactor: rename ...` → `docs: expand task 5 ...` → `test(gametest): add ...` → `docs: fix gametest report verification ...` → `feat: register creative tab ...` → `docs: task 5 adds literal tab id assertion ...` → `test(gametest): pin creative tab id literal` → `docs: rewrite readme ...`

---

## 验收清单（对照 设计.md）

- [ ] `fabric.mod.json`：id=`more-things-vanilla`、name=`MoreThingsVanilla`、entrypoints=`com.tt23xrstudio.*`
- [ ] `gradle.properties`：group=`com.tt23xrstudio`
- [ ] assets 路径 `assets/more-things-vanilla/`，mixin 配置 `more_things_vanilla*.mixins.json` 已同步
- [ ] 独立创造标签页已注册且 gametest 通过（`build/gametest/report.xml` 中测试存在且无 `<failure>` 元素——报告无 `failures="N"` 属性，勿用属性判定）
- [ ] `zh_cn`/`en_us` 语言文件存在
- [ ] README 已从模板示例改为 MoreThingsVanilla 说明（Task 5 Step 3）
- [ ] 测试已补字面量 ID 断言（Task 5 Step 2）
- [ ] 客户端冒烟通过：runClient 启动到主菜单无崩溃（Task 5 Step 5）
- [ ] 全部改动已提交、工作区干净
