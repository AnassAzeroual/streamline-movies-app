-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.swig.** { *; }

-keep class com.streamline.app.core.download.DownloadItem { *; }
-keep enum com.streamline.app.core.download.DownloadStatus { *; }
-keep class com.streamline.app.core.playback.PlaybackHistoryEntry { *; }
-keepattributes Signature, *Annotation*

-dontwarn org.conscrypt.**
-dontwarn okhttp3.internal.platform.**
-dontwarn retrofit2.**
