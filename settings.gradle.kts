pluginManagement {
    repositories {
        // GitHub Actions（海外 runner）优先走官方源；阿里云镜像对海外 IP 会 502。
        // 国内本地构建时若官方源被 TLS 切断，可自动回退到下面的镜像。
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/gradle-plugin/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 本地预置的 jitpack 构件（RenderX / libsu），避免 jitpack 抖动导致解析失败。
        // 路径相对 settings.gradle.kts，CI 与本地都可用。
        maven {
            url = uri(rootDir.resolve("ci-assets/m2"))
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        // CI 上官方源优先，镜像作回退。
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://dl.bintray.com/rikkaw/Shizuku") }
        maven { url = uri("https://api.xposed.info/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "Operit"
include(":app")
include(":dragonbones")
project(":dragonbones").projectDir = file("avator/dragonbones")
include(":terminal")
include(":mnn")
project(":mnn").projectDir = file("llm/mnn")
include(":llama")
project(":llama").projectDir = file("llm/llama")
include(":mmd")
project(":mmd").projectDir = file("avator/mmd")
include(":fbx")
project(":fbx").projectDir = file("avator/fbx")
include(":showerclient")
include(":quickjs")
