# Keep the app's own classes as they are: the release build then behaves exactly like the
# debug build it was tested as (enums looked up by name from saved settings, the maths engine).
# Libraries (Compose, Material) are still shrunk.
-keep class com.example.feynman.** { *; }
-keepclassmembers enum * { *; }
