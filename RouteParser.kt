package com.autoenvios
import java.time.LocalDate; import java.time.LocalTime; import java.util.UUID

object RouteParser {
    private val TIME_RE    = Regex("""(\d{1,2}):(\d{2})\s*([aApP][mM])?""")
    private val PAYMENT_RE = Regex("""\$\s*([\d.,]+)""")
    private val ZONES = listOf("Puente Aranda","Kennedy","Bosa","Suba","Usaquén","Chapinero","Engativá","Fontibón","Teusaquillo","Barrios Unidos")

    fun parseRoute(text:String, date:LocalDate=LocalDate.now()):Route? {
        if(text.isBlank()) return null
        val times=extractTimes(text)
        val start=times.firstOrNull()?:return null
        val end=times.getOrElse(1){start.plusHours(5)}
        return Route(id=UUID.randomUUID().toString(),startLocation=extractLocation(text),
            startTime=start,endTime=end,payment=extractPayment(text),date=date,rawText=text.trim())
    }

    private fun extractTimes(text:String):List<LocalTime> = TIME_RE.findAll(text).mapNotNull{m->
        try {
            var h=m.groupValues[1].toInt(); val min=m.groupValues[2].toInt(); val ap=m.groupValues[3].lowercase()
            if(ap=="pm"&&h<12)h+=12 else if(ap=="am"&&h==12)h=0 else if(ap.isEmpty()&&h in 1..5)h+=12
            if(h in 0..23&&min in 0..59) LocalTime.of(h,min) else null
        } catch(e:Exception){null}
    }.toList()

    private fun extractPayment(text:String):Long {
        PAYMENT_RE.find(text)?.let{m->
            val v=m.groupValues[1].replace(".","").replace(",","").toLongOrNull()
            if(v!=null&&v>10_000) return v
        }; return 0L
    }

    private fun extractLocation(text:String):String {
        ZONES.forEach{if(text.contains(it,ignoreCase=true))return it}; return "Desconocido"
    }
}
