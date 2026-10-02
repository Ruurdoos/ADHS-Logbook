plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    // Native toolchains are only needed when producing the iOS framework.
    if (providers.gradleProperty("enableIos").orNull == "true") {
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework { baseName = "LogbookShared"; isStatic = true }
        }
    }
    jvmToolchain(21)
    sourceSets { commonMain.dependencies { api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }; commonTest.dependencies { implementation(kotlin("test")) } }
}
