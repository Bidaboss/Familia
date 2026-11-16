# Familia, R8 for the release APK (audit technique). Merged after the release of 21/11 only:
# a screen that only the release build shows could break the day of the demo.

# Keep every name: the traces sent to Administration > Plantages stay readable without a
# mapping file, and grouping by signature (core/crash) keeps working. Shrinking and
# optimisation still remove the unused code, which is most of the gain.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Firestore models: the app maps every document by hand (doc.getString…), never with
# toObject(), so no model needs a keep rule. If one is ever read with toObject(), keep it here:
# -keep class fr.familia.app.core.model.** { *; }

# Type-safe navigation routes and PendingCrash are @Serializable: kotlinx.serialization ships
# its own consumer rules. These keep the generated serializers of our classes explicitly.
-keepclassmembers @kotlinx.serialization.Serializable class fr.familia.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class fr.familia.app.**$$serializer { *; }

# Firebase Messaging starts our service by its name from the manifest (kept by AAPT), and the
# Credential Manager / Google ID libraries ship their own rules.
