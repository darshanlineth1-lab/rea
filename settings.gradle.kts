pluginManagement {
    repositories {
        val offlineMirror = file("/tmp/toolchain/m2")
        if (offlineMirror.exists()) {
            maven { url = offlineMirror.toURI() }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val offlineMirror = file("/tmp/toolchain/m2")
        if (offlineMirror.exists()) {
            maven { url = offlineMirror.toURI() }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "RegainFocus"
include(":app")
