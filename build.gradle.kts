import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	alias(libs.plugins.kotlin)
	alias(libs.plugins.shadow)
	alias(libs.plugins.serialization)
	application
}

group = "dev.kamilbak"
version = "1.0.0"

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.kord.core)
	implementation(libs.slf4j.simple)
	implementation(libs.serialization.toml)
}

application {
	mainClass.set("dev.kamilbak.ares.MainKt")
}

tasks.withType(ShadowJar::class.java) {
	archiveClassifier.set("")
	minimize {
		// Used by Kord
		exclude(dependency("io.ktor:ktor-serialization-kotlinx-json:.*"))
	}
}