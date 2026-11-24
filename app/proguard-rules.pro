# Familia: rules for R8 in the release and essai builds (audit du 22/11).
# The libraries (Firebase, Media3, Credentials, kotlinx.serialization, Coil, Compose) bring their
# own consumer rules; only what reads our own code by name is kept here.

# Shrink and optimize, but keep the names: crash reports (core/crash) group and show the class
# names, and the icon aliases and shared wishes build component names from our package.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Navigation routes and every @Serializable class of the app: their serializers are found by
# name from the companion object.
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class fr.familia.app.** { *; }
-keepclassmembers class fr.familia.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Sign-in with Google: the Play Services provider is created by reflection by Credential Manager.
-keep class androidx.credentials.playservices.** { *; }

# Components named in the manifest are kept by AAPT; the messaging service is kept again for
# safety, since a lost push service fails silently.
-keep class fr.familia.app.core.notifications.FamiliaMessagingService { *; }

# « Plantages » reads the reason of a map failure from its class name (MapUnavailable$Timeout, …):
# keep each one whole, so R8 neither renames nor merges them (24/11: a ViewFailed came as Timeout).
-keep class fr.familia.app.feature.trips.map.MapUnavailable { *; }
-keep class fr.familia.app.feature.trips.map.MapUnavailable$* { *; }
