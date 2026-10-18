group = "io.github.yuubinnkyoku"

patches {
    about {
        name = "yuubinnkyoku TVer patches"
        description = "Experimental picture-in-picture and ad suppression for TVer 7.2.1"
        source = "https://github.com/yuubinnkyoku/morphe-tver-patch"
        author = "yuubinnkyoku"
        contact = "https://github.com/yuubinnkyoku"
        website = "https://github.com/yuubinnkyoku/morphe-tver-patch"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
