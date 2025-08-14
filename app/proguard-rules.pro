# Firebase Firestore
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.firebase.** { *; }

# Keep your data models - PENTING!
-keep class edu.unikom.focusflow.data.models.** { *; }
-keepclassmembers class edu.unikom.focusflow.data.models.** {
    public <init>();
    public <init>(...);
    <fields>;
    <methods>;
}

# Keep specific models with all constructors
-keep class edu.unikom.focusflow.data.models.Task {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.Subtask {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.TaskPriority {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.RecurrenceType {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.PomodoroSession {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.SessionType {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.UserProfile {
    public <init>();
    public <init>(...);
    *;
}

-keep class edu.unikom.focusflow.data.models.UserPreferences {
    public <init>();
    public <init>(...);
    *;
}

# Keep all enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final ** *;
}

# Keep Firestore annotations
-keep @com.google.firebase.firestore.DocumentId class * { *; }
-keep @com.google.firebase.firestore.PropertyName class * { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Keep Date and Calendar
-keep class java.util.Date { *; }
-keep class java.util.Calendar { *; }
-keep class java.text.SimpleDateFormat { *; }

# Keep Kotlin classes
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Keep data classes
-keepclassmembers class * {
    public synthetic <methods>;
}

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Prevent stripping of debug information
-keepattributes SourceFile,LineNumberTable

# Keep repository classes
-keep class edu.unikom.focusflow.data.repository.** { *; }

# Google Sign In (untuk menghilangkan deprecated warnings)
-dontwarn com.google.android.gms.auth.**
-dontwarn com.google.android.gms.common.**