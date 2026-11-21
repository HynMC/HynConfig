plugins {
    id("java")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.hynmc"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    exclude("io/github/hynmc/hynconfig/internal/**")
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(
        "io.github.hynmc",
        "hynconfig",
        project.version.toString()
    )

    pom {
        name.set("HynConfig")
        description.set("A configuration library for Java (Minecraft plugins).")
        url.set("https://github.com/HynMC/HynConfig")

        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("zzansalt")
                name.set("zzansalt")
                url.set("https://github.com/zzansalt")
            }
        }

        scm {
            url.set("https://github.com/HynMC/HynConfig")
            connection.set("scm:git:git://github.com/HynMC/HynConfig.git")
            developerConnection.set("scm:git:ssh://git@github.com/HynMC/HynConfig.git")
        }
    }
}
