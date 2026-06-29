plugins {
  kotlin("jvm") version "2.2.0"
  `java-library`
  `maven-publish`
}

group = "cn.varsa"
version = "0.1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("info.picocli:picocli:4.7.6")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
  implementation("com.networknt:json-schema-validator:1.5.6")
  implementation("io.modelcontextprotocol:kotlin-sdk-server:0.13.0")
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(21)
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifactId = "cli-core"

      pom {
        name.set("cli-core")
        description.set("Shared Kotlin utilities for local CLI tools")
        url.set("https://github.com/xnhp/cli-core")
      }
    }
  }

  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/xnhp/cli-core")
      credentials {
        username = providers.gradleProperty("gpr.user")
          .orElse(providers.environmentVariable("GITHUB_ACTOR"))
          .orNull
        password = providers.gradleProperty("gpr.key")
          .orElse(providers.environmentVariable("GITHUB_TOKEN"))
          .orNull
      }
    }
  }
}
