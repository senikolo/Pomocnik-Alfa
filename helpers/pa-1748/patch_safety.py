#!/usr/bin/env python3
"""PA 1.7.48: independent subscription storage, safer radio service and honest promotion alerts."""
from pathlib import Path
root=Path("project/app/src/main")
p=root/"java/com/ispina/lokalnie/SubscriptionsActivity.java"
s=p.read_text(encoding="utf-8")
def rep(old,new):
 global s
 assert s.count(old)==1,("subscription",s.count(old),old[:70])
 s=s.replace(old,new)
rep('add.setOnClickListener(v->{if(ensureNotificationPermission())showAdd();});',
    'add.setOnClickListener(v->showAdd());')
# Keep permission under user control; display banner rather than interrupt create flow.
rep('''        NativeUi.addSpacer(root,this,8);
        ScrollView sc=new ScrollView(this);''',
'''        NativeUi.addSpacer(root,this,8);
        if(!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()){
            LinearLayout info=NativeUi.card(this);
            info.addView(NativeUi.muted(this,
              "Subskrypcje zapiszą się normalnie. Powiadomienia są wyłączone — włącz je osobno, jeśli chcesz otrzymywać przypomnienia.",13));
            NativeUi.addSpacer(info,this,6);
            Button allow=NativeUi.button(this,"Włącz powiadomienia o odnowieniu",true);
            allow.setOnClickListener(v->ensureNotificationPermission());
            info.addView(allow,new LinearLayout.LayoutParams(-1,-2));
            root.addView(info);
        }
        ScrollView sc=new ScrollView(this);''')
rep('''                if(!ok){getSharedPreferences("native_subscriptions",0).edit().remove(id).apply();Toast.makeText(this,"Nie udało się zaplanować przypomnienia.",Toast.LENGTH_LONG).show();return;}
                dlg.dismiss();refresh();''',
'''                try{o.put("reminder_active",ok && androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled());}catch(Exception ignored){}
                getSharedPreferences("native_subscriptions",0).edit().putString(id,o.toString()).apply();
                if(!ok || !androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled())
                   Toast.makeText(this,"Zapisano subskrypcję. Przypomnienia są wyłączone lub nie mogą być teraz zaplanowane.",Toast.LENGTH_LONG).show();
                dlg.dismiss();refresh();''')
# Do not program a useless notification if permission denied.
rep('''                boolean ok=ReminderScheduler.saveAndSchedule(this,"notify_"+id,"Subskrypcja: "+n,"Odnowienie za "+d+(d==1?" dzień":" dni")+". Jeśli chcesz zrezygnować, zrób to teraz.",notify.getTimeInMillis(),reminderRepeat);''',
'''                boolean notifications=androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled();
                boolean ok=notifications && ReminderScheduler.saveAndSchedule(this,"notify_"+id,"Subskrypcja: "+n,"Odnowienie za "+d+(d==1?" dzień":" dni")+". Jeśli chcesz zrezygnować, zrób to teraz.",notify.getTimeInMillis(),reminderRepeat);''')
rep('''    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}''',
'''    @Override protected void onCreate(Bundle b){super.onCreate(b);build();}
    @Override protected void onResume(){super.onResume();restoreSubscriptionReminders();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==503){restoreSubscriptionReminders();build();}
    }
    private void restoreSubscriptionReminders(){
        if(!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled())return;
        SharedPreferences p=getSharedPreferences("native_subscriptions",0);
        long now=System.currentTimeMillis();
        for(String id:p.getAll().keySet()){
            try{
                JSONObject o=new JSONObject(p.getString(id,"{}"));
                if(o.optBoolean("reminder_active",true))continue;
                rollRenewalForward(o,now);
                int days=o.optInt("days",2);
                Calendar notify=Calendar.getInstance();
                notify.setTimeInMillis(o.optLong("renewal",0));
                notify.add(Calendar.DAY_OF_MONTH,-days);
                if(notify.getTimeInMillis()<=now)continue;
                String cycle=o.optString("cycle","monthly");
                boolean ok=ReminderScheduler.saveAndSchedule(this,"notify_"+id,
                    "Subskrypcja: "+o.optString("name","Subskrypcja"),
                    "Odnowienie za "+days+" dni. Jeśli chcesz zrezygnować, zrób to teraz.",
                    notify.getTimeInMillis(),cycle);
                o.put("reminder_active",ok);
                p.edit().putString(id,o.toString()).apply();
            }catch(Exception ignored){}
        }
    }''')
# on Android >=33, settings fallback if notification has been rejected; do not promise if rejected.
rep('''        return true;
    }

    private void refresh(){''',
'''        if(!androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()){
            try{
                android.content.Intent settings=new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                startActivity(settings);
            }catch(Exception ignored){}
            return false;
        }
        return true;
    }

    private void refresh(){''')
# Avoid NaN or Infinity poisoning totals.
rep('if(pr<0)throw new Exception();','if(!Double.isFinite(pr)||pr<0)throw new Exception();')
p.write_text(s,encoding="utf-8")

p=root/"AndroidManifest.xml"
s=p.read_text(encoding="utf-8")
old='''android:name=".radio.RadioService"
            android:exported="true"'''
assert s.count(old)==1
s=s.replace(old,'''android:name=".radio.RadioService"
            android:exported="false"''')
p.write_text(s,encoding="utf-8")

p=root/"java/com/ispina/lokalnie/deals/DealAlerts.java"
s=p.read_text(encoding="utf-8")
old='WorkManager.getInstance(c).enqueueUniqueWork("promotion-now",ExistingWorkPolicy.KEEP,'
assert s.count(old)==1
s=s.replace(old,'WorkManager.getInstance(c).enqueueUniqueWork("promotion-now",ExistingWorkPolicy.REPLACE,')
p.write_text(s,encoding="utf-8")
print("PASS subscriptions without permissions, reminders permission opt-in, radio non-exported, manual deals refresh")
