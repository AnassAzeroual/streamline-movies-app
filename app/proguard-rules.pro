# libtorrent4j talks to its native layer entirely through JNI, so none of the
# swig bindings may be renamed or stripped.
-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.swig.** { *; }

# Gson reflects over the TMDb DTOs and the persistent download queue.
-keep class com.streamline.app.data.remote.dto.** { *; }
-keep class com.streamline.app.core.download.DownloadItem { *; }
-keep enum com.streamline.app.core.download.DownloadStatus { *; }
-keepattributes Signature, *Annotation*

# OkHttp/Retrofit optional platform hooks.
-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**
-dontwarn retrofit2.**
