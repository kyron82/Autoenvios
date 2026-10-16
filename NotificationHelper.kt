package com.autoenvios
import android.app.*; import android.content.*; import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CH_ROUTES="ae_routes"; const val CH_MON="ae_mon"
    const val NID_BOOKED=2001; const val NID_BETTER=2002

    fun createChannels(ctx:Context){
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_ROUTES,"Rutas Agendadas",NotificationManager.IMPORTANCE_HIGH).apply{enableVibration(true)})
        nm.createNotificationChannel(NotificationChannel(CH_MON,"Monitor",NotificationManager.IMPORTANCE_MIN))
    }

    fun notifyRouteBooked(ctx:Context,r:Route,p:Int)=notify(ctx,NID_BOOKED,"✅ ¡Ruta Agendada!","${r.startLocation} · ${r.shiftLabel()} · ${r.formattedPayment()}\n${PriorityEngine.priorityLabel(p)}")
    fun notifyBetterRoute(ctx:Context,r:Route,oldP:Int,newP:Int)=notify(ctx,NID_BETTER,"🔄 ¡Ruta Mejorada! P$oldP→P$newP","${r.startLocation} · ${r.shiftLabel()} · ${r.formattedPayment()}\n${PriorityEngine.priorityLabel(newP)}")

    private fun notify(ctx:Context,id:Int,title:String,body:String){
        val nm=ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi=PendingIntent.getActivity(ctx,id,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id,NotificationCompat.Builder(ctx,CH_ROUTES).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }
}
