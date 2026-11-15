# The native libraries bind to these classes/methods/fields by exact name via JNI.
# They MUST NOT be renamed or removed by R8/ProGuard.
-keep class com.tiqiaa.local.LocalIrDb { *; }
-keep class com.tiqiaa.local.LocalIrDb$* { *; }
-keep class com.icontrol.dev.IControlIRData { *; }
-keep class com.icontrol.dev.IrData { *; }
-keep class com.icontrol.dev.TiqiaaUsbController { *; }

# Keep all native method names everywhere
-keepclasseswithmembernames class * {
    native <methods>;
}
