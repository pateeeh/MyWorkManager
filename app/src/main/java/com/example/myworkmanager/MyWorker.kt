//package com.example.myworkmanager
//
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import android.os.Build
//import android.os.Looper
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.work.Worker
//import androidx.work.WorkerParameters
//import com.loopj.android.http.AsyncHttpClient
//import com.loopj.android.http.AsyncHttpResponseHandler
//import com.example.myworkmanager.BuildConfig
//import cz.msebera.android.httpclient.Header
//import org.json.JSONObject
//import java.text.DecimalFormat
//
//class MyWorker(context: Context, workerParams: WorkerParameters): Worker(context, workerParams) {
//
//    companion object {
//        private val TAG = MyWorker::class.java.simpleName
//        const val EXTRA_CITY = "city"
//        const val NOTIFICATION_ID = 1
//        const val CHANNEL_ID = "channel_01"
//        const val CHANNEL_NAME = "dicoding channel"
//    }
//
//    private var resultStatus: Result? = null
//
//    override fun doWork(): Result {
//        val dataCity = inputData.getString(EXTRA_CITY)
//        return getCurrenWeather(dataCity)
//    }
//
//    private fun showNotification(title: String, description: String?) {
//        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val notification: NotificationCompat.Builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
//            .setSmallIcon(R.drawable.baseline_notifications_active_24)
//            .setContentTitle(title)
//            .setContentText(description)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setDefaults(NotificationCompat.DEFAULT_ALL)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
//            notification.setChannelId(CHANNEL_ID)
//            notificationManager.createNotificationChannel(channel)
//        }
//        notificationManager.notify(NOTIFICATION_ID, notification.build())
//    }
//
//    private fun getCurrenWeather(city: String?): Result {
//        Log.d(TAG, "getCurrentWeather: Mulai.....")
//        Looper.prepare()
//        val client = AsyncHttpClient()
//        val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=${BuildConfig.APP_ID}"
//        Log.d(TAG, "getCurrentWeather: $url")
//        client.post(url, object : AsyncHttpResponseHandler() {
//            override fun onSuccess(
//                statusCode: Int,
//                headers: Array<out Header>?,
//                responseBody: ByteArray
//            ) {
//                val result = String(responseBody)
//                Log.d(TAG, result)
//                try {
//                    val responseObject = JSONObject(result)
//                    val currentWeather: String = responseObject.getJSONArray("weather").getJSONObject(0).getString("main")
//                    val description: String = responseObject.getJSONArray("weather").getJSONObject(0).getString("desctiption")
//                    val tempInKelvin = responseObject.getJSONObject("main").getDouble("temp")
//                    val tempInCelsius = tempInKelvin - 273
//                    val temperature: String = DecimalFormat("##.##").format(tempInCelsius)
//                    val title = "Cuaca saat ini di $city"
//                    val message = "$currentWeather, $description with $temperature celsius"
//                    showNotification(title, message)
//                    Log.d(TAG, "onSuccess: Selesai.....")
//                    resultStatus = Result.success()
//                } catch (e: Exception) {
//                    showNotification("Gagal Memuat Data Cuaca", e.message)
//                    Log.d(TAG,"Gagal....")
//                    resultStatus = Result.failure()
//                }
//            }
//
//            override fun onFailure(
//                statusCode: Int,
//                headers: Array<out Header>?,
//                responseBody: ByteArray?,
//                error: Throwable?
//            ) {
//                Log.d(TAG, "Gagal.....")
//                //Jika proses gagal, maka jobFinished diset dengan parameter true. Yang artinya job perlu di reschedule
//                showNotification("Gagal Memuat Data Cuaca", error?.message)
//                resultStatus = Result.failure()
//            }
//        })
//
//        return resultStatus as Result
//    }
//
//}

package com.example.myworkmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import com.example.myworkmanager.BuildConfig
import cz.msebera.android.httpclient.Header
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.concurrent.CountDownLatch

class MyWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private val TAG = MyWorker::class.java.simpleName
        const val EXTRA_CITY = "city"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "channel_01"
        const val CHANNEL_NAME = "dicoding channel"
    }

    private val handlerThread = HandlerThread("WorkerThread").apply { start() } //  Menggunakan HandlerThread
    private val workerHandler = Handler(handlerThread.looper) //  Membuat Handler dengan Looper

    override fun doWork(): Result {
        val dataCity = inputData.getString(EXTRA_CITY) ?: return Result.failure() // Pastikan city tidak null
        return getCurrentWeather(dataCity)
    }

    private fun showNotification(title: String, description: String?) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification: NotificationCompat.Builder =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_notifications_active_24)
                .setContentTitle(title)
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            notification.setChannelId(CHANNEL_ID)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }

    private fun getCurrentWeather(city: String): Result {
        Log.d(TAG, "getCurrentWeather: Mulai.....")

        val latch = CountDownLatch(1) //  Menggunakan CountDownLatch agar thread menunggu respons API
        var resultStatus: Result = Result.failure()

        workerHandler.post { //  Menjalankan API request dalam HandlerThread
            val client = AsyncHttpClient()
            val url = "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=${BuildConfig.APP_ID}"
            Log.d(TAG, "getCurrentWeather: $url")

            client.get(url, object : AsyncHttpResponseHandler(workerHandler.looper) { //  Menggunakan Looper dari workerHandler
                override fun onSuccess(
                    statusCode: Int,
                    headers: Array<out Header>?,
                    responseBody: ByteArray
                ) {
                    val result = String(responseBody)
                    Log.d(TAG, result)
                    try {
                        val responseObject = JSONObject(result)
                        val currentWeather: String = responseObject.getJSONArray("weather").getJSONObject(0).getString("main")
                        val description: String = responseObject.getJSONArray("weather").getJSONObject(0).getString("description")
                        val tempInKelvin = responseObject.getJSONObject("main").getDouble("temp")
                        val tempInCelsius = tempInKelvin - 273
                        val temperature: String = DecimalFormat("##.##").format(tempInCelsius)
                        val title = "Cuaca saat ini di $city"
                        val message = "$currentWeather, $description with $temperature°C"
                        showNotification(title, message)
                        Log.d(TAG, "onSuccess: Selesai.....")
                        resultStatus = Result.success()
                    } catch (e: Exception) {
                        showNotification("Gagal Memuat Data Cuaca", e.message)
                        Log.e(TAG, "Gagal parsing JSON", e)
                        resultStatus = Result.failure()
                    }
                    latch.countDown() //  Mengurangi hitungan latch setelah respons diterima
                }

                override fun onFailure(
                    statusCode: Int,
                    headers: Array<out Header>?,
                    responseBody: ByteArray?,
                    error: Throwable?
                ) {
                    Log.e(TAG, "Gagal mendapatkan data cuaca", error)
                    showNotification("Gagal Memuat Data Cuaca", error?.message)
                    resultStatus = Result.failure()
                    latch.countDown() //  Pastikan latch tetap dihitung untuk menghindari deadlock
                }
            })
        }

        latch.await() //  Tunggu hingga request selesai
        return resultStatus
    }
}