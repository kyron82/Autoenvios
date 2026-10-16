package com.autoenvios
import java.time.LocalTime

object PriorityEngine {
    private val PM_START = LocalTime.of(14,30)
    private val PM_END   = LocalTime.of(15,30)
    private const val MIN_PAY = 184_000L

    fun getRoutePriority(r:Route):Int? = when {
        r.isOddDay && r.isAM && isPuenteAranda(r.startLocation) -> 1
        r.isOddDay && r.isPM && inWindow(r.startTime,PM_START,PM_END) && isPuenteAranda(r.startLocation) -> 2
        r.isEvenDay && r.isAM && r.payment > MIN_PAY -> 3
        else -> null
    }

    fun getBestRoute(routes:List<Route>):Route? =
        routes.mapNotNull{r->getRoutePriority(r)?.let{r to it}}.minByOrNull{(_,p)->p}?.first

    fun isBetterThanCurrentBooking(r:Route,currentP:Int):Boolean{
        val p=getRoutePriority(r)?:return false; return p<currentP
    }

    fun priorityLabel(p:Int?):String = when(p){
        1->"P1 — Día impar · AM · Puente Aranda"
        2->"P2 — Día impar · 2:30–3:30 PM · Puente Aranda"
        3->"P3 — Día par · AM · >$184.000"
        else->"Sin prioridad"
    }

    private fun isPuenteAranda(loc:String)=loc.contains("puente aranda",ignoreCase=true)
    private fun inWindow(t:LocalTime,s:LocalTime,e:LocalTime)=!t.isBefore(s)&&!t.isAfter(e)
}
