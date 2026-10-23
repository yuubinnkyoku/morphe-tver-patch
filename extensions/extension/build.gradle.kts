extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"

    // NicoOverlayBridge uses Java text blocks for its self-contained HTML renderer.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
