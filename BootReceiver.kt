package com.autoenvios
import android.content.*

class BootReceiver:BroadcastReceiver(){
    override fun onReceive(ctx:Context,i:Intent){
        if(i.action==Intent.ACTION_BOOT_COMPLETED||i.action==Intent.ACTION_MY_PACKAGE_REPLACED){
            val p=AppPrefs(ctx); if(p.isMonitoringEnabled){p.addLog("Boot — restaurando monitoreo");SchedulerReceiver.schedule(ctx,p.checkIntervalMinutes.toLong())}
        }
    }
}
