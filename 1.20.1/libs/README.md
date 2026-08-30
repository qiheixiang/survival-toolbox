# 编译期依赖（compile-only dependencies）

本目录存放 1.20.1 编译必需的第三方 jar，**仅用于 `compileOnly`**（Mixin
注解处理器需要验证 `@Mixin(targets = ...)` 目标类存在，运行时不会打包进模组）。

| 文件 | 来源 | 说明 |
|------|------|------|
| `flywheel-forge-1.20.1-1.0.4.jar` | <https://github.com/Engine-Room/Flywheel> | 机械动力（Create）渲染引擎，用于「透视眼镜」隐藏引擎渲染的 Mixin 目标类 |
| `embeddium-0.3.31+mc1.20.1.jar` | <https://modrinth.com/mod/embeddium> | Embeddium（Sodium 分支），用于「透视眼镜」满亮度渲染的 Mixin 目标类 |

如需重新获取或更换版本，下载对应 jar 放入本目录即可，无需修改构建脚本。
两者均为可选 Opt-in 依赖：缺失时对应功能自动禁用（Mixin 目标类载入失败仅告警，不影响游戏启动）。

# Compile-only dependencies

These jars are required **only at compile time** for the Mixin annotation processor
to resolve `@Mixin(targets = ...)` classes. They are never packaged into the mod jar.

- `flywheel-forge-1.20.1-1.0.4.jar` — Flywheel (Create's rendering engine), target of the
  X-Ray Goggles' engine-hiding mixin. Source: <https://github.com/Engine-Room/Flywheel>
- `embeddium-0.3.31+mc1.20.1.jar` — Embeddium (Sodium fork), target of the full-bright light-data
  mixin. Source: <https://modrinth.com/mod/embeddium>

Both are optional runtime dependencies: when absent, the related mixins simply do not apply
(the target class fails to load with a warning only) and the game runs normally.
