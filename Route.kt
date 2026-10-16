package com.autoenvios
import java.time.LocalDate; import java.time.LocalTime
data class Route(val id:String="",val startLocation:String="",val startTime:LocalTime=LocalTime.NOON,
    val endTime:LocalTime=LocalTime.NOON,val payment:Long=0L,val date:LocalDate=LocalDate.now(),val rawText:String="") {
    val isAM:Boolean get()=startTime.isBefore(LocalTime.NOON)
    val isPM:Boolean get()=!isAM
    val isOddDay:Boolean get()=date.dayOfMonth%2!=0
    val isEvenDay:Boolean get()=!isOddDay
    fun formattedPayment()="$ ${String.format("%,d",payment).replace(',','.')}"
    fun shiftLabel()=if(isAM)"AM" else "PM"
}
