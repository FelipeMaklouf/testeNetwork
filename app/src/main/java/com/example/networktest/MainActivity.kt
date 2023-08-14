@file:Suppress("DEPRECATION")

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
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.FileInputStream


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
    private lateinit var storage: FirebaseStorage


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

        // Dentro do método onCreate
        storage = FirebaseStorage.getInstance()


        // Coletar Informações
        PingTask().execute()
        informativenessOperator()
        atualizarInformacoes()
        obterDataEHora()

        val generateButton: Button = findViewById(R.id.generate_button)
        generateButton.setOnClickListener {
            informativenessOperator()
            Toast.makeText(this, "Arquivo CSV criado com sucesso!", Toast.LENGTH_SHORT).show()
            enviarDadosParaFirebase()
        }
    }

    private fun enviarDadosParaFirebase() {
        val storageRef = storage.reference
        val csvFileName = "network_info.csv"
        val fileRef = storageRef.child(csvFileName)

        // Caminho completo do arquivo local
        val caminhoDoArquivoLocal = File(Environment.getExternalStorageDirectory(), "/Documents/SignalTracker/Log/$csvFileName")

        val stream = FileInputStream(caminhoDoArquivoLocal)
        val uploadTask = fileRef.putStream(stream)

        uploadTask.addOnSuccessListener {
            // O upload foi bem-sucedido
            Toast.makeText(this, "Arquivo enviado para o Firebase com sucesso!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            // O upload falhou
            Toast.makeText(this, "Erro ao enviar o arquivo para o Firebase", Toast.LENGTH_SHORT).show()
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
                operatorTextView.text = "Operadora: $operatorNome"
            } else {
                networkTypeTextView.text = "Tipo de Rede: $networkType"
                operatorTextView.text = "Provedor: $ssid"
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
        val data = listOf(networkSubType, networkType, operatorNome, linkSpeedMbps, ssid)
        val csvFileName = "network_info.csv"

        val externalStorageState = Environment.getExternalStorageState()

        if (Environment.MEDIA_MOUNTED == externalStorageState) {
            val baseDirectory = Environment.getExternalStorageDirectory()
            val customDirectory = File(baseDirectory, "/Documents/SignalTracker/Log")

            if (!customDirectory.exists()) {
                customDirectory.mkdirs()  // Cria a pasta personalizada se não existir
            }
            val csvFile = File(customDirectory, csvFileName)
            try {
                val csvWriter = FileWriter(csvFile)
                data.forEach { data ->
                    csvWriter.append("${networkSubType} ", "${networkType} ","${operatorNome} ",
                        "${linkSpeedMbps} ")
                    csvWriter.appendLine()
                }
                csvWriter.flush()
                csvWriter.close()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Erro ao criar o arquivo CSV", Toast.LENGTH_SHORT).show()
            }
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