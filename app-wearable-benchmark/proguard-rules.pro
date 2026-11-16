# TensorFlow Lite is invoked through its public Java API; retain native bindings.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Services are named explicitly from AndroidManifest.xml and ADB component names.
-keep class kaist.iclab.benchmark.wearable.BenchmarkService { *; }
-keep class kaist.iclab.benchmark.wearable.ReplayService { *; }
