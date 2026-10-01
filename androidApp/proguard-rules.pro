# Glance fuckups
-keep class androidx.work.InputMerger { *; }
-keep class * extends androidx.work.InputMerger { *; }
-keep class androidx.work.impl.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
# Glance persists widget/receiver class names across application upgrades.
-keepnames class com.shub39.grit.widgets.** extends androidx.glance.appwidget.GlanceAppWidget
-keepnames class com.shub39.grit.widgets.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver
