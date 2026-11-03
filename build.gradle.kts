plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Optional: -Pterminwecker.buildRoot=C:/kurzer/pfad hält die Build-Ausgaben kurz,
// falls das Projekt in einem sehr tiefen Windows-Ordner liegt (MAX_PATH).
providers.gradleProperty("terminwecker.buildRoot").orNull?.let { root ->
    allprojects {
        layout.buildDirectory.set(file("$root/${project.name}"))
    }
}
