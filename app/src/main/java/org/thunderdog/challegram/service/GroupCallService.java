/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;

/**
 * Foreground service that keeps a joined video chat alive while the app is
 * backgrounded: posts the ongoing-call notification (PHONE_CALL|MICROPHONE
 * foreground type, applied automatically by {@link U#startForeground} for
 * {@link TdlibNotificationManager#ID_ONGOING_CALL_NOTIFICATION}), holds audio
 * focus, and puts the audio stack into communication mode.
 *
 * <p>Lifecycle is driven by {@code GroupCallManager} ({@link #start}/{@link #stop}).
 * Audio device routing (speaker / earpiece / Bluetooth selection) is a follow-up;
 * the WebRTC audio module captures the mic and plays through the default device.</p>
 */
public class GroupCallService extends Service implements AudioManager.OnAudioFocusChangeListener {
  private static final String CHANNEL_ID = "tgx_group_calls";
  private static final String ACTION_START = "org.thunderdog.challegram.GROUP_CALL_START";
  private static final String ACTION_STOP = "org.thunderdog.challegram.GROUP_CALL_STOP";

  public static void start (Context context) {
    Intent intent = new Intent(context, GroupCallService.class).setAction(ACTION_START);
    try {
      ContextCompat.startForegroundService(context, intent);
    } catch (Throwable t) {
      Log.e(Log.TAG_VOIP, "Unable to start GroupCallService", t);
    }
  }

  public static void stop (Context context) {
    // Use stopService (allowed from the background) rather than startService with a
    // STOP action — a background startService throws on Android 8+/12+ and would
    // leave the foreground notification + audio focus + IN_COMMUNICATION mode stuck.
    // stopService triggers onDestroy -> releaseAudio and removes the notification.
    try {
      context.stopService(new Intent(context, GroupCallService.class));
    } catch (Throwable t) {
      // Service may already be gone; ignore.
    }
  }

  private boolean inForeground;

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    String action = intent != null ? intent.getAction() : null;
    if (ACTION_STOP.equals(action)) {
      stopCall();
      return START_NOT_STICKY;
    }
    startCall();
    return START_NOT_STICKY;
  }

  private void startCall () {
    if (!inForeground) {
      U.startForeground(this, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION, buildNotification());
      inForeground = true;
      acquireAudio();
    }
  }

  private void stopCall () {
    releaseAudio();
    U.stopForeground(this, true, TdlibNotificationManager.ID_ONGOING_CALL_NOTIFICATION);
    inForeground = false;
    stopSelf();
  }

  private Notification buildNotification () {
    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
      NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
      if (channel == null) {
        channel = new NotificationChannel(CHANNEL_ID, Lang.getString(R.string.VoiceChat), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
      }
    }
    PendingIntent contentIntent = PendingIntent.getActivity(UI.getContext(), 0,
      Intents.valueOfCall(), PendingIntent.FLAG_UPDATE_CURRENT | Intents.mutabilityFlags(false));
    return new NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.baseline_phone_24_white)
      .setContentTitle(Lang.getString(R.string.VoiceChat))
      .setContentText(Lang.getString(R.string.VoipOngoingGroupCall))
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setContentIntent(contentIntent)
      .build();
  }

  private void acquireAudio () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (am == null) {
      return;
    }
    try {
      am.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
      am.setMode(AudioManager.MODE_IN_COMMUNICATION);
    } catch (Throwable t) {
      Log.w(Log.TAG_VOIP, "Unable to acquire audio for group call", t);
    }
  }

  private void releaseAudio () {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (am == null) {
      return;
    }
    try {
      am.abandonAudioFocus(this);
      am.setMode(AudioManager.MODE_NORMAL);
    } catch (Throwable t) {
      Log.w(Log.TAG_VOIP, "Unable to release audio for group call", t);
    }
  }

  @Override
  public void onAudioFocusChange (int focusChange) {
    // No-op: tgcalls manages capture/playback; focus loss is handled by the OS.
  }

  @Override
  public void onDestroy () {
    releaseAudio();
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }
}
