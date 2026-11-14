"""Ajoute le module « bibliothèque musicale », les permissions et le numéro de version au projet Android."""
import os, re, shutil, sys

run = sys.argv[1] if len(sys.argv) > 1 else "1"
java_dir = "android/app/src/main/java/pf/brian/pehe"
os.makedirs(java_dir, exist_ok=True)
for f in ("MusicLibraryPlugin.java", "MainActivity.java"):
    shutil.copy(os.path.join("native", f), os.path.join(java_dir, f))

man = "android/app/src/main/AndroidManifest.xml"
m = open(man, encoding="utf-8").read()
perms = ('    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />\n'
         '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />\n'
         '    <uses-permission android:name="android.permission.RECORD_AUDIO" />\n'
         '    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />\n')
if "READ_MEDIA_AUDIO" not in m:
    m = m.replace("    <application", perms + "\n    <application", 1)
open(man, "w", encoding="utf-8").write(m)

gr = "android/app/build.gradle"
g = open(gr, encoding="utf-8").read()
g = re.sub(r"versionCode \d+", f"versionCode {run}", g)
g = re.sub(r'versionName "[^"]*"', f'versionName "1.0.{run}"', g)
open(gr, "w", encoding="utf-8").write(g)
print("Projet Android préparé, version 1.0." + run)
