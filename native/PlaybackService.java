package pf.brian.pehe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

/** Garde Pehe en vie pendant la lecture (écran éteint, autre app) et affiche les commandes. */
public class PlaybackService extends Service {
    static final String CHANNEL = "pehe_lecture";
    static final String ACT_UPDATE = "pf.brian.pehe.UPDATE";
    static final String ACT_STOP = "pf.brian.pehe.STOP";
    static final String ACT_PREV = "pf.brian.pehe.PREV";
    static final String ACT_TOGGLE = "pf.brian.pehe.TOGGLE";
    static final String ACT_NEXT = "pf.brian.pehe.NEXT";

    static MusicLibraryPlugin plugin;
    private PowerManager.WakeLock wake;
    private String title = "Pehe", artist = "";
    private boolean playing = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lecture en cours", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("Commandes de lecture de Pehe");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pehe:lecture");
        wake.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACT_STOP.equals(a)) {
            release();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACT_PREV.equals(a) || ACT_TOGGLE.equals(a) || ACT_NEXT.equals(a)) {
            if (plugin != null) plugin.mediaAction(a.equals(ACT_PREV) ? "prev" : a.equals(ACT_NEXT) ? "next" : "toggle");
            if (ACT_TOGGLE.equals(a)) playing = !playing;
        } else if (intent != null) {
            title = intent.getStringExtra("title") != null ? intent.getStringExtra("title") : "Pehe";
            artist = intent.getStringExtra("artist") != null ? intent.getStringExtra("artist") : "";
            playing = intent.getBooleanExtra("playing", false);
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
        if (playing) { if (!wake.isHeld()) wake.acquire(4 * 60 * 60 * 1000L); } else release();
        return START_NOT_STICKY;
    }

    private PendingIntent pi(String action, int code) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification build() {
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = PendingIntent.getActivity(this, 9, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(content)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_previous, "Précédent", pi(ACT_PREV, 1))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Lecture", pi(ACT_TOGGLE, 2))
            .addAction(android.R.drawable.ic_media_next, "Suivant", pi(ACT_NEXT, 3))
            .setDeleteIntent(pi(ACT_STOP, 4))
            .build();
    }

    private void release() {
        if (wake != null && wake.isHeld()) wake.release();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }
}
