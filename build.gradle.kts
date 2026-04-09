import org.gradle.kotlin.dsl.repositories

allprojects {
    group = "my"
    version = "0.1.0"

    repositories {
        mavenCentral()

        maven { url = uri("https://repo.spring.io/milestone") }

        maven { url = uri("https://repo.spring.io/snapshot") }

        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}