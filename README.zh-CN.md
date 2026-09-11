# TrajectoryLens

**中文** · [English](README.md)

> **See where things are going — before they get there.**
> Minecraft **26.2** · Fabric · client-side · MIT

[![build](https://github.com/QunKA777/trajectorylens/actions/workflows/build.yml/badge.svg)](https://github.com/QunKA777/trajectorylens/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-3C8527)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.3%2B-DBD0B4)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/QunKA777/trajectorylens?label=download)](https://github.com/QunKA777/trajectorylens/releases/latest)

TrajectoryLens predicts and renders where dropped items, arrows, TNT, falling blocks and even whole
explosion chains are going to end up — as translucent, glass-like coloured segments you can see
through terrain. No particles, no block changes, no world edits, and **nothing is ever sent to the
server**: install it on your client and it works on any server, vanilla or modded.

预测掉落物 / 投掷物 / 爆炸的运动轨迹并以"染色玻璃质感"的半透明彩色线段绘制出来,附命中点、落点、
因果链与物流诊断。**全部在客户端运行**(无粒子、不放置方块、不改世界、不向服务器发包),连原版服务器
也能用;同一 jar 装到纯服务端则零副作用,只多一个预测命令。

## 安装

1. 装 **Fabric Loader 0.19.3+** 与 **Fabric API**(26.2 版);
2. 把 `trajectorylens-1.1.0.jar` 放进客户端 `mods/`;
3. 进游戏后按 **G** 开关掉落物轨迹,按 **H+J** 打开控制面板,或输入 `/trajectorylens`(短别名 `/tl`)。

- **只装在你自己客户端就够**:所有功能都在本地计算,服务器不需要装、也不会收到任何本模组的数据包,
  因此不存在额外权限 / 反作弊交互问题;原版服务器、别家 Fabric 服务器、整合服都能用。
- 同一 jar 装到**服务端**也只多一条 `/trajectorylens simulate …`(无渲染),公开服务器请注意该命令默认无权限限制。
- 从旧版 `ItemTrajectory`(`itemtrajectory`)升级:配置会自动读取 `config/itemtrajectory.json` 并迁移,
  观察列表 / 颜色 / 计数器都不会丢;换 jar 时请**删掉旧的 `itemtrajectory-*.jar`**,避免两个 mod 同时加载。

## 功能总览

| 分类 | 功能 |
|---|---|
| **掉落物** | 逐 tick 复刻的轨迹预测 · 静止落点与预计静止时间 · 多物品配色 · 观察列表(按物品 id 过滤) |
| **投掷物** | 箭 / 三叉戟 / 雪球 / 鸡蛋 / 末影珍珠 / 药水 / 火球 的飞行路线与命中目标 · 手持瞄准预测 |
| **爆炸** | TNT / 苦力怕 / TNT 矿车 引信倒计时与爆炸圈 · **因果链推演**(连锁引爆、被推物品落点、方块后果) |
| **方块** | 沙子 / 沙砾 / 铁砧 / 钟乳石等**下落方块落点**,正下方有人时标红 |
| **生物** | 威胁与仇恨指示(锁定 / 警戒 / 未察觉) · 实体行走范围预测 |
| **物流** | 卡口计数器(速率 / 堆积 / 3 分钟趋势色带)· **漏斗堵塞检测** · 实体普查 · 报告导出 |
| **丢失** | 失踪溯源(被谁 / 被什么弄没的)· 漏斗高亮 · 物品寿命(即将消失)倒计时 |
| **交互** | 8 页控制面板 · 纯键盘组合键绑定 · 物品/生物搜索选择器 · 全部设置持久化 |

## 控制面板

默认 **H+J** 打开(可用面板里的 按键 页改);顶部两行四列共 8 页,窗口够宽时自动排成两列,永远不需要滚动:

| 页 | 内容 |
|---|---|
| **总览** | 三个总开关 + 一键全开 / 全关(只留面板)+ 当前状态 + 快捷键提示 |
| **掉落物** | 掉落物轨迹 · 搜索添加物品 · 清空观察 · 当前观察(点行移除)· 失踪溯源 · 失踪标记时长 · 漏斗高亮时长 · 物品寿命提醒 |
| **投掷物** | 投掷物轨迹 · 手持瞄准预测 · 爆炸预警 |
| **爆炸推演** | 因果链推演 · 推演层数(1-4)· 击退推演时长(3/6/9/12 秒)· 下落方块落点 |
| **生物** | 威胁指示 · 行走范围盘 · 预测时长 · 搜索添加生物类型 · 当前观察 |
| **物流** | 实体普查 · 失踪摘要 · 漏斗堵塞检测 · 堵塞判定时长 · 卡口计数器 · 添加计数器 · 计数器列表(带趋势) |
| **按键** | 点行 → 直接按新键(可组合,最多 3 个),松手即绑定;Esc 取消,退格清除 |
| **工具** | 查看颜色/观察列表/开关 · 导出报告 · 原版按键设置 · 失踪记录 · 指令速查 |

## 键位与命令

| 键 | 行为 |
|---|---|
| **G** | 开 / 关掉落物预测轨迹(原版按键设置里可改,搜 `TrajectoryLens`) |
| **O+P**(同时按住) | 开 / 关实体行走范围盘 |
| **H+J**(同时按住) | 打开控制面板 |

> **注意**: `O+P` / `H+J` 是**组合键**,按住不放才算触发,不是按顺序敲。

根指令 **`/trajectorylens`**,短别名 **`/tl`**(下表一律写全名,把 `/trajectorylens` 换成 `/tl` 效果相同)。
客户端与服务端注册的是同一棵树:只装客户端时走客户端实现(不发包),服务端也装了本 Mod 时由服务端树接管并回传执行,补全与效果一致。

| 命令 | 用途 |
|---|---|
| `/trajectorylens toggle` | 开启/关闭轨迹显示(同按 G) |
| `/trajectorylens target <物品id>` | 把该物品加入观察列表(可省略命名空间,如 `diamond`);重复执行累加,不重置已有项 |
| `/trajectorylens target clear` | 清空观察列表,恢复跟踪所有物品 |
| `/trajectorylens color <物品id> <颜色>` | 设某物品轨迹颜色:6 位 `RRGGBB` 或 8 位 `AARRGGBB`,支持 `#`/`0x` 前缀,`auto` 恢复自动配色 |
| `/trajectorylens colors` / `status` | 列出当前颜色(含自动分配)与开关 / 目标状态 |
| `/trajectorylens sim(ulate) <x> <y> <z> <vx> <vy> <vz>` | (服务端)对给定初始位置/速度跑一遍预测引擎并输出终点,用于装置设计 |
| `/trajectorylens projectiles` / `tnt` / `aim` / `chain` / `falling` `on\|off\|toggle` | 投掷物轨迹 / TNT 爆炸预警 / 手持瞄准 / 因果链 / 下落方块 开关 |
| `/trajectorylens chaindepth [层]` | 因果链推演层数 1-4(不带参数循环) |
| `/trajectorylens chainhorizon [秒]` | 被炸飞物品/生物的推演时长(不带参数循环 3/6/9/12) |
| `/trajectorylens threat on\|off\|toggle` | 威胁指示开关 |
| `/trajectorylens census` | 输出附近实体普查(敌对/动物/物品/经验/投掷物数量与最多的种类) |
| `/trajectorylens flowcount on\|off\|toggle` | 卡口计数器显示开关 |
| `/trajectorylens counter add <名字>` | 在准星所指位置放置计数面(物品穿越即计数,给出 /min 与上游堆积) |
| `/trajectorylens counter remove <名字>` / `clear` / `list` | 移除 / 清空 / 查看计数器 |
| `/trajectorylens jam on\|off\|toggle` · `jamtime [秒]` | 漏斗堵塞检测开关与判定阈值(默认 6 秒) |
| `/trajectorylens losttrack on\|off\|toggle` | 失踪溯源开关 |
| `/trajectorylens lost` / `lost clear` | 输出失踪记录 / 清空记录 |
| `/trajectorylens losttime [秒]` | 失踪标记显示时长(1-300,不带参数循环 3/5/6/8/10/15/30/60) |
| `/trajectorylens glowtime [秒]` | 漏斗高亮时长(1-300,不带参数循环 2/3/5/8/10/15/20) |
| `/trajectorylens despawn on\|off\|toggle` | 物品寿命(即将消失)提醒开关 |
| `/trajectorylens export` | 把普查 / 计数器(含趋势)/ 堵塞 / 失踪记录导出成 `config/trajectorylens-report-*.txt` |
| `/trajectorylens range add <实体类型>` | 把实体类型加入行走范围观察(如 `zombie`/`villager`,Tab 可补全) |
| `/trajectorylens range clear` / `list` / `toggle` | 清空 / 查看 / 开关 行走范围盘 |
| `/trajectorylens range time <秒>` | 范围预测时长 1-60 秒(默认 5) |
| `/trajectorylens range color <实体类型> <颜色>` | 设置某类型的范围盘颜色 |
| `/trajectorylens gui` | 打开控制面板 |

## 原理与精度

26.2 的掉落物物理与旧版本差异很大(水中阻力 0.99 / 熔岩 0.95、水中无重力、水流推力 0.014、
触地按方块摩擦系数、碰撞与反弹内置于 `Entity.move`)。本 Mod 不手写公式,而是创建一个**影子
ItemEntity**(从不加入世界),逐 tick 调用与服务器相同的引擎方法
(`updateFluidInteraction → 流体分支/重力 → noPhysics → move → applyEffectsFromBlocks → 拖曳/摩擦`),
因此只要没有玩家/生物推挤、拾取、合并等外部事件干扰,预测与原版逐 tick 一致。
被拾取/消失时轨迹立刻清除;外部扰动每 20 tick 或状态漂移时自动重算。

投掷物同理,按各自真实参数复刻:**箭** 重力 0.05 / 空气 0.99 / 水 0.6;**雪球·鸡蛋·珍珠·药水** 0.03 / 0.99 / 0.8;
**火球** 0.95 阻力 + 每 tick 0.1 加速;**TNT** 0.04 / 0.98 且落地反弹 `(0.7, -0.5, 0.7)`、引信 80 tick;
**下落方块** 0.04 / 0.98。命中点是方块碰撞射线 + 实体碰撞箱(外扩 0.3)取最近者。

## 轨迹外观

- 轨迹:亮紫 → 深紫渐变的半透明方块段(染色玻璃观感),较厚、不透明度高,**全透视**——
  走 Gizmo 的 always-on-top 通道渲染,可穿过地形观察整条轨迹;**不用粒子、不放置任何方块**;
- 落点(静止):绿色半透明圆盘 + `rest ~x.xs` 预计静止时间(物品被拾取/消失即刻清除,不会残留闪烁);
- 终点原因:燃毁 = 橙点 + `burns`;到达预测上限 / 离开加载 = 紫点。

## 投掷物 · 爆炸 · 因果链

- **投掷物轨迹**:追踪 48 格内的箭/光灵箭/三叉戟、雪球/鸡蛋/末影珍珠/药水、恶魂与烈焰人火球与 TNT,
  在命中点画圆环并标注**命中方块**或**命中:<实体名>**(这一箭会射中谁);
- **手持瞄准预测**:手持弓/弩/三叉戟/雪球等时实时显示弹道,松开即命中;
- **末影珍珠**:标出传送落点(原版传送到撞击前一 tick 的位置)、传送自伤 5 点与危险落点判定;
- **爆炸预警**:点燃的 TNT 显示引信倒计时、预测落点与内外两层爆炸影响圈(内圈 = 威力 4 必破坏范围,
  外圈 = 2 倍半径伤害/击退区);苦力怕按 `getSwellDir()` 同步的膨胀状态预警;TNT 矿车同样支持;
- **因果链推演**:从爆炸点继续往下算——
  - **连锁引爆**:波及范围内已点燃的 TNT、被炸飞后引信仍在的 TNT、TNT 矿车,各按自己的剩余引信排进时间链;
  - **谁被推到哪里**:被冲击波推出的物品/生物各跑一条抛物线(生物重力 0.08,物品与 TNT 0.04),
    弧线末端直接写结论:**→ 岩浆!** / **→ 仙人掌** / **→ 虚空** / **→ 水里(安全)** / **→ 摔落 N 点** / **→ 安全落地**;
  - **方块后果**:被摧毁的 TNT 方块数量、上方**下落方块**是否会砸落、其中几处下面有生物;
  - 层数(1-4)与推演时长(3/6/9/12 秒)可调,每层最多 8 个分支;每个阶段都做了隔离,出问题只会少画一段。

> 击退与连锁是**近似推演**(真实击退受爆炸接触面、药水效果、抗性影响),标注意图是给出量级与方向,不是逐 tick 精确复刻。

## 下落方块落点

沙子 / 沙砾 / 铁砧 / 混凝土粉末 / 钟乳石等下落的方块显示虚线落点轨迹 + 落点圆环,标注方块名与预计落地时间;
**正下方 2 格内有生物/玩家时整体变红**并写 `砸到 僵尸!` / `砸到你!`。

## 生物:威胁指示与行走范围

- **威胁指示**:32 格内敌对生物标注 **§c锁定**(有视线 + 朝向你 + 近距离)/ **§6警戒** / **§7未察觉**,
  锁定目标连一条红线并显示距离,只显示最近 12 个。依据是"距离 + 追击范围属性 + 视线射线 + 朝向",
  属客户端推断(真实 AI 目标在服务端),但足够判断"这只僵尸是不是冲我来的";
- **行走范围盘**:按 **O+P** 开启后,对被观察实体类型显示**极限可达域**——从当前位置按行走速度在未来 N 秒
  (默认 5s,可调 5/10/15/20/30/60)理论上能到达的所有格子,地面边界描边 + 中心圆环,整数格对齐,可多类型并存(各自配色)。

## 物流:计数器、堵塞与普查

- **卡口计数器**:在准星处放 3x3 虚拟计数面(法线跟随朝向),物品穿越即计数,显示 **最近 1 分钟速率**、
  **5 分钟均值**、**上游 8 格堆积数**,并保留最近 3 分钟**趋势色带** `▁▂▄▆█` 与 ↑/↓ 趋势——用来判断
  "这条水道 / 漏斗线是不是变慢了";
- **漏斗堵塞检测**:物品**静止**在漏斗上超过阈值(默认 6 秒)即框出:
  **红框 = 堵塞**(下游满 / 分类机卡住 / 根本吸不进去,框上写 `堵塞 N 件 已 8.4s <物品名>`),
  **蓝框 = 红石锁定**(该漏斗 `enabled=false`,是你自己关的,不算故障)。判定用原版吸取体积
  (`x..x+1, y+0.1875..y+1.5, z..z+1`)与 `enabled` 属性,和游戏行为一致;漏斗矿车同样识别;
- **实体普查**:附近实体总数、敌对 / 动物 / 其他生物、物品 / 经验球 / 投掷物数量与最多的 6 种类型,
  物品 >200 或敌对 >60(接近刷怪上限)时给出提示——判断"刷怪塔为什么变慢";

## 丢失的物品:失踪溯源 · 漏斗高亮 · 寿命提醒

- **失踪溯源**记录附近掉落物**为什么消失**:被玩家拾取、**被漏斗 / 漏斗矿车吸走**、岩浆 / 火焰 / 仙人掌销毁、
  被爆炸摧毁、合并堆叠、超时消失(5 分钟)、掉入虚空等,统计在物流页顶部给出摘要,消失位置留一个
  **幽灵标记**(颜色随原因变化,默认 6 秒淡出);
- **漏斗判定按原版几何来**:物品最后位置落在漏斗吸取体积内(±0.35 容差),或从漏斗上方 ≤3 格直直掉进去,
  就归因为**被漏斗吸走**;红石锁住的漏斗**不算**。判定顺序是 岩浆/火焰/仙人掌/爆炸 → **漏斗** → 玩家拾取 →
  超时 → 合并 → 未知,所以"玩家站在漏斗旁边"不会再被误报成被玩家捡走;
- **漏斗高亮**:吸走物品的漏斗高亮 **默认 5 秒**、颜色由亮到淡渐变,期间再次吸入会**重置计时**,
  一眼看出"东西到底进了哪个漏斗";时长可在面板 / `glowtime` 调整;
- **物品寿命提醒**:原版掉落物 5 分钟后消失,客户端看不到真实计时器,因此用**本客户端持续观察到的时长**:
  同一物品在视野内待满 4 分半后头顶出现琥珀倒计时 `≤30s 消失`,剩不到 30 秒变红并在聊天栏提醒一次;
- **报告导出**:`/trajectorylens export` 把普查、全部计数器(速率 / 趋势 / 累计)、堵塞情况、失踪记录写成
  `config/trajectorylens-report-<时间戳>.txt`,隔几天导一份就能对比农场是否退化。

## 联机时"客户端独装"能看到什么

| 能做 | 看不到 / 只能推断 |
|---|---|
| 全部轨迹/预测/高亮/面板/指令(纯本地执行,不发给服务器) | 只能看到**服务器同步给你的实体**:超出实体跟踪距离或未加载区块里的掉落物看不到 |
| 高度、方块、流体、漏斗 `enabled` 状态等方块数据 | 别人的背包、箱子内容、漏斗里到底装了多少(服务器不发) |
| 客户端自己观察出的消失原因(漏斗/岩浆/超时/虚空…都准) | **别的玩家捡走**的东西只能标"原因未知" |
| 物品寿命倒计时(按你看到的时长推算,是真实 5 分钟的下界) | 物品真实 age / 引信等服务器私有字段(用观察值代替) |

> 想让"别人扔的东西 / 远处的东西"也进预测,才需要服务端也装本 Mod(设计文档里的二期方案 B:服务端权威轨迹广播)。

## 从源码构建

只需要 **JDK 25**;Gradle 用仓库自带的 wrapper(首次运行会自动下载 Gradle 9.5.1)。

```bash
./gradlew build        # Windows: gradlew.bat build  → build/libs/trajectorylens-1.1.0.jar
./gradlew test         # 纯逻辑单测(路径抽稀 / 颜色工具)
./gradlew genSources   # (可选)生成 26.2 反编译源码,方便查 API
```

> 26.2 起游戏不再混淆(Yarn 已停更),Loom 1.17 直接按官方命名编译,依赖写 `implementation`;
> 需要 Fabric Loader ≥ 0.19.3、Fabric API 0.160.0+26.2、Java 25。

## 目录结构

```
trajectorylens/
├── src/main/java/dev/soityy/trajectorylens/        common(双端安全)
│   ├── TrajectoryLensMod.java                      入口 + 服务端指令树(payload 转发)
│   ├── physics/                                    影子实体预测引擎(逐 tick 复刻原版)
│   ├── network/TargetPayload.java                  服务端 → 客户端 设置包
│   └── util/PathTools.java                         路径抽稀 / 颜色工具(带单测)
├── src/client/java/dev/soityy/trajectorylens/client/
│   ├── TrajectoryLensClient.java                   客户端入口: 注册 tracker / renderer / 配置
│   ├── track/                                      追踪与仿真(掉落物、投掷物、TNT、因果链、物流、失踪溯源)
│   ├── render/                                     6 个 Gizmo 渲染器(轨迹/范围/投掷物/流量/威胁/失踪)
│   ├── ui/                                         控制面板、搜索选择器、配置持久化、组合键、报告导出
│   └── command/ClientCommands.java                 纯客户端指令回退(服务端没装也能用)
├── src/test/java/dev/soityy/trajectorylens/        JUnit 单测
├── src/main/resources/fabric.mod.json + assets/trajectorylens/icon.png
├── docs/DESIGN-zh.md                               设计方案、版本核查与决策记录
├── .github/workflows/build.yml                     CI: JDK 25 构建 + 上传 jar
├── CHANGELOG.md / LICENSE / README.md
└── gradlew(.bat) / gradle/wrapper/                 自带 Gradle wrapper
```

## 语言 / Languages

游戏内界面**跟随游戏语言**:`assets/trajectorylens/lang/en_us.json` 提供英文,`zh_cn.json` 是中文原文,
其他语言(日/德/俄…)会回退到英文。目前控制面板、搜索选择器与全部聊天提示都已翻译,叠加在世界上的
标签(轨迹文字、堵塞提示等)会在后续版本补上。翻译键就是中文原文(gettext 风格),所以**缺翻译也不会显示成
key**,只会显示中文原文。欢迎 PR 补 `assets/trajectorylens/lang/<语言>.json`。

## 已知限制

- 玩家 / 生物走位推挤、物品合并 / 拾取无法预知:对应轨迹实时重算或消失;
- 未加载区块处预测终止(真实物品在那里同样停摆);
- 别的玩家捡走的东西、容器内容等服务器私有信息客户端拿不到,只能推断或标"未知";
- 玻璃观感来自半透明填充 Gizmo,默认走 always-on-top 通道(可透过地形看到);
- 面板按 GUI 缩放自动排成 1 列 / 2 列,极小的窗口下个别行可能被挤掉,建议 GUI 缩放 ≥ 2。

## 许可与致谢

- 作者 **soityy** · 许可证 **MIT**(见 [LICENSE](LICENSE));
- 版本历史见 [CHANGELOG.md](CHANGELOG.md),设计取舍见 [docs/DESIGN-zh.md](docs/DESIGN-zh.md);
- 26.2 起游戏不再混淆(Yarn 停更),本项目直接按官方命名编译,不含 Mixin、不改动任何原版类;
- 欢迎 issue / PR:<https://github.com/QunKA777/trajectorylens>。
