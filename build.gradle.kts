import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    idea
    `maven-publish`
    jacoco
    id("net.minecraftforge.gradle") version "6.0.54"
    id("org.parchmentmc.librarian.forgegradle") version "1.2.0"
    id("org.spongepowered.mixin") version "0.7.38"
}

group = "com.bettercontent"
version = property("mod_version") as String
base {
    archivesName.set(property("artifact_name") as String)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// One configuration-time directory binds the RunConfig, setup and exact native audit sink.
val nativeGameTestDirectory = layout.buildDirectory.dir("gametest/${UUID.randomUUID()}")
fun provisionNativeProperties(directory: java.io.File) {
    Files.createDirectories(directory.toPath())
    try {
        Files.writeString(directory.toPath().resolve("server.properties"),
            "online-mode=false\nenforce-secure-profile=false\n",
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } catch (alreadyPresent: java.nio.file.FileAlreadyExistsException) {
        // Retain exact existing effective properties; never normalize or overwrite a fixture.
    }
}

// Resources-only native profile: materialize the pack's existing authored Malum fixes,
// without adding them to Commerce's production source set or staged JAR.
val nativeFixture by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
val malumAdvancementFixes = listOf("impetus_creation_zinc", "node_focusing_zinc",
    "zinc_from_node_smelting", "zinc_from_node_blasting")
val authoredMalumFixRoot = file("../../better-content-modpack/kubejs/data/malum/advancements/recipes")

tasks.named<ProcessResources>(nativeFixture.processResourcesTaskName) {
    dependsOn(tasks.named("compileJava"))
    from(layout.buildDirectory.file("tmp/compileJava/better_spirit_commerce.refmap.json"))
    from(authoredMalumFixRoot) {
        include(malumAdvancementFixes.map { "$it.json" })
        into("data/malum/advancements/recipes")
    }
    from(file("../../better-content-modpack/kubejs/data/malum/recipes/create/crushing/crush_rare_earths.json")) {
        into("data/malum/recipes/create/crushing")
    }
    doLast {
        malumAdvancementFixes.forEach { name ->
            check(authoredMalumFixRoot.resolve("$name.json").isFile) { "Missing authored pack fix: $name" }
        }
    }
}

val visualHarness by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[visualHarness.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[visualHarness.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

minecraft {
    mappings("official", property("minecraft_version") as String)
    copyIdeResources = true
    jarJar.enable()

    runs {
        configureEach {
            workingDirectory(project.file("run"))
            property("forge.logging.console.level", "debug")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }
        val baseClient = create("client")
        val baseServer = create("server") { arg("--nogui") }
        create("gameTestServer") {
            workingDirectory(nativeGameTestDirectory.get().asFile)
            property("forge.enableGameTest", "true")
            property("forge.gameTestServer", "true")
            property("forge.enabledGameTestNamespaces", property("mod_id") as String)
            mods { named(property("mod_id") as String) { source(nativeFixture) } }
            arg("--nogui")
        }
        create("visualServer") {
            parent(baseServer)
            workingDirectory(project.file("run-visual-server"))
            property("bettercontent.resident.debugSites", "true")
            mods { create("better_spirit_commerce_visual_harness") { source(visualHarness) } }
        }
        create("visualClient") {
            parent(baseClient)
            workingDirectory(project.file("run-visual-client"))
            args("--quickPlayMultiplayer", "127.0.0.1:25565", "--width", "1600", "--height", "900")
            mods { create("better_spirit_commerce_visual_harness") { source(visualHarness) } }
        }
    }
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://maven.createmod.net")
    maven("https://maven.ithundxr.dev/mirror")
    maven("https://maven.tterrag.com/")
    maven("https://harleyoconnor.com/maven")
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.llamalad7.mixinextras.org/releases/")
    maven("https://maven.valkyrienskies.org") { content { includeGroup("org.valkyrienskies.core") } }
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
    mavenCentral()
}

// CI and release builds provide verified runtime JARs explicitly. Ordinary local builds
// use the canonical sibling artifact produced by Dimension Drink's stageRuntimeJar task.
val providerDirectory = providers.environmentVariable("BC_CUSTOM_MOD_JAR_DIR").orNull
require(providerDirectory == null || providerDirectory.isNotBlank()) {
    "BC_CUSTOM_MOD_JAR_DIR must not be blank"
}
val celestialEventsJar = if (providerDirectory == null) {
    file("../better-celestial-events/build/libs/better-celestial-events-0.1.0.jar")
} else {
    file(providerDirectory).resolve("better-celestial-events-0.1.0.jar")
}
require(celestialEventsJar.isFile) {
    "Missing optional compile provider better-celestial-events-0.1.0.jar at $celestialEventsJar"
}
val dimensionDrinkJar = if (providerDirectory == null) {
    file("../better-dimension-fonts/build/libs/better-dimension-fonts-1.0.0.jar")
} else {
    file(providerDirectory).resolve("better-dimension-fonts-1.0.0.jar")
}
require(dimensionDrinkJar.isFile) {
    "Missing Better Content provider better-dimension-fonts-1.0.0.jar at $dimensionDrinkJar; prepare BC_CUSTOM_MOD_JAR_DIR or build better-dimension-fonts first"
}
val betterContentFixesJar = if (providerDirectory == null) {
    file("../better-compat-fixes/build/libs/better-compat-fixes-0.1.9.jar")
} else {
    file(providerDirectory).resolve("better-compat-fixes-0.1.9.jar")
}
require(betterContentFixesJar.isFile) {
    "Missing Better Content provider better-compat-fixes-0.1.9.jar at $betterContentFixesJar; prepare BC_CUSTOM_MOD_JAR_DIR or build better-compat-fixes first"
}
val notificationsJar = if (providerDirectory == null) {
    file("../better-gameplay-notices/build/libs/better-gameplay-notices-1.0.0.jar")
} else {
    file(providerDirectory).resolve("better-gameplay-notices-1.0.0.jar")
}
require(notificationsJar.isFile) {
    "Missing Better Content provider better-gameplay-notices-1.0.0.jar at $notificationsJar"
}
// Resolve sibling reobfuscated mods through ForgeGradle so the GameTest dev
// runtime remaps them into the same names as its Minecraft classes.
repositories {
    ivy {
        name = "celestialEventsLocal"
        url = uri(celestialEventsJar.parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local.celestial") }
    }
    ivy {
        name = "dimensionDrinkLocal"
        url = uri(dimensionDrinkJar.parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local.dimensiondrink") }
    }
    ivy {
        name = "betterContentFixesLocal"
        url = uri(betterContentFixesJar.parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local.fixes") }
    }
    ivy {
        name = "betterContentNotificationsLocal"
        url = uri(notificationsJar.parentFile)
        patternLayout { artifact("[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
        content { includeGroup("bettercontent.local.notifications") }
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:${property("minecraft_version")}-${property("forge_version")}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    implementation(fg.deobf("com.simibubi.create:create-${property("minecraft_version")}:${property("create_version")}:slim"))
    implementation(fg.deobf("net.createmod.ponder:Ponder-Forge-${property("minecraft_version")}:${property("ponder_version")}"))
    compileOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-api-${property("minecraft_version")}:${property("flywheel_version")}"))
    runtimeOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-${property("minecraft_version")}:${property("flywheel_version")}"))
    implementation(fg.deobf("com.tterrag.registrate:Registrate:${property("registrate_version")}"))
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:[0.5.0,0.6.0)")!!)
    implementation(jarJar("net.java.dev.jna:jna:[5.14.0,5.14.0]")!!)
    implementation(jarJar("net.java.dev.jna:jna-platform:[5.14.0,5.14.0]")!!)
    compileOnly(fg.deobf("bettercontent.local.celestial:better-celestial-events:0.1.0"))
    // Default native gate proves the optional provider can be absent. Opt in for the present profile.
    if (providers.gradleProperty("celestialRuntime").orNull == "true") {
        runtimeOnly(fg.deobf("bettercontent.local.celestial:better-celestial-events:0.1.0"))
    }
    compileOnly(fg.deobf("bettercontent.local.dimensiondrink:better-dimension-fonts:1.0.0"))
    // ForgeGradle's GameTest launch uses the main runtime classpath. Keep the
    // provider mod available there as well as at compile time.
    runtimeOnly(fg.deobf("bettercontent.local.dimensiondrink:better-dimension-fonts:1.0.0"))
    // Flat file dependencies do not carry Forge mod dependencies transitively.
    runtimeOnly(fg.deobf("bettercontent.local.fixes:better-compat-fixes:0.1.9"))
    runtimeOnly(fg.deobf("bettercontent.local.notifications:better-gameplay-notices:1.0.0"))
    runtimeOnly(fg.deobf("curse.maven:kotlin-for-forge-351264:7291067"))
    compileOnly(fg.deobf("curse.maven:hyle-609850:7736352"))
    compileOnly(fg.deobf("curse.maven:thirst-was-taken-679270:6660408"))
    compileOnly(fg.deobf("curse.maven:cold-sweat-506194:7893262"))
    compileOnly(fg.deobf("curse.maven:pollution-of-the-realms-269973:8554528"))
    compileOnly(fg.deobf("curse.maven:little-logistics-570050:4799459"))
    compileOnly(fg.deobf("curse.maven:weather-storms-tornadoes-237746:5244118"))
    // Native bytecode contracts for the optional campfire smoke integration, not runtime requirements.
    testRuntimeOnly(fg.deobf("curse.maven:advanced-chimneys-244830:8499852"))
    testRuntimeOnly(fg.deobf("curse.maven:forgeendertech-244844:8554308"))
    testRuntimeOnly(fg.deobf("curse.maven:weather-storms-tornadoes-237746:5244118"))
    compileOnly(fg.deobf("curse.maven:creativecore-257814:7649757"))
    compileOnly(fg.deobf("curse.maven:ambientsounds-254284:7550220"))
    compileOnly(fg.deobf("curse.maven:oculus-581495:6020952"))
    compileOnly(fg.deobf("curse.maven:sophisticated-core-618298:7916595"))
    compileOnly(fg.deobf("curse.maven:sophisticated-storage-619320:7973265"))
    compileOnly(fg.deobf("curse.maven:curios-api-309927:6418456"))
    compileOnly(fg.deobf("curse.maven:mantle-74924:7563777"))
    compileOnly(fg.deobf("curse.maven:tinkers-construct-74072:7449219"))
    compileOnly(fg.deobf("curse.maven:farmers-delight-398521:8007609"))
    compileOnly(fg.deobf("curse.maven:hexerei-548599:6314111"))
    compileOnly(fg.deobf("curse.maven:polymorph-388800:6450982"))
    compileOnly(fg.deobf("curse.maven:architectury-api-419699:5137938"))
    compileOnly(fg.deobf("curse.maven:epic-fight-mod-405076:8049910"))
    compileOnly(fg.deobf("curse.maven:valkyrien-skies-258371:7906689"))
    compileOnly(fg.deobf("curse.maven:realistic-block-physics-375616:6393411"))
    compileOnly(fg.deobf("curse.maven:realistic-physics-1030082:6026115"))
    compileOnly(fg.deobf("curse.maven:rehooked-1096531:6341096"))
    testRuntimeOnly(fg.deobf("curse.maven:rehooked-1096531:6341096"))
    compileOnly(fg.deobf("curse.maven:patchouli-306770:7731017"))
    compileOnly(fg.deobf("curse.maven:lodestone-616457:6213794"))
    compileOnly(fg.deobf("curse.maven:malum-484064:6646111"))
    compileOnly("org.valkyrienskies.core:api:1.1.0+cf208d8b56")
    runtimeOnly(fg.deobf("curse.maven:curios-api-309927:6418456"))
    runtimeOnly(fg.deobf("curse.maven:lodestone-616457:6213794"))
    runtimeOnly(fg.deobf("curse.maven:malum-484064:6646111"))
    // Exact committed pack pin; Malum ships unconditional jeed:effect_provider recipes.
    // Dev/native runtime only, not a production Commerce dependency or bundled artifact.
    runtimeOnly(fg.deobf("curse.maven:jeed-532286:6172935"))
    runtimeOnly(fg.deobf("curse.maven:jei-238222:7920915"))
    runtimeOnly(fg.deobf("curse.maven:rats-323596:5904296"))
    runtimeOnly(fg.deobf("curse.maven:citadel-331936:7476570"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
}

tasks.named<Jar>("jar") {
    finalizedBy("reobfJar")
}

val stageRuntimeJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Stages the reobfuscated runtime jar into build/libs using the canonical release filename."
    dependsOn(tasks.named("reobfJar"))
    mustRunAfter(tasks.named("jarJar"))
    mustRunAfter(tasks.named("reobfJarJar"))
    from(layout.buildDirectory.file("reobfJar/output.jar"))
    into(layout.buildDirectory.dir("libs"))
    rename { "${base.archivesName.get()}-$version.jar" }
    doLast {
        val runtime = destinationDir.resolve("${base.archivesName.get()}-$version.jar")
        val profileOnly = zipTree(runtime).matching {
            include(malumAdvancementFixes.map { "data/malum/advancements/recipes/$it.json" })
            include("data/malum/recipes/create/crushing/crush_rare_earths.json")
            include("com/bettercontent/spiritcommerce/nativefixture/**")
        }
        check(profileOnly.isEmpty) { "Native-only pack fixes leaked into the production Commerce JAR" }
    }
}

tasks.named("assemble") {
    dependsOn(stageRuntimeJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.named("compileVisualHarnessJava") { dependsOn(tasks.named("classes")) }
tasks.withType<JavaExec>().configureEach {
    if (name == "runVisualServer") standardInput = System.`in`
}

tasks.test {
    dependsOn(tasks.named(nativeFixture.processResourcesTaskName))
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val nativeDiagnosticsTest by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(tasks.named("classes"))
    commandLine("python3", "-B", "-m", "unittest", "discover", "-s", "scripts", "-p", "test_native_diagnostics.py")
}
tasks.named("check") { dependsOn(nativeDiagnosticsTest) }

tasks.register("headlessGameTest") {
    group = "verification"
    description = "Runs Forge game tests in a headless dedicated server."
    dependsOn(tasks.named("runGameTestServer"))
    doLast {
        val log = nativeGameTestDirectory.get().file("logs/latest.log").asFile
        check(log.isFile && Regex("All [0-9]+ required tests passed").containsMatchIn(log.readText())) {
            "Forge GameTests did not pass; inspect ${log.absolutePath}"
        }
        exec { commandLine("python3", "-B", "scripts/check_native_diagnostics.py", log.absolutePath) }
    }
}

tasks.register("verifyFast") {
    group = "verification"
    description = "Runs deterministic unit/resource checks without Forge game tests."
    dependsOn(tasks.named("check"))
}

tasks.register("verifyFull") {
    group = "verification"
    description = "Runs the full verification lane including headless Forge game tests."
    dependsOn(tasks.named("verifyFast"))
    dependsOn(tasks.named("headlessGameTest"))
    dependsOn("verifyNativeRunFixture")
}

tasks.register("verifyVisualHarness") {
    group = "verification"
    description = "Checks that the trader camp visual harness produced its reviewed screenshot set."
    doLast {
        val root = layout.projectDirectory.dir("run-visual-client/screenshots").asFile
        val captures = listOf("camps-overview.png", "awning-profile.png", "awning-detail.png")
        captures.forEach { name ->
            val image = root.resolve(name)
            if (!image.isFile || image.length() == 0L) {
                throw GradleException("Trader camp visual harness did not produce $name")
            }
        }
    }
}

tasks.register("verifyMarketVisualHarness") {
    group = "verification"
    description = "Checks that the Local Market real-client screenshot set was captured."
    doLast {
        val root = layout.projectDirectory.dir("run-visual-client/screenshots").asFile
        listOf("market-populated.png", "market-filtered.png", "market-empty.png",
            "market-no-matches.png", "market-narrow.png",
            "market-selected.png")
            .forEach { name ->
                val image = root.resolve(name)
                if (!image.isFile || image.length() == 0L) {
                    throw GradleException("Local Market visual harness did not produce $name")
                }
            }
    }
}

val resetGameTestMods = tasks.register("resetGameTestMods") {
    description = "Prepares an isolated UUID GameTest directory without deleting prior fixtures."
    doLast { nativeGameTestDirectory.get().asFile.mkdirs() }
}

tasks.register("verifyNativeRunFixture") {
    group = "verification"
    doLast {
        check(file(minecraft.runs.getByName("gameTestServer").workingDirectory).canonicalFile ==
            nativeGameTestDirectory.get().asFile.canonicalFile) { "Native RunConfig/setup directory mismatch" }
        val fixture = layout.buildDirectory.dir("native-run-guard/${UUID.randomUUID()}").get().asFile
        provisionNativeProperties(fixture)
        val properties = fixture.resolve("server.properties")
        check(properties.readText() == "online-mode=false\nenforce-secure-profile=false\n")
        val custom = "# retained exact bytes\r\nonline-mode=true\r\n".toByteArray()
        properties.writeBytes(custom)
        provisionNativeProperties(fixture)
        check(properties.readBytes().contentEquals(custom)) { "Existing native properties overwritten" }
    }
}

val syncGameTestStructures = tasks.register<Sync>("syncGameTestStructures") {
    from(layout.projectDirectory.dir("src/main/resources/gameteststructures"))
    into(nativeGameTestDirectory.map { it.dir("gameteststructures") })
}

val prepareNativeRuntime by tasks.registering {
    dependsOn(resetGameTestMods, tasks.named("compileJava"), tasks.named("processResources"),
        tasks.named(nativeFixture.processResourcesTaskName))
    doLast {
        check(layout.buildDirectory.file("resources/nativeFixture/better_spirit_commerce.refmap.json").get().asFile.isFile) {
            "Native dev classpath is missing its generated mixin refmap"
        }
        provisionNativeProperties(nativeGameTestDirectory.get().asFile)
        nativeGameTestDirectory.get().file("native-diagnostic-nonce").asFile.writeText(UUID.randomUUID().toString())
    }
}

tasks.matching { it.name.startsWith("prepareRunGameTestServer") }.configureEach {
    dependsOn(resetGameTestMods)
    dependsOn(syncGameTestStructures)
    dependsOn(prepareNativeRuntime)
    dependsOn(tasks.named(nativeFixture.classesTaskName))
}

tasks.processResources {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version")
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

mixin {
    add(sourceSets.main.get(), "better_spirit_commerce.refmap.json")
    config("better_spirit_commerce.mixins.json")
}
