package com.autoenvios
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler; import android.os.Looper
import android.view.accessibility.AccessibilityEvent; import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDate

class EnviosAccessibilityService:AccessibilityService(){
    companion object{
        @Volatile var instance:EnviosAccessibilityService?=null
        @Volatile var isBusy:Boolean=false
        fun triggerCheck(){instance?.startRouteCheck()?:android.util.Log.w("AutoEnvios","Servicio de accesibilidad no activo")}
    }

    private enum class S{IDLE,OPENING,WAITING,NAV_DISP,READING,SELECTING,AGENDAR,CONFIRM,DONE}
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var prefs:AppPrefs
    private var state=S.IDLE; private var target:Route?=null; private var isReplace=false; private var retry=0; private val MAX_R=3

    override fun onServiceConnected(){super.onServiceConnected();instance=this;prefs=AppPrefs(this);prefs.addLog("✅ Servicio de accesibilidad CONECTADO")}
    override fun onDestroy(){instance=null;prefs.addLog("Servicio desconectado");super.onDestroy()}

    fun startRouteCheck(){
        if(isBusy){prefs.addLog("Ocupado, omitiendo");return}
        isBusy=true;state=S.OPENING;retry=0;prefs.resetIfNewDay();prefs.lastCheckTime=System.currentTimeMillis()
        prefs.addLog("▶ Iniciando verificación")
        val intent=packageManager.getLaunchIntentForPackage(prefs.enviosPackageName)
        if(intent==null){prefs.addLog("❌ App no encontrada: ${prefs.enviosPackageName}");reset();return}
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent);state=S.WAITING
        handler.postDelayed({if(state==S.WAITING){prefs.addLog("⏱ Timeout abriendo app");reset()}},15_000)
    }

    override fun onAccessibilityEvent(e:AccessibilityEvent?){
        if(state==S.IDLE||e==null)return
        val pkg=e.packageName?.toString()?:return
        if(pkg!=prefs.enviosPackageName)return
        when(state){
            S.WAITING->{ state=S.NAV_DISP; handler.postDelayed({navDisponibles()},1800) }
            S.NAV_DISP->{ if(e.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED||e.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){ handler.removeCallbacksAndMessages(null);handler.postDelayed({readRoutes()},1200) } }
            S.READING->{ handler.removeCallbacksAndMessages(null);handler.postDelayed({readRoutes()},800) }
            S.AGENDAR->{ handler.postDelayed({clickAgendar()},600) }
            S.CONFIRM->{ handler.postDelayed({confirmBooking()},600) }
            else->{}
        }
    }
    override fun onInterrupt(){prefs.addLog("Servicio interrumpido");reset()}

    private fun navDisponibles(){
        val root=rootInActiveWindow?:run{retryOrFail("raíz no disponible");return}
        val node=findByText(root,"Disponibles")?:findByText(root,"disponibles")?:findByText(root,"DISPONIBLES")
        if(node!=null){node.performAction(AccessibilityNodeInfo.ACTION_CLICK);state=S.NAV_DISP;prefs.addLog("Tocando Disponibles");handler.postDelayed({readRoutes()},3000)}
        else{prefs.addLog("Intentando leer directamente");state=S.READING;readRoutes()}
        root.recycle()
    }

    private fun readRoutes(){
        if(state!=S.NAV_DISP&&state!=S.READING)return
        state=S.READING
        val root=rootInActiveWindow?:run{retryOrFail("ventana no accesible");return}
        val sb=StringBuilder(); collectText(root,sb); val txt=sb.toString(); root.recycle()
        prefs.addLog("Pantalla: ${txt.length} chars")
        if(txt.contains("No hay recorridos",ignoreCase=true)||txt.contains("No encontramos",ignoreCase=true)||txt.contains("sin recorridos",ignoreCase=true)){
            prefs.addLog("Sin rutas disponibles");goHome();reset();return
        }
        val routes=mutableListOf<Route>()
        val blocks=txt.split("\n\n").filter{it.length>15}
        blocks.forEach{RouteParser.parseRoute(it)?.let{r->routes.add(r)}}
        if(routes.isEmpty()) RouteParser.parseRoute(txt)?.let{routes.add(it)}
        routes.forEach{r->prefs.addLog("  Ruta: ${r.startLocation} ${r.shiftLabel()} ${r.formattedPayment()} → P${PriorityEngine.getRoutePriority(r)?:"-"}")}
        val best=PriorityEngine.getBestRoute(routes)
        if(best==null){prefs.addLog("Ninguna ruta cumple prioridades");goHome();reset();return}
        val bestP=PriorityEngine.getRoutePriority(best)!!
        val curP=prefs.currentBookedPriority
        val should=!prefs.hasBookingForToday()||PriorityEngine.isBetterThanCurrentBooking(best,curP)
        if(!should){prefs.addLog("Ruta actual P$curP ya es óptima");goHome();reset();return}
        isReplace=prefs.hasBookingForToday(); target=best; state=S.SELECTING
        prefs.addLog("${if(isReplace)"🔄 Reemplazando" else "📌 Agendando"} P$bestP: ${best.startLocation}")
        selectRoute(best)
    }

    private fun selectRoute(r:Route){
        val root=rootInActiveWindow?:run{retryOrFail("no acceso para seleccionar");return}
        val node=r.startLocation.takeIf{it!="Desconocido"}?.let{root.findAccessibilityNodeInfosByText(it)?.firstOrNull()?.let{n->getClickableParent(n)}}
            ?:findFirstRouteCard(root)
        if(node!=null){node.performAction(AccessibilityNodeInfo.ACTION_CLICK);state=S.AGENDAR;handler.postDelayed({clickAgendar()},2500)}
        else{prefs.addLog("❌ Tarjeta no encontrada");goHome();reset()}
        root.recycle()
    }

    private fun clickAgendar(){
        val root=rootInActiveWindow?:run{retryOrFail("no acceso para Agendar");return}
        val btn=findByText(root,"Agendar")?:findByText(root,"AGENDAR")?:findByText(root,"Reservar")?:findByText(root,"Confirmar reserva")
        if(btn!=null){btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);state=S.CONFIRM;prefs.addLog("Botón Agendar tocado");handler.postDelayed({confirmBooking()},2000)}
        else retryOrFail("botón Agendar no encontrado")
        root.recycle()
    }

    private fun confirmBooking(){
        val root=rootInActiveWindow
        (root?.let{findByText(it,"Confirmar")?:findByText(it,"Aceptar")?:findByText(it,"Sí")?:findByText(it,"OK")})
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        root?.recycle()
        val r=target?:run{reset();return}
        val p=PriorityEngine.getRoutePriority(r)?:run{reset();return}
        val old=prefs.currentBookedPriority
        prefs.currentBookedPriority=p; prefs.currentBookedDate=LocalDate.now().toString()
        prefs.lastBookedDescription="${r.startLocation} · ${r.shiftLabel()} · ${r.formattedPayment()}"
        prefs.addLog("✅ Ruta agendada! ${PriorityEngine.priorityLabel(p)}")
        if(isReplace) NotificationHelper.notifyBetterRoute(this,r,old,p) else NotificationHelper.notifyRouteBooked(this,r,p)
        state=S.DONE; handler.postDelayed({goHome();reset()},1500)
    }

    private fun findByText(root:AccessibilityNodeInfo,t:String)=root.findAccessibilityNodeInfosByText(t)?.firstOrNull()
    private fun getClickableParent(n:AccessibilityNodeInfo):AccessibilityNodeInfo?{var c:AccessibilityNodeInfo?=n;repeat(6){c=c?.parent;if(c?.isClickable==true)return c};return null}
    private fun findFirstRouteCard(root:AccessibilityNodeInfo):AccessibilityNodeInfo?{
        var res:AccessibilityNodeInfo?=null
        fun traverse(n:AccessibilityNodeInfo){if(res!=null)return;if(n.isClickable&&n.childCount>=2){val sb=StringBuilder();collectText(n,sb);if(Regex("""\d{1,2}:\d{2}""").containsMatchIn(sb))res=n};for(i in 0 until n.childCount)n.getChild(i)?.let{traverse(it);it.recycle()}}
        traverse(root);return res
    }
    private fun collectText(n:AccessibilityNodeInfo,sb:StringBuilder){n.text?.let{sb.append(it).append("\n")};for(i in 0 until n.childCount)n.getChild(i)?.let{collectText(it,sb);it.recycle()}}
    private fun goHome(){try{performGlobalAction(GLOBAL_ACTION_HOME)}catch(_:Exception){}}
    private fun retryOrFail(reason:String){retry++;if(retry<=MAX_R){prefs.addLog("⚠️ Reintento $retry/$MAX_R: $reason");handler.postDelayed({when(state){S.NAV_DISP->navDisponibles();S.READING->readRoutes();S.SELECTING->target?.let{selectRoute(it)};S.AGENDAR->clickAgendar();S.CONFIRM->confirmBooking();else->reset()}},2000)}else{prefs.addLog("❌ Abortando: $reason");goHome();reset()}}
    private fun reset(){handler.removeCallbacksAndMessages(null);state=S.IDLE;isBusy=false;target=null;isReplace=false;retry=0}
}
