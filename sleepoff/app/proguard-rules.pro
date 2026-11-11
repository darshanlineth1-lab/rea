# SleepOff ProGuard / R8 rules.
# The app uses no reflection-based serialization (DataStore Preferences + org.json),
# so only a few defensive keeps are needed.

# Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# DataStore
-keep class androidx.datastore.** { *; }

# Navigation Compose (route strings only, no reflection, but keep safe)
-keep class androidx.navigation.** { *; }

# Keep model classes referenced from JSON history parsing
-keep class com.sleepoff.app.domain.** { *; }
