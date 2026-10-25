# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# Inflated by name from layout/view_adzan_overlay.xml, which R8 cannot see. Without this keep the
# constructors are shrunk away and the adzan overlay fails to inflate. Nothing else needs a rule: the
# activities and services are named in the manifest, which R8 reads, and no code here is reflective.
-keep class com.gathin.tvhome.FullScreenTextureView { *; }
