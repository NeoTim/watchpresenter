/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zuluindia.watchpresenter;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;

import com.zuluindia.watchpresenter.backend.messaging.model.VersionMessage;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.zuluindia.watchpresenter.messaging.GcmCheckRegistrationAsyncTask;
import com.zuluindia.watchpresenter.messaging.MessagingService;
import com.zuluindia.watchpresenter.tutorial.TutorialActivity;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends Activity {

    private SharedPreferences settings;
    private String accountName;
    private GoogleAccountCredential credential;
    private static final int REQUEST_ACCOUNT_PICKER = 2;
    private static final int TUTORIAL_ACTIVITY = 3;
    private String versionName;
    private static final String ACTION_STOP_MONITORING = "com.zuluindia.watchpresenter.STOP_MONITORING";
    public static final int PRESENTING_NOTIFICATION_ID = 001;
    private static final int TUTORIAL_VERSION = 1;

    public static boolean active = false;

    private static final String STATE_REGISTERED = "state_registered";
    private static final long CHECK_REGISTRATION_PERIOD = 15000;

    private boolean registered;

    private Timer timer;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MainActivity.ACTION_STOP_MONITORING)) {
                Log.d(Constants.LOG_TAG, "Notification dismissed");
                Intent objIntent = new Intent(context, MonitorVolumeKeyPress.class);
                context.stopService(objIntent);
                //finish the activity to prevent it from restarting the volume
                //keys monitoring on activity resume after the notification has been dismissed
                finish();
            }
        }
    };



    private void chooseAccount() {
        startActivityForResult(credential.newChooseAccountIntent(),
                REQUEST_ACCOUNT_PICKER);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(Constants.SETTINGS_NAME, MODE_PRIVATE);
        registered = settings.getBoolean(Constants.PREF_REGISTERED, false);
        credential = GoogleAccountCredential.usingAudience(this,
                "server:client_id:" + Constants.ANDROID_AUDIENCE);
        setSelectedAccountName(settings.getString(Constants.PREF_ACCOUNT_NAME, null));
        if (credential.getSelectedAccountName() != null) {
            Log.d(Constants.LOG_TAG, "User already logged in");
        } else {
            Log.d(Constants.LOG_TAG, "User not logged in. Requesting user...");
            launchChooseAccount();
        }
        try {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            Log.d(Constants.LOG_TAG, "Pagage name: " + getPackageName());
            Log.d(Constants.LOG_TAG, "Version code: " + versionCode);
            Log.d(Constants.LOG_TAG, "Version name: " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Constants.LOG_TAG, "Cannot retrieve app version", e);
        }
        registerReceiver(broadcastReceiver, new IntentFilter(ACTION_STOP_MONITORING));
    }

    private void scheduleCheckRegistration(){
        if(registered == false) {
            if (timer != null) {
                timer.cancel();
            }
            timer = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    checkAndUpdateRegistration();
                }
            };
            timer.schedule(timerTask, CHECK_REGISTRATION_PERIOD);
        }
    }

    private void checkAndUpdateRegistration(){
        (new GcmCheckRegistrationAsyncTask(MessagingService.get(this), this)).execute();
    }

    public void launchChooseAccount(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.chooseAnAccount))

                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setMessage(R.string.beforeAccountChooseMessage)
                .setIcon(android.R.drawable.ic_dialog_alert);

        builder.setCancelable(false);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                chooseAccount();
            }
        });
        builder.show();
    }

    public void launchNotification(){
// Build intent for notification content
        Intent viewIntent = new Intent(this, SendMessageReceiver.class);
        viewIntent.setAction("com.zuluindia.watchpresenter.SEND_MESSAGE");
        viewIntent.putExtra(Constants.EXTRA_MESSAGE, Constants.NEXT_SLIDE_MESSAGE);
        PendingIntent viewPendingIntent =
                PendingIntent.getBroadcast(this, 0, viewIntent, 0);


        Intent dismissedIntent = new Intent(ACTION_STOP_MONITORING);
        PendingIntent dismissedPendingIntent =
                PendingIntent.getBroadcast(this, 0, dismissedIntent, 0);


        NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                com.zuluindia.watchpresenter.R.drawable.ic_stat_ic_action_forward_blue, null, viewPendingIntent).build();
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(com.zuluindia.watchpresenter.R.drawable.ic_launcher)
                        .setContentTitle(getResources().getString(R.string.notificationTitle))
                        .setContentText(getResources().getString(R.string.notificationMessage))
                        .setDeleteIntent(dismissedPendingIntent)
                        .addAction(action)
                        .setContentIntent(viewPendingIntent)
                        .extend(new NotificationCompat.WearableExtender()
                                .setContentAction(0));

// Get an instance of the NotificationManager service
        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

// Build the notification and issues it with notification manager.
        notificationManager.notify(PRESENTING_NOTIFICATION_ID, notificationBuilder.build());
        Intent objIntent = new Intent(this, MonitorVolumeKeyPress.class);
        startService(objIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        launchNotification();
        active = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(com.zuluindia.watchpresenter.R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == com.zuluindia.watchpresenter.R.id.action_about) {
            (new AlertDialog.Builder(this)
                    .setTitle(getResources().getString(R.string.aboutTitle) + " " + versionName)
                    .setMessage(R.string.aboutMessage)

                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //nothing to do here
                    }
                })
                        .setIcon(android.R.drawable.ic_dialog_alert)
            ).show();
            return true;
        }
        if (id == R.id.action_tutorial) {
            launchTutorial();
            return true;
        }
        if (id == R.id.action_switchAccounts) {
            switchAccounts();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (data != null && data.getExtras() != null) {
                    String accountName =
                            data.getExtras().getString(
                                    AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        Log.d(Constants.LOG_TAG, "User picked. Account name: " + accountName);
                        setSelectedAccountName(accountName);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(Constants.PREF_ACCOUNT_NAME, accountName);
                        editor.commit();
                        final int lastTutorialShown =
                                settings.getInt(Constants.PREF_LAST_TUTORIAL_SHOWN, 0);
                        if(lastTutorialShown < TUTORIAL_VERSION){
                            launchTutorial();
                        }
                    }
                    else{
                        alertAndClose();
                    }
                }
                else{
                    alertAndClose();
                }
                break;
            case TUTORIAL_ACTIVITY:
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(Constants.PREF_LAST_TUTORIAL_SHOWN, TUTORIAL_VERSION);
                editor.commit();
                break;
        }
    }

    private void alertAndClose(){
        (new AlertDialog.Builder(this)
                .setTitle(getString(R.string.errorNoAccount))
                .setMessage(R.string.cannotContinueWithoutAccount)

                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
        ).show();
    }

    private void setSelectedAccountName(String accountName) {
        credential.setSelectedAccountName(accountName);
        this.accountName = accountName;
    }

    public GoogleAccountCredential getCredential(){
        return this.credential;
    }


    public void showSuggestUpdateDialog(final VersionMessage message){
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getResources().getString(R.string.aMessageFromTheDeveloper))
                .setMessage(message.getMessage());
        final String action = message.getAction();
        if(Constants.VersionMessageActions.ACTION_RECOMMEND_UPGRADE.equals(action) ||
                Constants.VersionMessageActions.ACTION_FORCE_UPGRADE.equals(action)){
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getUrl()));
                    startActivity(myIntent);
                    finish();
                }
            });
        }
        if(Constants.VersionMessageActions.ACTION_BLOCK.equals(action)){
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        }
        if(Constants.VersionMessageActions.ACTION_SHOW_MESSAGE.equals(action)){
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //nothing to do here
                }
            });
        }
        if(Constants.VersionMessageActions.ACTION_RECOMMEND_UPGRADE.equals(action)){
            builder.setNegativeButton(com.zuluindia.watchpresenter.R.string.later, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //nothing to do here
                }
            });
        }
        if(Constants.VersionMessageActions.ACTION_FORCE_UPGRADE.equals(action)){
            builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        }
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onStop() {
        super.onPause();
        active = false;
        if(timer != null){
            timer.cancel();
        }
    }

    private void switchAccounts(){
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(Constants.PREF_ACCOUNT_NAME);
        editor.remove(Constants.PREF_REGISTERED);
        editor.commit();
        registered = false;
        Intent objIntent = new Intent(this, MonitorVolumeKeyPress.class);
        stopService(objIntent);
        MessagingService.reset();
        recreate();
    }

    private void launchTutorial(){
        Intent intent = new Intent(this, TutorialActivity.class);
        startActivityForResult(intent, TUTORIAL_ACTIVITY);
    }


    public void registrationUpdate(boolean registered){
        this.registered = registered;
        if(registered){
            //No need to check anymore
            if(timer != null){
                timer.cancel();
            }
        }
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(Constants.PREF_REGISTERED, registered);
        editor.commit();
        updateInterface();
    }

    private void updateInterface(){
        setContentView(com.zuluindia.watchpresenter.R.layout.activity_main);
        TextView versionTextView = (TextView)findViewById(com.zuluindia.watchpresenter.R.id.versionText);
        versionTextView.setText(getResources().getString(R.string.versionPrefix) + " " + versionName);
        if(!registered){
            TextView usageText = (TextView)findViewById(R.id.usageText);
            TextView warning1 = (TextView)findViewById(R.id.warning1);
            TextView warning2 = (TextView)findViewById(R.id.warning2);
            ImageView usageImage = (ImageView)findViewById(R.id.usageImage);
            usageText.setText(R.string.chromeExtensionNotDetected);
            warning1.setText(R.string.installChromeExtension);
            warning2.setText(R.string.goToSite);
            usageImage.setImageResource(R.drawable.phone_usage_broken);
        }
    }

    public void showTroubleShooting(View v){
        LayoutInflater inflater = getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.html_dialog, null);
        WebView mainWebView = (WebView) dialogLayout.findViewById(R.id.mainWebView);
        mainWebView.loadUrl("file:///android_asset/troubleshooting.html");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogLayout);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setTitle(getString(R.string.troubleshooting));
        builder.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateInterface();
        scheduleCheckRegistration();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_REGISTERED, registered);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        registered = savedInstanceState.getBoolean(STATE_REGISTERED);
    }
}
