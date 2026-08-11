# Minification is off for now; this file exists so the build config can reference it.
# When R8 is turned on, kotlinx.serialization needs its serializers kept:
# -keepclassmembers class dev.fand1l.pixelfloat.** { *** Companion; }
# -keepclasseswithmembers class dev.fand1l.pixelfloat.** { kotlinx.serialization.KSerializer serializer(...); }
