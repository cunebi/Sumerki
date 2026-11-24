package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.remote.GeocodingApiService
import com.example.data.remote.TimezoneApiService
import com.example.data.remote.TwilightApiService
import com.example.domain.TwilightCheckEngine
import com.example.scheduler.MidnightScheduler
import com.example.system.LocationHelper
import com.example.system.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val preferencesRepository = UserPreferencesRepository(appContext)
    val database = AppDatabase.getInstance(appContext)
    val checkDao = database.twilightCheckDao()
    val locationHelper = LocationHelper(appContext)

    val firestoreDb: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )
    val firestoreSyncRepository = FirestoreSyncRepository(firestoreDb)

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    val twilightApiService: TwilightApiService = Retrofit.Builder()
        .baseUrl("https://api.sunrise-sunset.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TwilightApiService::class.java)

    val geocodingApiService: GeocodingApiService = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeocodingApiService::class.java)

    val timezoneApiService: TimezoneApiService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(TimezoneApiService::class.java)

    val twilightCheckEngine = TwilightCheckEngine(
        context = appContext,
        preferencesRepository = preferencesRepository,
        checkDao = checkDao,
        twilightApi = twilightApiService,
        locationHelper = locationHelper,
        firestoreSyncRepository = firestoreSyncRepository,
        timezoneApi = timezoneApiService
    )
}

class TwilightApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)

        appScope.launch {
            val settings = container.preferencesRepository.getSnapshot()
            if (settings.onboardingCompleted && settings.dailyMidnightEnabled) {
                MidnightScheduler.scheduleDailyMidnightCheck(this@TwilightApp)
            }
        }
    }
}
