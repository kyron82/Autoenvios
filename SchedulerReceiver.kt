package com.autoenvios
import android.app.*; import android.content.*; import android.os.SystemClock

class SchedulerReceiver:BroadcastReceiver(){
    override fun onReceive(ctx:Context,i:Intent){
        val p=AppPrefs(ctx); if(!p.isMonitoringEnabled)return
        p.addLog("⏰ Alarma — disparando verificación")
        EnviosAccessibilityService.triggerCheck()
        schedule(ctx,p.checkIntervalMinutes.toLong())
    }
    companion object{
        private const val RC=9001
        fun schedule(ctx:Context,mins:Long=5){
            val am=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi=PendingIntent.getBroadcast(ctx,RC,Intent(ctx,SchedulerReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val t=SystemClock.elapsedRealtime()+mins*60*1000
            try{am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,t,pi)}catch(e:SecurityException){am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,t,pi)}
        }
        fun cancel(ctx:Context){
            val am=ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(PendingIntent.getBroadcast(ctx,RC,Intent(ctx,SchedulerReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }
}
