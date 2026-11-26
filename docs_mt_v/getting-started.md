# 快速上手

## 安装

1. 准备 Minecraft **26.2** 的实例，使用 Java 25，安装 Fabric Loader（至少 0.19.0）。
2. 将适用于 26.2 的 Fabric API（本项目构建版本为 `0.161.0+26.2`）和 MoreThingsVanilla `0.1.0` 的运行用 JAR 放入该实例的 `mods/`。多人游戏时，服务端和各客户端均安装匹配的版本。
3. 从 Fabric 配置启动。进入创造世界，打开物品栏的「更多内容-原版」标签页，找到「竖直红石」或「白色羊毛半砖」即可确认内容已加载。

注：本 Mod 建议和 [`vanilla-refit-fabric`](https://github.com/X-starRelight/vanilla-refit-fabric) Mod 一起食用。

## 第一次制作：两个白色座位

在工作台一行放 3 块白色羊毛，取出 6 个白色羊毛半砖。每 3 个半砖再横排一次，总共得到 2 个白色坐垫。拿着其中一个对地板顶面使用，再空手使用坐垫即可入座。另一个可以先和染料无序合成，再摆到旁边。更多放置位置和回收方式见[坐垫](cushion.md)。

如果想先试红石，单独把 1 份红石粉放进合成栏，就能做出 1 块竖直红石。在红石块上方逐块叠起三块，观察各面显示的 `15、14、13`；下一步可按[竖直红石指南](redstone.md)接灯。

## 从源码运行（可选）

在装有 JDK 25 的环境中打开项目根目录，使用仓库附带的 Gradle Wrapper：

| 目的 | Windows PowerShell | 结果 |
|---|---|---|
| 构建 JAR | `.\gradlew.bat build` | 在 `build/libs/` 取运行用 JAR，跳过 `-sources.jar` |
| 直接试玩 | `.\gradlew.bat runClient` | 启动开发客户端 |
| 运行行为测试 | `.\gradlew.bat runGametest` | 测试报告写入 `build/gametest/report.xml` |

在 Linux 或 macOS 上，将 `.\gradlew.bat` 换成 `./gradlew`。遇到启动或配方问题可查阅[常见问题](faq.md)。
