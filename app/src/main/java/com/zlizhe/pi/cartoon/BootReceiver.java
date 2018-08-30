package com.zlizhe.pi.cartoon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.View;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zackl on 2018/1/5.
 */

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if(intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {     // boot
            final Intent intent2 = new Intent(context, MainActivity.class);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            new Handler().postDelayed(new Runnable(){
                public void run() {
                    context.startActivity(intent2);
                }
            }, 5000); //联通认证需要时间
//          intent2.setAction("android.intent.action.MAIN");
//          intent2.addCategory("android.intent.category.LAUNCHER");
        }
    }
}
