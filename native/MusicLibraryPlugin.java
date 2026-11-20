package pf.brian.pehe;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Pehe : chansons du téléphone, partage, appareils audio, mises à jour. */
@CapacitorPlugin(
    name = "MusicLibrary",
    permissions = {
        @Permission(alias = "audio", strings = { Manifest.permission.READ_MEDIA_AUDIO }),
        @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE }),
        @Permission(alias = "notif", strings = { "android.permission.POST_NOTIFICATIONS" })
    }
)
public class MusicLibraryPlugin extends Plugin {

    private boolean firstDevices = true;

    // ---------- Lecture en arrière-plan ----------
    void mediaAction(String a) {
        JSObject o = new JSObject();
        o.put("action", a);
        notifyListeners("mediaAction", o);
    }

    @PluginMethod
    public void bgUpdate(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notif") != PermissionState.GRANTED && !notifAsked) {
            notifAsked = true;
            requestPermissionForAlias("notif", call, "notifCallback");
            return;
        }
        startBg(call);
    }

    private boolean notifAsked = false;

    @PermissionCallback
    private void notifCallback(PluginCall call) { startBg(call); }

    private void startBg(PluginCall call) {
        try {
            String t = call.getString("title", "Pehe"), a = call.getString("artist", "");
            boolean pl = call.getBoolean("playing", false);
            long pos = (long) (call.getDouble("position", 0.0) * 1000), dur = (long) (call.getDouble("duration", 0.0) * 1000);
            float sp = call.getFloat("speed", 1f);
            boolean rec = call.getBoolean("recording", false), bz = call.getBoolean("busy", false);
            if (PlaybackService.instance != null) {
                PlaybackService.instance.update(t, a, pl, pos, dur, sp, rec, bz);
            } else {
                Intent i = new Intent(getContext(), PlaybackService.class).setAction(PlaybackService.ACT_UPDATE);
                i.putExtra("title", t);
                i.putExtra("artist", a);
                i.putExtra("playing", pl);
                i.putExtra("position", pos);
                i.putExtra("duration", dur);
                i.putExtra("speed", sp);
                i.putExtra("recording", rec);
                i.putExtra("busy", bz);
                androidx.core.content.ContextCompat.startForegroundService(getContext(), i);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    void mediaSeek(double sec) {
        JSObject o = new JSObject();
        o.put("action", "seek");
        o.put("time", sec);
        notifyListeners("mediaAction", o);
    }

    @PluginMethod
    public void bgStop(PluginCall call) {
        try {
            getContext().startService(new Intent(getContext(), PlaybackService.class).setAction(PlaybackService.ACT_STOP));
        } catch (Exception ignored) {}
        call.resolve();
    }

    @Override
    public void load() {
        PlaybackService.plugin = this;
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        am.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                if (firstDevices) { firstDevices = false; return; }
                send(added, "added");
            }
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                send(removed, "removed");
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void send(AudioDeviceInfo[] list, String what) {
        for (AudioDeviceInfo d : list) {
            int t = d.getType();
            // On ignore le canal « appels » (SCO) qui s'ouvre et se ferme tout seul
            if (t == 7) continue;
            String kind = kind(t);
            if (kind == null || kind.startsWith("Haut-parleur")) continue;
            JSObject o = new JSObject();
            o.put(what, d.getProductName() != null && d.getProductName().length() > 0 ? d.getProductName().toString() : kind);
            o.put("kind", kind);
            o.put("headset", true);
            o.put("stillHeadset", headsetConnected());
            notifyListeners("outputsChanged", o);
            return;
        }
    }

    private boolean headsetConnected() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == 8 || t == 26 || t == 27 || t == 3 || t == 4 || t == 22 || t == 11 || t == 12) return true;
        }
        return false;
    }

    private static String kind(int t) {
        switch (t) {
            case 8: return "Bluetooth (musique)";
            case 7: return "Bluetooth (appels)";
            case 26: case 27: return "Bluetooth LE";
            case 3: case 4: return "Casque filaire";
            case 22: case 11: case 12: return "Casque USB";
            case 2: return "Haut-parleur du téléphone";
            case 9: case 10: return "HDMI";
            default: return null;
        }
    }

    @PluginMethod
    public void outputs(PluginCall call) {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        JSArray arr = new JSArray();
        JSArray speakers = new JSArray();
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            String kind = kind(d.getType());
            if (kind == null) continue;
            JSObject o = new JSObject();
            String name = d.getProductName() != null ? d.getProductName().toString() : "";
            o.put("name", kind.startsWith("Haut-parleur") ? "Haut-parleur" : (name.isEmpty() ? kind : name));
            o.put("kind", kind);
            if (kind.startsWith("Haut-parleur")) speakers.put(o); else arr.put(o);
        }
        for (int i = 0; i < speakers.length(); i++) { try { arr.put(speakers.get(i)); } catch (Exception ignored) {} }
        JSObject ret = new JSObject();
        ret.put("devices", arr);
        call.resolve(ret);
    }

    @PluginMethod
    public void openBluetooth(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url")));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void info(PluginCall call) {
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            JSObject ret = new JSObject();
            ret.put("versionName", pi.versionName);
            ret.put("versionCode", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private void shareUri(Uri uri, String mime, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        if (text != null) i.putExtra(Intent.EXTRA_TEXT, text);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent c = Intent.createChooser(i, "Partager");
        c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getContext().startActivity(c);
    }

    @PluginMethod
    public void share(PluginCall call) {
        try {
            shareUri(Uri.parse(call.getString("uri")), "audio/*", call.getString("text"));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /** Enregistre un fichier (Word, texte) dans Téléchargements/Pehe. */
    @PluginMethod
    public void saveDownload(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29) { call.reject("Android trop ancien", "OLD"); return; }
        try {
            byte[] data = Base64.decode(call.getString("data"), Base64.DEFAULT);
            String name = call.getString("name", "pehe.txt").replaceAll("[\\\\/:*?\"<>|]", "_");
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, call.getString("mime", "application/octet-stream"));
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pehe");
            Uri u = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("Dossier Téléchargements indisponible");
            try (OutputStream o = getContext().getContentResolver().openOutputStream(u)) { o.write(data); }
            JSObject ret = new JSObject();
            ret.put("uri", u.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void shareFile(PluginCall call) {
        try {
            byte[] data = Base64.decode(call.getString("data"), Base64.DEFAULT);
            String name = call.getString("name", "pehe.webm").replaceAll("[\\/:*?\"<>|]", "_");
            File dir = new File(getContext().getCacheDir(), "partage");
            dir.mkdirs();
            File f = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            shareUri(uri, call.getString("mime", "audio/*"), call.getString("text"));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    // ---------- Mise à jour interne ----------
    private volatile boolean dlCancel = false;
    private volatile boolean dlRunning = false;

    private File updateFile() {
        File dir = new File(getContext().getCacheDir(), "maj");
        dir.mkdirs();
        return new File(dir, "Pehe-maj.apk");
    }

    @PluginMethod
    public void downloadUpdate(final PluginCall call) {
        final String url = call.getString("url");
        final long sizeHint = call.getLong("size", 0L);
        if (url == null) { call.reject("Adresse manquante"); return; }
        if (dlRunning) { call.reject("Téléchargement déjà en cours"); return; }
        dlCancel = false;
        dlRunning = true;
        new Thread(() -> {
            HttpURLConnection con = null;
            File out = updateFile();
            try {
                String u = url;
                for (int i = 0; i < 6; i++) {
                    con = (HttpURLConnection) new URL(u).openConnection();
                    con.setInstanceFollowRedirects(false);
                    con.setConnectTimeout(20000);
                    con.setReadTimeout(30000);
                    con.setRequestProperty("User-Agent", "Pehe");
                    int code = con.getResponseCode();
                    if (code >= 300 && code < 400) { u = con.getHeaderField("Location"); con.disconnect(); continue; }
                    if (code != 200) throw new Exception("Erreur serveur " + code);
                    break;
                }
                long total = con.getContentLength();
                if (total <= 0) total = sizeHint;
                long done = 0, lastNotify = 0;
                byte[] buf = new byte[64 * 1024];
                try (InputStream in = con.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (dlCancel) throw new Exception("ANNULE");
                        fo.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastNotify > 150) {
                            lastNotify = now;
                            JSObject o = new JSObject();
                            o.put("done", done);
                            o.put("total", total);
                            notifyListeners("updateProgress", o);
                        }
                    }
                }
                JSObject o = new JSObject();
                o.put("done", done);
                o.put("total", total > 0 ? total : done);
                notifyListeners("updateProgress", o);
                JSObject ret = new JSObject();
                ret.put("size", done);
                call.resolve(ret);
            } catch (Exception e) {
                out.delete();
                if ("ANNULE".equals(e.getMessage())) call.reject("Annulé", "CANCELLED");
                else call.reject("Téléchargement impossible : " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
                dlRunning = false;
            }
        }).start();
    }

    @PluginMethod
    public void cancelUpdate(PluginCall call) {
        dlCancel = true;
        call.resolve();
    }

    @PluginMethod
    public void installUpdate(PluginCall call) {
        File f = updateFile();
        if (!f.exists()) { call.reject("Aucune mise à jour téléchargée"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
            JSObject ret = new JSObject();
            ret.put("needPermission", true);
            call.resolve(ret);
            return;
        }
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        JSObject ret = new JSObject();
        ret.put("needPermission", false);
        call.resolve(ret);
    }

    private String alias() {
        return Build.VERSION.SDK_INT >= 33 ? "audio" : "storage";
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "permCallback");
            return;
        }
        doScan(call);
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) doScan(call);
        else call.reject("Permission refusée", "DENIED");
    }

    private void doScan(PluginCall call) {
        JSArray tracks = new JSArray();
        Uri col = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DISPLAY_NAME
        };
        String sel = "(" + MediaStore.Audio.Media.IS_MUSIC + " != 0 OR " + MediaStore.Audio.Media.MIME_TYPE
            + " = 'audio/mpeg') AND " + MediaStore.Audio.Media.DURATION + " >= 5000";
        try (Cursor c = getContext().getContentResolver().query(col, proj, sel, null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    JSObject t = new JSObject();
                    t.put("uri", ContentUris.withAppendedId(col, id).toString());
                    t.put("title", c.getString(1));
                    t.put("artist", c.getString(2));
                    t.put("album", c.getString(3));
                    t.put("duration", c.getLong(4));
                    t.put("added", c.getLong(5));
                    t.put("name", c.getString(6));
                    tracks.put(t);
                }
            }
        } catch (Exception e) {
            call.reject("Lecture impossible : " + e.getMessage());
            return;
        }
        JSObject ret = new JSObject();
        ret.put("tracks", tracks);
        call.resolve(ret);
    }
}
