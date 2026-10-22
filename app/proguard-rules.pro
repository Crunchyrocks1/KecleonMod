# Keep Android components referenced in AndroidManifest (auto-kept, listed for clarity)
-keep public class dev.rootcatch.ugphone.MainActivity
-keep public class dev.rootcatch.ugphone.FarmService

# Auto-generated class referenced by FarmSession at runtime
-keep class dev.rootcatch.ugphone.AgentBuild { *; }

# Keep all enums intact (switch statements rely on ordinal/name)
-keepclassmembers enum * { *; }

# Keep Parcelable/Serializable if used
-keepclassmembers class * implements java.io.Serializable { static final long serialVersionUID; }

# Preserve line numbers in stack traces for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep JSONObject field names used in engine message parsing
-keepclassmembers class org.json.** { *; }
