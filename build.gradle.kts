plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(1)
    benchmarkMode.set(listOf("avgt"))
    timeOnIteration.set("1s")
    resultFormat.set("TEXT")
    failOnError.set(true)

    val includePattern = findProperty("jmh.includes") as String?
    if (!includePattern.isNullOrBlank()) {
        includes.set(listOf(includePattern))
    }

    val excludePattern = findProperty("jmh.excludes") as String?
    if (!excludePattern.isNullOrBlank()) {
        excludes.set(listOf(excludePattern))
    }
}
