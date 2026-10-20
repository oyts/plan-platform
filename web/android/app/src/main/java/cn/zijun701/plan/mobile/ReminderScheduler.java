package cn.zijun701.plan.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Native bounded alarm wake; authenticated live check, never cached conditional claims. */
final class ReminderScheduler {
    static final String CHANNEL="plan-reminders-v1",VAULT="PlanCabin.ReminderDevice.v1";
    static final Object LOCK=new Object();
    static volatile long testWake;static volatile String testResult="NOT_RUN";static volatile int testHttp;
    static boolean permission(Context c){return NotificationManagerCompat.from(c).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    static JSONObject saved(Context c)throws Exception {String raw=new SessionVault(c).read(VAULT);return raw==null?null:new JSONObject(raw);}
    static JSONObject status(Context c)throws Exception {JSONObject s=saved(c);return new JSONObject().put("enabled",s!=null).put("permission",permission(c)).put("exact",exact(c)).put("platform","ANDROID").put("deviceId",s==null?JSONObject.NULL:s.getJSONObject("registration").getString("id")).put("owner",s==null?JSONObject.NULL:s.getString("owner"));}
    static void enable(Context c,String server,String owner,JSONObject registration)throws Exception {
        if(!permission(c))throw new IllegalStateException("请先允许计划舱发送通知。");
        server=TransportRules.server(server,BuildConfig.DEBUG);
        String id=registration.getString("id"),token=registration.getString("token");
        if(!owner.matches("[a-f0-9-]{36}")||!id.matches("[a-f0-9-]{36}")||!token.matches(java.util.regex.Pattern.quote(id)+"\\.[a-f0-9]{64}"))throw new IllegalArgumentException("提醒设备授权格式无效。");
        synchronized(LOCK){clear(c);new SessionVault(c).write(VAULT,new JSONObject().put("server",server).put("owner",owner).put("registration",registration).toString());schedule(c,System.currentTimeMillis()+15000);}
    }
    static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,1701,new Intent(c,ReminderReceiver.class).setAction("cn.zijun701.plan.REMINDER_TICK"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c,long at){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(exact(c))a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c));
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c));
    }
    static void clear(Context c)throws Exception {synchronized(LOCK){new SessionVault(c).write(VAULT,null);((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));NotificationManagerCompat.from(c).cancelAll();}}
    static void refresh(Context c)throws Exception{synchronized(LOCK){NotificationManagerCompat.from(c).cancelAll();if(saved(c)!=null)schedule(c,System.currentTimeMillis()+15000);}}
    static void tick(Context c){
        if(BuildConfig.DEBUG){testWake=System.currentTimeMillis();testResult="STARTED";testHttp=0;}
        long next=System.currentTimeMillis()+900000;JSONObject s=null;
        try{
            s=saved(c);if(s==null||!permission(c))return;
            String token=s.getJSONObject("registration").getString("token");
            String server=TransportRules.server(s.getString("server"),BuildConfig.DEBUG);
            HttpURLConnection connection=(HttpURLConnection)TransportRules.target(server,"/api/reminders/poll",null).toURL().openConnection();
            connection.setRequestMethod("POST");connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(3000);connection.setRequestProperty("X-Reminder-Key",token);connection.setFixedLengthStreamingMode(0);connection.setDoOutput(true);
            try{
                int http=connection.getResponseCode();if(BuildConfig.DEBUG)testHttp=http;
                if(http==401){synchronized(LOCK){JSONObject current=saved(c);if(current!=null&&current.getJSONObject("registration").getString("token").equals(token))clear(c);}return;}
                if(http!=200)return;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                long deadline=android.os.SystemClock.elapsedRealtime()+5000;
                try(InputStream in=connection.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>65536||android.os.SystemClock.elapsedRealtime()>deadline)throw new IOException();bytes.write(b,0,n);}}
                JSONObject poll=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));long serverNow=poll.getLong("serverTime");
                next=System.currentTimeMillis()+Math.max(15000,Math.min(900000,poll.getLong("nextWakeAt")-serverNow));
                JSONArray notices=poll.getJSONArray("notices");
                if(BuildConfig.DEBUG)testResult="VALID_RESPONSE_NOTICES_"+notices.length();
                synchronized(LOCK){
                    JSONObject current=saved(c);if(current==null||!current.getJSONObject("registration").getString("token").equals(token))return;
                    JSONArray active=poll.getJSONArray("activeKeys");java.util.Set<String> allowed=new java.util.HashSet<>();for(int i=0;i<Math.min(20,active.length());i++)allowed.add(active.getString(i));
                    if(Build.VERSION.SDK_INT>=23)for(android.service.notification.StatusBarNotification old:((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications())if(old.getId()==1701&&!allowed.contains(old.getTag()))NotificationManagerCompat.from(c).cancel(old.getTag(),old.getId());
                    for(int i=0;i<Math.min(20,notices.length());i++){JSONObject n=notices.getJSONObject(i);if(n.getLong("expiresAt")>serverNow)show(c,n);}
                }
            }finally{connection.disconnect();}
        }catch(Exception unavailable){if(BuildConfig.DEBUG)testResult="ERROR_"+unavailable.getClass().getSimpleName();/* No credential value, message, log or immediate retry. */}
        finally{try{synchronized(LOCK){JSONObject current=saved(c);if(s!=null&&current!=null&&current.getJSONObject("registration").getString("token").equals(s.getJSONObject("registration").getString("token")))schedule(c,next);}}catch(Exception unavailable){/* Remains disabled if secure storage is inaccessible. */}}
    }
    private static void show(Context c,JSONObject n)throws Exception {
        String key=n.getString("key"),path=n.getString("path"),title=n.getString("title"),body=n.getString("body");
        if(!key.matches("[a-f0-9]{64}")||!validPath(path)||title.length()>180||body.length()>600)return;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"事项提醒",NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription("本人开启的周期任务与待办提醒");channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager.createNotificationChannel(channel);}
        int id=key.hashCode();Intent open=new Intent(c,MainActivity.class).setData(UriForReminder.key(key)).putExtra("planReminderPath",path).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intent=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification=new NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.plan_icon).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(intent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setTimeoutAfter(300000);
        NotificationManagerCompat.from(c).notify(key,1701,notification.build());
    }
    private static final class UriForReminder{static android.net.Uri key(String key){return android.net.Uri.parse("plan-reminder://notice/"+key);}}
    static boolean validPath(String path){return ReminderRoutes.valid(path);}
}
