initVersions(file("project-versions.json"))

// Top-level build file
buildscript {
    extra.apply {
        set("kotlin_version", kotlin_version)
    }

    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        maven { url = uri("https://www.jitpack.io") }
    }

    dependencies {
        //classpath("com.android.tools.build:gradle:8.0.2")
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath(kotlin("gradle-plugin", version = kotlin_version))
        classpath("com.jakewharton:butterknife-gradle-plugin:10.2.3")
        classpath("org.codehaus.groovy:groovy-json:3.0.8")
        classpath("com.yanzhenjie.andserver:plugin:2.1.12")
        classpath(libs.okhttp)
    }
}

// 统一仓库在 settings.gradle.kts，这里清空！！！
allprojects {
    // 空的，不写任何仓库
}

tasks.register<Delete>("clean").configure {
    delete(rootProject.layout.buildDirectory)
}