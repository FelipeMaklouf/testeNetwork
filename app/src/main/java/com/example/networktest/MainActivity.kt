package com.example.networktest

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.InetAddress
import android.os.Handler
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.widget.Button
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity(){
    private val csv = "network_info.csv"
    private lateinit var operatorTextView: TextView
    private lateinit var networkTypeTextView: TextView
    private lateinit var networkSpeedTextView: TextView
    private lateinit var pingResultTextView: TextView
    private val INTERVALO_ATUALIZACAO = 1000
    private lateinit var handler: Handler
    private lateinit var dataTextView: TextView
    private lateinit var horaTextView: TextView
    private lateinit var horaFormatada: SimpleDateFormat
    private lateinit var wifiProviderTextView: TextView
    private lateinit var wifiSpeedTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val collectedData = mutableListOf<Array<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        operatorTextView = findViewById(R.id.operatorTextView)
        networkTypeTextView = findViewById(R.id.networkTypeTextView)
        networkSpeedTextView = findViewById(R.id.networkSpeedTextView)
        pingResultTextView = findViewById(R.id.pingResultTextView)
        dataTextView = findViewById(R.id.dataTextView)
        horaTextView = findViewById(R.id.horaTextView)
        horaFormatada = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        wifiProviderTextView = findViewById(R.id.operatorTextView)
        wifiSpeedTextView = findViewById(R.id.networkSpeedTextView)
        wifiProviderTextView = findViewById(R.id.operatorTextView)
        handler = Handler()

        // Coletar Informações
        PingTask().execute()
        informativenessOperator()
        atualizarInformacoes()
        obterDataEHora()

        val generateButton: Button = findViewById(R.id.generate_button)
        generateButton.setOnClickListener {
            informativenessOperator()
        }
    }

    private fun atualizarInformacoes() {
        PingTask().execute()
        informativenessOperator()
        obterDataEHora()
        handler.postDelayed({
            atualizarInformacoes()
        }, INTERVALO_ATUALIZACAO.toLong())
    }

    private fun informativenessOperator() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        val networkType = networkInfo?.typeName
        val networkSubType = networkInfo?.subtypeName
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid
        val operatorNome = telephonyManager.networkOperatorName
        val linkSpeedMbps = wifiInfo?.linkSpeed

        if (networkInfo != null && networkInfo.isConnected) {
            if (networkType == "MOBILE"){
                networkTypeTextView.text = "Tipo de Rede: Dados Móveis"
            } else {
                networkTypeTextView.text = "Tipo de Rede: $networkType"
            }
            if (linkSpeedMbps != null && linkSpeedMbps > 0) {
                wifiSpeedTextView.text = "Velocidade Wifi: $linkSpeedMbps Mbps"
            } else {
                if (networkSubType == "LTE"){
                    networkSpeedTextView.text = "Velocidade da Rede: 4G"
                } else if (networkSubType == "UMTS"){
                    networkSpeedTextView.text = "Velocidade da Rede: 3G+"
                } else if (networkSubType == "HSPA+"){
                    networkSpeedTextView.text = "Velocidade da Rede: 3G"
                } else if (networkSubType == "GPRS"){
                    networkSpeedTextView.text = "Velocidade da Rede: 2G"
                }
            }
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            operatorTextView.text = "Operadora: $operatorNome"
        }
        if (networkType == "MOBILE"){
            networkTypeTextView.text = "Tipo de Rede: Dados Móveis"
            operatorTextView.text = "Operadora: $operatorNome"
        } else if(networkType == "WIFI"){
            networkTypeTextView.text = "Tipo de Rede: $networkType"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                operatorTextView.text = "Provedor: $ssid"
            }
        }

        val data = listOf(networkSubType)
        val csvFile: File
        val externalStorageState = Environment.getExternalStorageState()

        if (Environment.MEDIA_MOUNTED == externalStorageState) {
            val directory = getExternalFilesDir(null)
            csvFile = File(directory, csv)
        } else {
            return
        }
        try {
            val csvWriter = FileWriter(csvFile)
            data.forEach { data ->
                csvWriter.append("${networkSubType}")
                csvWriter.append('\n')
            }

            csvWriter.flush()
            csvWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    private inner class PingTask : AsyncTask<Void, Void, String>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): String {
            return try {
                val address = InetAddress.getByName("www.google.com")
                val startTime = System.currentTimeMillis()
                if (address.isReachable(3000)) {
                    val endTime = System.currentTimeMillis()
                    (endTime - startTime).toString()
                } else {
                    "Timeout"
                }
            } catch (e: IOException) {
                "Erro: ${e.message}"
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: String?) {
            pingResultTextView.text = "Latência:\n$result ms"
        }
    }
    private fun obterDataEHora() {
        val calendario = Calendar.getInstance()

        val dia = calendario.get(Calendar.DAY_OF_MONTH)
        val mes = calendario.get(Calendar.MONTH) + 1
        val ano = calendario.get(Calendar.YEAR)

        val horaComSegundos = horaFormatada.format(calendario.time)
        val dataFormatada = String.format("%02d/%02d/%04d", dia, mes, ano)

        dataTextView.text = "Data:\n$dataFormatada"
        horaTextView.text = "Hora:\n$horaComSegundos"
    }
}