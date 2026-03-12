plugins {
  kotlin("jvm") version "2.2.0"
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
}

kotlin {
  jvmToolchain(17)
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifactId = "cli-core"
    }
  }
}
