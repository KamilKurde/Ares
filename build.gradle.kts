import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	alias(libs.plugins.kotlin)
	alias(libs.plugins.shadow)
	alias(libs.plugins.serialization)
	application
}

group = "com.github.KamilKurde"
version = "0.1.0"

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.kord.core)
	implementation(libs.slf4j.simple)
	implementation(libs.serialization.toml)
}

application {
	mainClass.set("MainKt")
}

tasks.withType(ShadowJar::class.java) {
	archiveClassifier.set("")
}