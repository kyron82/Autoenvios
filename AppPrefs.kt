package com.autoenvios
import android.content.Context; import android.content.SharedPreferences
import java.text.SimpleDateFormat; import java.time.LocalDate; import java.util.*

class AppPrefs(context:Context) {
    private val p:SharedPreferences=context.getSharedPreferences("ae_prefs",Context.MODE_PRIVATE)
    var isMonitoringEnabled:Boolean get()=p.getBoolean("mon",false); set(v)=p.edit().putBoolean("mon",v).apply()
    var enviosPackageName:String get()=p.getString("pkg",DEF_PKG)?:DEF_PKG; set(v)=p.edit().putString("pkg",v).apply()
    var checkIntervalMinutes:Int get()=p.getInt("interval",5); set(v)=p.edit().putInt("interval",v).apply()
    var currentBookedPriority:Int get()=p.getInt("bprio",Int.MAX_VALUE); set(v)=p.edit().putInt("bprio",v).apply()
    var currentBookedDate:String get()=p.getString("bdate","")?:""; set(v)=p.edit().putString("bdate",v).apply()
    var lastBookedDescription:String get()=p.getString("bdesc","Ninguna")?:"Ninguna"; set(v)=p.edit().putString("bdesc",v).apply()
    var lastCheckTime:Long get()=p.getLong("lcheck",0L); set(v)=p.edit().putLong("lcheck",v).apply()
    val activityLog:List<String> get(){val r=p.getString("log","")?:""; return if(r.isEmpty()) emptyList() else r.split("|||")}
    fun addLog(msg:String){
        val ts=SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(Date())
        android.util.Log.d("AutoEnvios","[$ts] $msg")
        val cur=activityLog.toMutableList(); cur.add(0,"[$ts] $msg")
        p.edit().putString("log",cur.take(80).joinToString("|||")).apply()
    }
    fun clearLog()=p.edit().remove("log").apply()
    fun resetIfNewDay(){
        val today=LocalDate.now().toString()
        if(currentBookedDate!=today){currentBookedPriority=Int.MAX_VALUE;currentBookedDate=today;lastBookedDescription="Ninguna";addLog("Nuevo día — estado reiniciado")}
    }
    fun hasBookingForToday()=currentBookedDate==LocalDate.now().toString()&&currentBookedPriority!=Int.MAX_VALUE
    companion object{const val DEF_PKG="com.mercadolibre.android.mlb"}
}
