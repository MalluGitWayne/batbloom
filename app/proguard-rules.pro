# Batbloom release build rules (R8).

# Enum names are saved in settings and backups (theme, plant kind): keep them stable across versions.
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembernames enum com.malluwayne.batbloom.** { <fields>; }

# Room: generated database and DAO implementations are created by reflection.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.malluwayne.batbloom.data.AppDatabase_Impl { *; }

# Readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
