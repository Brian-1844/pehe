package pf.brian.pehe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Garde Pehe en vie pendant la lecture (écran éteint, autre app),
 * affiche les commandes (notification + écran verrouillé) et reçoit
 * les boutons des casques, enceintes et voitures Bluetooth.
 */
public class PlaybackService extends Service {
    static final String CHANNEL = "pehe_lecture";
    static final String ACT_UPDATE = "pf.brian.pehe.UPDATE";
    static final String ACT_STOP = "pf.brian.pehe.STOP";
    static final String ACT_PREV = "pf.brian.pehe.PREV";
    static final String ACT_TOGGLE = "pf.brian.pehe.TOGGLE";
    static final String ACT_NEXT = "pf.brian.pehe.NEXT";

    static MusicLibraryPlugin plugin;
    static PlaybackService instance;

    private PowerManager.WakeLock wake;
    private MediaSession session;
    private String title = "Pehe", artist = "";
    private boolean playing = false;
    private long position = 0, duration = 0;
    private float speed = 1f;
    private boolean foreground = false;
    private boolean recording = false, busy = false;
    private int fgType = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lecture en cours", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("Commandes de lecture de Pehe");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pehe:lecture");
        wake.setReferenceCounted(false);

        session = new MediaSession(this, "Pehe");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { send("play"); }
            @Override public void onPause() { send("pause"); }
            @Override public void onSkipToNext() { send("next"); }
            @Override public void onSkipToPrevious() { send("prev"); }
            @Override public void onStop() { send("pause"); }
            @Override public void onSeekTo(long pos) {
                if (plugin != null) plugin.mediaSeek(pos / 1000.0);
            }
        });
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open != null) session.setSessionActivity(PendingIntent.getActivity(this, 9, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        session.setActive(true);
    }

    private void send(String a) {
        if (plugin != null) plugin.mediaAction(a);
    }

    /** Mise à jour directe (sans relancer le service, donc permise en arrière-plan). */
    void update(String t, String ar, boolean pl, long pos, long dur, float sp, boolean rec, boolean bz) {
        recording = rec; busy = bz;
        title = t != null ? t : "Pehe";
        artist = ar != null ? ar : "";
        playing = pl; position = pos; duration = dur; speed = sp;
        refresh();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (ACT_STOP.equals(a)) {
            stopAll();
            return START_NOT_STICKY;
        }
        if (ACT_PREV.equals(a)) send("prev");
        else if (ACT_NEXT.equals(a)) send("next");
        else if (ACT_TOGGLE.equals(a)) send(playing ? "pause" : "play");
        else if (intent != null) {
            title = intent.getStringExtra("title");
            if (title == null) title = "Pehe";
            artist = intent.getStringExtra("artist");
            if (artist == null) artist = "";
            playing = intent.getBooleanExtra("playing", false);
            position = intent.getLongExtra("position", 0);
            duration = intent.getLongExtra("duration", 0);
            speed = intent.getFloatExtra("speed", 1f);
            recording = intent.getBooleanExtra("recording", false);
            busy = intent.getBooleanExtra("busy", false);
        }
        refresh();
        return START_NOT_STICKY;
    }

    private void refresh() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position, playing ? speed : 0f)
            .build());
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .build());
        Notification n = build();
        int type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK | (recording && Build.VERSION.SDK_INT >= 30 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0) : 0;
        if (!foreground || type != fgType) {
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, type);
                else startForeground(1, n);
            } catch (Exception e) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                else startForeground(1, n);
                type = Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
            }
            foreground = true;
            fgType = type;
        } else {
            getSystemService(NotificationManager.class).notify(1, n);
        }
        if (playing || recording || busy) { if (!wake.isHeld()) wake.acquire(6 * 60 * 60 * 1000L); } else if (wake.isHeld()) wake.release();
    }

    private PendingIntent pi(String action, int code) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(recording ? "● " + title : title)
            .setContentText(artist)
            .setContentIntent(PendingIntent.getActivity(this, 9, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(playing || recording || busy)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setDeleteIntent(pi(ACT_STOP, 4))
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Précédent", pi(ACT_PREV, 1)).build())
            .addAction(new Notification.Action.Builder(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Lecture", pi(ACT_TOGGLE, 2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Suivant", pi(ACT_NEXT, 3)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }

    private void stopAll() {
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(true);
        foreground = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (wake != null && wake.isHeld()) wake.release();
        if (session != null) { session.setActive(false); session.release(); }
        instance = null;
        super.onDestroy();
    }
}
