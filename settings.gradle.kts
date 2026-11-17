pluginManagement {
    repositories {
        // 国内镜像前置：dl.google.com 在本机 TLS 握手会被切断
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/gradle-plugin/")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：dl.google.com / repo1.maven.org 直连不稳，镜像优先
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google()
        mavenCentral()
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
