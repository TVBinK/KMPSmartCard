import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.buscardmanagement"
version = "1.0-SNAPSHOT"

// Cấu hình Java compatibility - dùng Java 21
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Kotlin toolchain - dùng Java 21
kotlin {
    jvmToolchain(21)
}

// Loại bỏ Java Card applet khỏi Gradle build (chỉ compile bằng JCIDE)
sourceSets {
    main {
        java {
            exclude("**/applet/**")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Jetpack Compose Desktop
    implementation(compose.desktop.currentOs)
    
    // Java SmartCard API (javax.smartcardio)
    // Đã có sẵn trong JDK, không cần thêm dependency
    
    // AWT/Swing cho image processing
    // Đã có sẵn trong JDK
    
    // Optional: Thêm logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Optional: JSON processing (nếu cần)
    implementation("com.google.code.gson:gson:2.10.1")

    // SQLite JDBC Driver
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    
    // Coroutines cho async database operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "BusCardManagement"
            packageVersion = "1.0.0"
            
            description = "Hệ thống quản lý thẻ xe bus với Smart Card"
            vendor = "Bus Card Management System"
            
            windows {
                // Cấu hình cho Windows
                menuGroup = "Bus Card Management"
                upgradeUuid = "BUS-CARD-2025-UUID-001"
            }
            
            linux {
                // Cấu hình cho Linux
                packageName = "bus-card-management"
            }
        }
    }
}

