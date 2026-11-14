package pf.brian.pehe;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

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
        @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class MusicLibraryPlugin extends Plugin {

    private boolean firstDevices = true;

    @Override
    public void load() {
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
            String kind = kind(d.getType());
            if (kind == null) continue;
            JSObject o = new JSObject();
            o.put(what, d.getProductName() != null && d.getProductName().length() > 0 ? d.getProductName().toString() : kind);
            o.put("kind", kind);
            o.put("headset", !kind.startsWith("Haut-parleur"));
            notifyListeners("outputsChanged", o);
            return;
        }
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
