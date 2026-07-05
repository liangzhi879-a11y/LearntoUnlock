// 题库 Schema + JSON 解析。纯 Kotlin JVM 库，使用 kotlinx.serialization。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}

// 让测试能读取 assets 下的样例题库
sourceSets {
    test {
        resources.srcDirs("$rootDir/quizbank")
    }
}
