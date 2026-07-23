# 点清 TickClear · Ardot 高保真原型生成提示词

> 用途：把下方「主提示词」整体粘贴进 **Ardot**（ardot.tencent.com 网页端，或 macOS 桌面端）的 AI 输入框，即可生成**可编辑、组件化、分层**的高保真设计原型（不是位图）。
> 配套：已定稿的设计 Token 见 `点清APP_产品设计文档.md` 附录 **D.7**。
> 若使用 Ardot 桌面端 + WorkBuddy MCP（macOS），我（WorkBuddy）可直接调用 Ardot MCP 驱动生成，无需你手动粘贴。

---

## 主提示词（一次性生成整套，直接复制）

```
请用 Ardot 为 Android 任务/待办 App「点清 TickClear」设计一套高保真、可编辑的移动端原型（手机竖屏 390×844）。
设计语言：Light & Breezy（轻盈清爽、呼吸感、微质感、活泼不幼稚）。
请用设计变量（Design Token / Variables）定义下列颜色、字号、间距、圆角，并支持 Light / Dark 双模式自动切换：

【色彩 · 清空蓝 Light 默认】
- Primary #2563EB（填充按钮底，白字）；Brand #5B8FF9（进度环 / FAB 描边 / 激活态）
- 辅助：完成绿 #61DDAA、组紫 #A78BFA
- 中性：背景 #F5F7FA、卡片 #FFFFFF、主文字 #1F2937、次文字 #5B6472、描边 #D5DBE5
- 语义：成功 #0E8C5E、错误·逾期 #C62828、警告·提醒橙 #9A5B00、暂停灰 #6B7280

【暗夜 Dark 模式】
- 背景 #0F1115、卡片 #161A21、主文字 #E6EAF0、次文字 #9AA3B2、描边 #2C333E
- Primary #9DB8FF、Brand #5B8FF9、成功 #5FD6A0、错误 #FF8A82、警告 #FFC97A

【字号 sp】Display 32 / Headline 24 / Title 18 / Body 15 / Label 15 / Caption 13 / Micro 11（中文系统无衬线，数字等宽 tnum）
【间距 4dp 栅格】4 / 8 / 12 / 16 / 24 / 32 / 48
【圆角】卡片 16 / 按钮 12 或胶囊 999 / 弹窗 20
【高程】卡片 1dp、FAB 3dp（暗夜用色调 elevation）

请生成以下 6 个页面（每页独立 Frame，使用自动布局与组件，便于后续修改）：
1. 今日视图（主屏）：顶部「今日·周三」+ 右侧圆形进度环（直径100dp、线宽10dp、圆角线帽、Brand 蓝进度）；任务卡片列表（白卡/16dp圆角/软阴影，含圆形勾选框、任务名、时间或位置 chip、组色点缀）；其中一张卡带「2 项冲突」橙红警示角标；右下蓝色圆形 FAB；底部三 Tab 导航（今日 激活 / 统计 / 设置）。
2. 统计页：连续打卡天数大卡（🔥 连续 7 天）、GitHub 风格打卡热力图（深绿100% / 浅绿50-99% / 灰1-49% / 空0%）、成就徽章一排（🌱🔥💯⚡🎯）、周完成趋势；底部三 Tab（统计 激活）。
3. 语音任务管理面板：半屏 BottomSheet（毛玻璃、圆角上沿），顶部音量波形条 +「正在聆听…」+ 蓝色圆形麦克风按钮，下方解析确认卡（展示「明早8点 提醒我 吃药」+ 修改/取消/确定），底层今日视图变暗。
4. 任务组页：组卡片（带组色）+ 组内子任务列表 + 组内一键完成。
5. 设置页：主题切换（清空蓝/薄荷绿/暗夜）、本地数据管理→回收站入口、语音识别服务商配置、动画开关。
6. 暗夜模式今日视图：套用 Dark 变量，其余同页面 1。

要求：文字全中文；组件化、可双击进入编辑；进度环与 FAB 用真实矢量绘制；输出可编辑设计稿，不要位图截图。
```

---

## 分屏精修提示词（生成首稿后用，逐页打磨）

- **今日视图**：「在今日视图右侧进度环下方加一条今日激励文案区；任务卡片左滑出现『完成/删除』操作按钮；冲突任务卡用红色左边框强调。」
- **统计页**：「热力图右侧加月份切换；成就徽章未解锁用灰色占位；加一个『本月完成率』圆环。」
- **语音面板**：「波形条改为随音量高度变化的蓝色矩形条；麦克风按钮加呼吸光晕动画；确认卡左侧加 🤖 图标。」
- **暗夜模式**：「确保所有文字对比 ≥4.5:1；卡片用色调 elevation 而非深阴影；进度环 Brand 蓝在暗底更亮。」

---

## 设计变量（Variables）落地建议（给 Ardot 的 Design Token 面板）

在 Ardot「变量」面板建立两套 Mode：`Light` / `Dark`，类型含 COLOR / FLOAT / STRING：

| 变量名 | Light | Dark | 类型 |
|--------|-------|------|------|
| color/primary | #2563EB | #9DB8FF | COLOR |
| color/brand | #5B8FF9 | #5B8FF9 | COLOR |
| color/success | #0E8C5E | #5FD6A0 | COLOR |
| color/error | #C62828 | #FF8A82 | COLOR |
| color/warning | #9A5B00 | #FFC97A | COLOR |
| color/bg | #F5F7FA | #0F1115 | COLOR |
| color/surface | #FFFFFF | #161A21 | COLOR |
| color/onSurface | #1F2937 | #E6EAF0 | COLOR |
| color/outline | #D5DBE5 | #2C333E | COLOR |
| radius/card | 16 | 16 | FLOAT |
| radius/pill | 999 | 999 | FLOAT |
| space/md | 12 | 12 | FLOAT |
| text/title | 18 | 18 | FLOAT |

> 注：Ardot 的 `apply_variables` 支持多模式（Light/Dark）与 COLOR/FLOAT/BOOLEAN/STRING 四种类型；若走 MCP（macOS 桌面端），可由我直接写入，无需手填。
