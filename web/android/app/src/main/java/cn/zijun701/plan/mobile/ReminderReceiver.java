package cn.zijun701.plan.mobile;

import android.content.*;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        PendingResult pending=goAsync();
        new Thread(()->{try{ReminderScheduler.tick(context.getApplicationContext());}finally{pending.finish();}},"plan-reminder-wake").start();
    }
}
