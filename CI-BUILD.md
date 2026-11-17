# 定制构建说明（GitHub Actions）

本文记录本定制仓库在 GitHub Actions 上的构建方式，以及它与上游 `android-build.yml` 的差异。

## 快速使用

### 1. 准备 GitHub 私有仓库

在 GitHub 上创建**空**私有仓库（不要勾 README / .gitignore / license）。

### 2. 推送

```bash
cd operit-custom/operit
GITHUB_TOKEN=ghp_xxx REPO=你的用户名/operit-custom bash tools/push-to-github.sh
```

或者用已打好的 bundle 从零恢复（不依赖本机工作区）：

```bash
git clone /sdcard/Download/Operit/custom-repo/operit-custom.bundle operit-custom
cd operit-custom
git remote add origin https://<token>@github.com/你的用户名/operit-custom.git
git push -u origin master:main --force
```

### 3. 触发构建

推送后自动触发（`push: branches: [main, master]`），也可手动：

GitHub → Actions → **Custom Build (Operit + Purge + ZFX + Mika)** → Run workflow
可选任务：`assembleDebug` / `assembleRelease` / `:app:compileDebugKotlin` / `:app:bundleRelease`

产物在 run 页面底部 **Artifacts** → `operit-custom-apk-<run_number>`。

## 与上游 CI 的三处必要差异

| 差异 | 原因 | 本仓库的处理 |
|---|---|---|
| 不做 `git submodule update` | 本定制把 `terminal` 展开为普通目录（上游是 `OperitTerminalCore` submodule），本地无 `.git`，CI 里 submodule 命令会失败 | `.gitmodules` 已置空占位；工作流用 `submodules: false` |
| 回填 ffmpeg AAR | 上游 `ci/script/prepare_android_dependencies.py` 会 `shutil.rmtree(app/libs)` 再解 `libs.zip`，会删掉定制的 `ffmpeg-kit-local.aar` | AAR 存于 `ci-assets/`，依赖准备后复制回 `app/libs/` |
| 去掉 aarch64 特化 | `gradle.properties` 里 `org.gradle.java.home` 指向本地 `/usr/lib/jvm/java-21-openjdk-arm64`，`android.aapt2FromMavenOverride` 指向本地 arm64 aapt2；x86 runner 上都会打挂 | 工作流在构建前 `sed` 删除这两行，改用 setup-java 的 JDK 21 |

## 为什么本地也能编，但仍然要用 CI

本地 aarch64/proot：

- 单次 `:app:compileDebugKotlin` 约 **1h35m**
- 后台进程会被 proot 会话回收，只能前台长等
- 磁盘剩 ~4.7G，`clean` 后不敢轻易全量重来
- 需要 arm64 版的 aapt2、aidl、zipalign 与自编 `liboperit_ripgrep.so`

CI 上 x86_64 runner：

- Gradle 依赖与 CMake 产物可缓存，二次构建快得多
- 交叉编译 arm64 native（NDK toolchain 原生支持 x86 主机）
- 不占本地磁盘，不怕会话回收

## 构建产物版本

`versionName` 固定 `1.1.71`，`versionCode` 53（与上游一致）。

## 插件开关

- **Purge**（dsh-purge 1.1.71 清洗能力）：`PurgeEngine.setEnabled(false)` → 全部补丁关闭、`PromptHookRegistry.clearBuiltinHooks()`
- **ZFX**（zfx-emperor 0.2.0-mod.4 破甲能力）：`ZfxEngine.setEnabled(false)` → persona/续命/评分全部旁路

两者独立，关闭任一或全部时 Operit 恢复原始行为。

## 已解的关键构建阻塞（本地记录，CI 上不出现）

1. `liboperit_ripgrep.so`：rustc 交叉编译
2. `ffmpeg-kit-local.aar`：官方 6.0-2，53165315 字节，SHA256 `5a37a58c…0b9ea`
3. aapt2：arm64 版替换
4. backdrop 1.0.6 是 Java 21 字节码 → 必须 JDK 21
5. STT 模型镜像兜底
6. AIDL 编译
7. `duplicate class: DisplayGeometry`：根因是 `AgentDisplay.kt` 里有**两个 `companion object`**（Kotlin 非法），kapt stub 生成进入错误恢复，污染 `DisplayGeometry` 的产出。合并为一个 companion 后彻底修复。