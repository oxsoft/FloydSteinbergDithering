plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "com.oxsoft"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.oxsoft.floyd.steinberg.dithering.App")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "com.oxsoft.floyd.steinberg.dithering.App"
    }

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}