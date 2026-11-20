# Room, WorkManager and OkHttp ship their own consumer rules.
# Workers are instantiated by WorkManager via reflection; keep their constructors.
-keep class ru.mtproxymanager.work.** { <init>(...); }
