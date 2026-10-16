package com.autoenvios
import android.content.Intent; import android.os.*; import android.provider.Settings
import android.widget.*; import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity:AppCompatActivity(){
    private lateinit var prefs:AppPrefs
    private lateinit var tvAccess:TextView; private lateinit var btnAccess:Button
    private lateinit var swMon:Switch; private lateinit var tvStatus:TextView
    private lateinit var tvBooked:TextView; private lateinit var tvLastCheck:TextView
    private lateinit var tvPkg:TextView; private lateinit var tvInterval:TextView
    private lateinit var btnTest:Button; private lateinit var btnClear:Button
    private lateinit var lvLog:ListView; private lateinit var logAdapter:ArrayAdapter<String>
    private lateinit var btnPkg:Button; private lateinit var btnInterval:Button
    private val refreshHandler=Handler(Looper.getMainLooper())
    private val refreshRunnable=object:Runnable{override fun run(){updateUI();refreshHandler.postDelayed(this,3000)}}

    override fun onCreate(s:Bundle?){
        super.onCreate(s); setContentView(R.layout.activity_main)
        prefs=AppPrefs(this); NotificationHelper.createChannels(this)
        tvAccess=findViewById(R.id.tvAccess); btnAccess=findViewById(R.id.btnAccess)
        swMon=findViewById(R.id.swMon); tvStatus=findViewById(R.id.tvStatus)
        tvBooked=findViewById(R.id.tvBooked); tvLastCheck=findViewById(R.id.tvLastCheck)
        tvPkg=findViewById(R.id.tvPkg); tvInterval=findViewById(R.id.tvInterval)
        btnTest=findViewById(R.id.btnTest); btnClear=findViewById(R.id.btnClear)
        lvLog=findViewById(R.id.lvLog); btnPkg=findViewById(R.id.btnPkg); btnInterval=findViewById(R.id.btnInterval)
        logAdapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,mutableListOf<String>())
        lvLog.adapter=logAdapter
        setupListeners()
    }
    override fun onResume(){super.onResume();updateUI();refreshHandler.post(refreshRunnable)}
    override fun onPause(){refreshHandler.removeCallbacks(refreshRunnable);super.onPause()}

    private fun setupListeners(){
        btnAccess.setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        swMon.setOnCheckedChangeListener{_,on->
            if(on&&!isAccessibilityOn()){Toast.makeText(this,"Primero activa el servicio de accesibilidad",Toast.LENGTH_LONG).show();swMon.isChecked=false;return@setOnCheckedChangeListener}
            prefs.isMonitoringEnabled=on
            if(on){SchedulerReceiver.schedule(this,prefs.checkIntervalMinutes.toLong());prefs.addLog("Monitoreo ACTIVADO");Toast.makeText(this,"✅ Monitoreo activado",Toast.LENGTH_SHORT).show()}
            else{SchedulerReceiver.cancel(this);prefs.addLog("Monitoreo DESACTIVADO")}
            updateUI()
        }
        btnTest.setOnClickListener{
            if(!isAccessibilityOn()){Toast.makeText(this,"Activa el servicio de accesibilidad primero",Toast.LENGTH_LONG).show();return@setOnClickListener}
            prefs.addLog("Verificación manual");EnviosAccessibilityService.triggerCheck()
            Toast.makeText(this,"Verificando rutas…",Toast.LENGTH_SHORT).show()
        }
        btnClear.setOnClickListener{prefs.clearLog();logAdapter.clear();Toast.makeText(this,"Log limpiado",Toast.LENGTH_SHORT).show()}
        btnPkg.setOnClickListener{showPkgDialog()}
        btnInterval.setOnClickListener{showIntervalDialog()}
    }

    private fun updateUI(){
        val ok=isAccessibilityOn()
        tvAccess.text=if(ok)"✅ Servicio de accesibilidad ACTIVO" else "❌ Servicio de accesibilidad INACTIVO"
        tvAccess.setTextColor(ContextCompat.getColor(this,if(ok)android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        btnAccess.text=if(ok)"Ver configuración" else "Activar ahora"
        swMon.isChecked=prefs.isMonitoringEnabled
        tvStatus.text=if(prefs.isMonitoringEnabled)"🟢 Monitoreo activo" else "🔴 Monitoreo detenido"
        if(prefs.hasBookingForToday()){
            tvBooked.text="📦 ${prefs.lastBookedDescription}\n${PriorityEngine.priorityLabel(prefs.currentBookedPriority)}"
            tvBooked.setTextColor(ContextCompat.getColor(this,android.R.color.holo_green_dark))
        }else{tvBooked.text="Sin ruta agendada para hoy";tvBooked.setTextColor(ContextCompat.getColor(this,android.R.color.darker_gray))}
        val lc=prefs.lastCheckTime
        tvLastCheck.text=if(lc==0L)"Nunca" else "Última verificación: ${java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale.getDefault()).format(java.util.Date(lc))}"
        tvPkg.text="App: ${prefs.enviosPackageName}"; tvInterval.text="Intervalo: ${prefs.checkIntervalMinutes} min"
        logAdapter.clear(); logAdapter.addAll(prefs.activityLog.take(30)); logAdapter.notifyDataSetChanged()
    }

    private fun showPkgDialog(){
        val input=EditText(this).apply{setText(prefs.enviosPackageName);setPadding(40,20,40,20)}
        AlertDialog.Builder(this).setTitle("Package name de Envíos Extra")
            .setMessage("Escribe el package de la app o usa 'Detectar'.")
            .setView(input)
            .setPositiveButton("Guardar"){_,_->val v=input.text.toString().trim();if(v.isNotEmpty()){prefs.enviosPackageName=v;updateUI();Toast.makeText(this,"Guardado",Toast.LENGTH_SHORT).show()}}
            .setNeutralButton("Detectar automáticamente"){_,_->detectPkg()}
            .setNegativeButton("Cancelar",null).show()
    }

    private fun detectPkg(){
        val candidates=listOf("com.mercadolibre.android.mlb","com.mercadolibre","com.mercadolibre.android","com.mercadolibre.android.flex","com.mercadoenvios","com.mercadolibre.android.enviospartners")
        val installed=candidates.filter{try{packageManager.getPackageInfo(it,0);true}catch(_:Exception){false}}
        when{
            installed.isEmpty()->AlertDialog.Builder(this).setTitle("No detectada").setMessage("Escribe el package manualmente.\n\nPara encontrarlo: Ajustes → Apps → Envíos Extra → información de la app.").setPositiveButton("OK",null).show()
            installed.size==1->{prefs.enviosPackageName=installed[0];updateUI();Toast.makeText(this,"Detectado: ${installed[0]}",Toast.LENGTH_LONG).show()}
            else->AlertDialog.Builder(this).setTitle("Selecciona la app correcta").setItems(installed.toTypedArray()){_,i->prefs.enviosPackageName=installed[i];updateUI()}.show()
        }
    }

    private fun showIntervalDialog(){
        val opts=arrayOf("5 minutos","10 minutos","15 minutos","30 minutos"); val vals=intArrayOf(5,10,15,30)
        AlertDialog.Builder(this).setTitle("Intervalo de verificación").setItems(opts){_,i->
            prefs.checkIntervalMinutes=vals[i];if(prefs.isMonitoringEnabled)SchedulerReceiver.schedule(this,vals[i].toLong());updateUI()
        }.show()
    }

    private fun isAccessibilityOn():Boolean{
        val en=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false
        return en.contains(packageName,ignoreCase=true)
    }
}
