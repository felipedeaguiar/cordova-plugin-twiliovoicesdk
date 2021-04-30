package com.phonegap.plugins.twiliovoice.fcm;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import static android.R.attr.data;
import static android.R.attr.packageNames;

import com.phonegap.plugins.twiliovoice.SoundPoolManager;
import com.phonegap.plugins.twiliovoice.TwilioVoicePlugin;

import java.util.Map;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "VoiceFCMService";
    private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";
    private static final String VOICE_CHANNEL = "default";
    public static final String VOICE_CHANNEL_LOW_IMPORTANCE = "notification-channel-low-importance";
    public static final String VOICE_CHANNEL_HIGH_IMPORTANCE = "notification-channel-high-importance";
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String CANCELLED_CALL_INVITE = "CANCELLED_CALL_INVITE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_ACCEPT = "ACTION_ACCEPT";
    public static final String ACTION_REJECT = "ACTION_REJECT";
    public static final String ACTION_INCOMING_CALL_NOTIFICATION = "ACTION_INCOMING_CALL_NOTIFICATION";
    public static final String ACTION_INCOMING_CALL = "ACTION_INCOMING_CALL";
    public static final String ACTION_CANCEL_CALL = "ACTION_CANCEL_CALL";
    public static final String ACTION_FCM_TOKEN = "ACTION_FCM_TOKEN";

    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.i(TAG, "onNewToken: " + token);
        Intent intent = new Intent(TwilioVoicePlugin.ACTION_SET_FCM_TOKEN);
        intent.putExtra(TwilioVoicePlugin.KEY_FCM_TOKEN, token);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Received onMessageReceived()");
        Log.d(TAG, "Bundle data: " + remoteMessage.getData());
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check ifx message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            final int notificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            Voice.handleMessage(this, data, new MessageListener()  {
                @Override
                public void onCallInvite(CallInvite callInvite) {
                    VoiceFirebaseMessagingService.this.notify(callInvite, notificationId);
                    VoiceFirebaseMessagingService.this.sendCallInviteToPlugin(callInvite, notificationId);
                }

                @Override
                public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException callException) {
                    Log.i(TAG, "Canceling call invite from: " + cancelledCallInvite.getFrom());
                    VoiceFirebaseMessagingService.this.sendCancelledCallToPlugin(notificationId);
                }
            });
        }
    }

    private void notify(CallInvite callInvite, int notificationId) {
        String callSid = callInvite.getCallSid();
        Notification notification = null;

        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.setAction(TwilioVoicePlugin.ACTION_INCOMING_CALL);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID_KEY, notificationId);
        extras.putString(CALL_SID_KEY, callSid);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel callInviteChannel = new NotificationChannel(VOICE_CHANNEL,
                    "Primary Voice Channel", NotificationManager.IMPORTANCE_DEFAULT);
            callInviteChannel.setLightColor(Color.RED);
            callInviteChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(callInviteChannel);

            notification = buildNotification(callInvite.getFrom() + " is calling", pendingIntent, extras);
            notificationManager.notify(notificationId, notification);
        } else {
            int iconIdentifier = getResources().getIdentifier("icon", "mipmap", getPackageName());
            int incomingCallAppNameId = (int) getResources().getIdentifier("incoming_call_app_name", "string", getPackageName());
            String contentTitle = getString(incomingCallAppNameId);

            if (contentTitle == null) {
                contentTitle = "Incoming Call";
            }
            final String from = callInvite.getFrom() + " is calling winker";

            Intent rejectIntent = new Intent(getApplicationContext(), VoiceFirebaseMessagingService.class);
            rejectIntent.setAction(Constants.ACTION_REJECT);
            rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
            rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            PendingIntent piRejectIntent = PendingIntent.getService(getApplicationContext(), 0, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent acceptIntent = new Intent(getApplicationContext(), VoiceFirebaseMessagingService.class);
            acceptIntent.setAction(ACTION_REJECT);
            acceptIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
            acceptIntent.putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId);
            PendingIntent piAcceptIntent = PendingIntent.getService(getApplicationContext(), 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(this)
                            .setSmallIcon(iconIdentifier)
                            .setContentTitle(contentTitle)
                            .setContentText(from)
                            .setAutoCancel(true)
                            .set
            notificationManager.notify(notificationId, notificationBuilder.build());

        }
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    public Notification buildNotification(String text, PendingIntent pendingIntent, Bundle extras) {
        int iconIdentifier = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (iconIdentifier == 0) {
            iconIdentifier = getResources().getIdentifier("ic_launcher", "drawable", getPackageName());
        }

        int incomingCallAppNameId = getResources().getIdentifier("incoming_call_app_name", "string", getPackageName());
        String contentTitle = getString(incomingCallAppNameId);
        return new Notification.Builder(getApplicationContext(), VOICE_CHANNEL)
                .setSmallIcon(iconIdentifier)
                .setContentTitle(contentTitle)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setExtras(extras)
                .setAutoCancel(true)
                .setFullScreenIntent(pendingIntent, true);
                .build();
    }

    /*
     * Send the IncomingCallMessage to the Plugin
     */
    private void sendCallInviteToPlugin(CallInvite incomingCallMessage, int notificationId) {
        Intent intent = new Intent(TwilioVoicePlugin.ACTION_INCOMING_CALL);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_INVITE, incomingCallMessage);
        intent.putExtra(TwilioVoicePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /*
     * Send the CancelledCallMessage to the Plugin
     */
    private void sendCancelledCallToPlugin(int notificationId) {
        Log.d(TAG, "sendCancelledCallToPlugin");
        Intent intent = new Intent(TwilioVoicePlugin.ACTION_CANCELLED_CALL);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
