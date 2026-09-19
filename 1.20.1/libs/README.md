# 编译期依赖（compile-only dependencies）

本目录存放 1.20.1 编译必需的第三方 jar，**仅用于 `compileOnly`**（Mixin
注解处理器需要验证 `@Mixin(targets = ...)` 目标类存在，运行时不会打包进模组）。

| 文件 | 来源 | 说明 |
|------|------|------|
| `flywheel-forge-1.20.1-1.0.4.jar` | <https://github.com/Engine-Room/Flywheel> | 机械动力（Create）渲染引擎，用于「透视眼镜」隐藏引擎渲染的 Mixin 目标类 |
| `embeddium-0.3.31+mc1.20.1.jar` | <https://modrinth.com/mod/embeddium> | Embeddium（Sodium 分支），用于「透视眼镜」满亮度渲染的 Mixin 目标类 |
| `legendarysurvivaloverhaul-1.20.1-2.3.23.1.jar` | <https://modrinth.com/mod/legendary-survival-overhaul> | Legendary Survival Overhaul，用于「自适应附魔」屏蔽其低水分屏幕模糊效果的 Mixin 目标类 |

如需重新获取或更换版本，下载对应 jar 放入本目录即可，无需修改构建脚本。
三者均为可选 Opt-in 依赖：缺失时对应功能自动禁用（Mixin 目标类载入失败仅告警，不影响游戏启动）。

> 注：`legendarysurvivaloverhaul` 的 Mixin 使用 `@Shadow` 读取目标类字段，
> 因此**编译期必须**存在该 jar（缺少时 Mixin 注解处理器会直接判定构建失败）；
> 另外两条 Mixin 只注入方法，缺少目标类时仅告警。

# Compile-only dependencies

These jars are required **only at compile time** for the Mixin annotation processor
to resolve `@Mixin(targets = ...)` classes. They are never packaged into the mod jar.

- `flywheel-forge-1.20.1-1.0.4.jar` — Flywheel (Create's rendering engine), target of the
  X-Ray Goggles' engine-hiding mixin. Source: <https://github.com/Engine-Room/Flywheel>
- `embeddium-0.3.31+mc1.20.1.jar` — Embeddium (Sodium fork), target of the full-bright light-data
  mixin. Source: <https://modrinth.com/mod/embeddium>
- `legendarysurvivaloverhaul-1.20.1-2.3.23.1.jar` — Legendary Survival Overhaul, target of the
  Adaptation enchantment's screen-blur suppression mixin.
  Source: <https://modrinth.com/mod/legendary-survival-overhaul>

All three are optional runtime dependencies: when absent, the related mixins simply do not apply
(the target class fails to load with a warning only) and the game runs normally.

> Note: the Legendary Survival Overhaul mixin uses `@Shadow` to read a target-class field, so the
> jar **must** be present at compile time (otherwise the Mixin annotation processor fails the build);
> the other two mixins only inject methods and merely warn when their target is missing.
