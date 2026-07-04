# Author: Xia Zihang
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep model classes used by org.json deserialization
-keep class iss.nus.edu.sg.ca_application.model.** { *; }

# Keep Live2D Cubism native libraries
-keep class com.live2d.** { *; }
-keep class jp.live2d.** { *; }

# Keep Retrofit interfaces (if added later)
# -keep,allowobfuscation,allowshrinking interface retrofit2.Call
# -keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep data classes
-keepattributes *Annotation*
-keepattributes Signature
