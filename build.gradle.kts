plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.abovevacant"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Above Vacant",
            "Automatic-Module-Name" to "com.abovevacant.epitaph"
        )
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("html5", true)
        addStringOption("Xdoclint:none", "-quiet")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("epitaph")
                description.set("Lightweight, zero-dependency decoder for Android tombstone protobuf files")
                url.set("https://github.com/abovevacant/epitaph")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("abovevacant")
                        name.set("Above Vacant")
                        url.set("https://github.com/abovevacant")
                    }
                }

                scm {
                    url.set("https://github.com/abovevacant/epitaph")
                    connection.set("scm:git:git://github.com/abovevacant/epitaph.git")
                    developerConnection.set("scm:git:ssh://git@github.com/abovevacant/epitaph.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "sonatype"
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl

            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME") ?: ""
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD") ?: ""
            }
        }
    }
}

signing {
    val hasKey = findProperty("signing.keyId") != null
        || findProperty("signing.gnupg.keyName") != null
    isRequired = hasKey
    if (hasKey) {
        useGpgCmd()
        sign(publishing.publications["mavenJava"])
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}
