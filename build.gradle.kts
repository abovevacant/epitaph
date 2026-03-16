plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("jacoco")
    id("com.diffplug.spotless") version "7.0.2"
    id("net.thebugmc.gradle.sonatype-central-portal-publisher") version "1.2.4"
}

group = "com.abovevacant"

fun runCommand(vararg args: String): String? =
    try {
        val process =
            ProcessBuilder(*args)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() == 0) output.ifEmpty { null } else null
    } catch (_: Exception) {
        null
    }

val gitCommit =
    System.getenv("GITHUB_SHA")?.ifBlank { null }
        ?: runCommand("git", "rev-parse", "--verify", "HEAD")

val gitTag =
    when (System.getenv("GITHUB_REF_TYPE")) {
        "tag" -> System.getenv("GITHUB_REF_NAME")?.ifBlank { null }
        else -> runCommand("git", "describe", "--tags", "--exact-match", "HEAD")
    }

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.ow2.asm:asm:9.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}

tasks.register("coverageSummary") {
    dependsOn(tasks.jacocoTestReport)
    doLast {
        val report = file("build/reports/jacoco/test/jacocoTestReport.xml")
        if (!report.exists()) {
            println("No coverage report found. Run tests first.")
            return@doLast
        }
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(report)
        val counters = xml.documentElement.childNodes
        println("\n--- Coverage Summary ---")
        for (i in 0 until counters.length) {
            val node = counters.item(i)
            if (node.nodeName == "counter") {
                val type = node.attributes.getNamedItem("type").nodeValue
                val missed = node.attributes.getNamedItem("missed").nodeValue.toDouble()
                val covered = node.attributes.getNamedItem("covered").nodeValue.toDouble()
                val total = missed + covered
                val pct = if (total > 0) (covered / total * 100) else 0.0
                println("  %-14s %6.1f%% (%s/%s)".format(type, pct, covered.toInt(), total.toInt()))
            }
        }
        println()
    }
}

tasks.jar {
    manifest {
        val manifestAttributes =
            mutableMapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Above Vacant",
                "Automatic-Module-Name" to "com.abovevacant.epitaph"
            )
        gitCommit?.let { manifestAttributes["Build-Commit"] = it }
        gitTag?.let { manifestAttributes["Build-Tag"] = it }
        attributes(manifestAttributes)
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("html5", true)
        addStringOption("Xdoclint:none", "-quiet")
    }
}

centralPortal {
    username = findProperty("centralPortalUsername") as String? ?: System.getenv("CENTRAL_PORTAL_USERNAME") ?: ""
    password = findProperty("centralPortalPassword") as String? ?: System.getenv("CENTRAL_PORTAL_PASSWORD") ?: ""

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

        withXml {
            val projectNode = asNode()
            val scmNode =
                (projectNode.children().firstOrNull {
                    it is groovy.util.Node && it.name().toString().endsWith("scm")
                } as? groovy.util.Node)
                    ?: projectNode.appendNode("scm")
            scmNode.appendNode("tag", gitTag ?: "HEAD")
        }
    }
}

signing {
    val hasKey = findProperty("signing.keyId") != null
        || findProperty("signing.gnupg.keyName") != null
    isRequired = hasKey
    if (hasKey) {
        useGpgCmd()
        sign(publishing.publications)
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}
