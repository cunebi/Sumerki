package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.TwilightCheckEntity
import com.example.data.model.AppSettings
import com.example.data.model.CheckLiveState
import com.example.data.model.CityOption
import com.example.data.model.LocationMode
import com.example.data.model.PermissionStatus
import com.example.data.model.TwilightPreference
import com.example.data.remote.CloudTwilightCheck
import com.example.data.remote.CuratedCities
import com.example.domain.CheckOutcome
import com.example.scheduler.MidnightScheduler
import com.example.system.PermissionStatusHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class MainUiState(
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val currentUserName: String = "",
    val settings: AppSettings = AppSettings(),
    val permissions: PermissionStatus = PermissionStatus(),
    val history: List<TwilightCheckEntity> = emptyList(),
    val latestCheck: TwilightCheckEntity? = null,
    val checkLiveState: CheckLiveState = CheckLiveState.Idle,
    val isDetectingLocation: Boolean = false,
    val locationDetectError: String? = null,
    val citySearchQuery: String = "",
    val citySearchResults: List<CityOption> = CuratedCities.presets,
    val isSearchingCities: Boolean = false,
    val citySearchError: String? = null,
    val countdownToMidnightText: String = "",
    val cloudSynced: Boolean = false,
    val cloudSyncError: String? = null
)

class MainViewModel(
    private val container: AppContainer,
    private val currentUserId: String,
    private val currentUserName: String
) : ViewModel() {

    private val appContext: Context = container.appContext
    private val prefs = container.preferencesRepository
    private val checkDao = container.checkDao
    private val firestoreRepo = container.firestoreSyncRepository

    private val permissionsFlow = MutableStateFlow(PermissionStatusHelper.getStatus(appContext))
    private val checkLiveFlow = MutableStateFlow<CheckLiveState>(CheckLiveState.Idle)
    private val detectingLocationFlow = MutableStateFlow(false)
    private val locationErrorFlow = MutableStateFlow<String?>(null)
    private val cityQueryFlow = MutableStateFlow("")
    private val cityResultsFlow = MutableStateFlow(CuratedCities.presets)
    private val searchingCitiesFlow = MutableStateFlow(false)
    private val citySearchErrorFlow = MutableStateFlow<String?>(null)
    private val countdownFlow = MutableStateFlow(computeCountdownText())
    private val loadedSettingsFlow = MutableStateFlow<AppSettings?>(null)
    private val cloudSyncedFlow = MutableStateFlow(false)
    private val cloudSyncErrorFlow = MutableStateFlow<String?>(null)
    private val cloudHistoryFlow = MutableStateFlow<List<TwilightCheckEntity>>(emptyList())

    private var searchJob: Job? = null
    private var checkJob: Job? = null
    private var cloudHydratedOnce = false

    init {
        viewModelScope.launch {
            prefs.settingsFlow.collect { loadedSettingsFlow.value = it }
        }
        viewModelScope.launch {
            while (true) {
                countdownFlow.value = computeCountdownText()
                delay(30_000L)
            }
        }
        observeCloudSettings()
        observeCloudChecks()
    }

    private fun observeCloudSettings() {
        viewModelScope.launch {
            firestoreRepo.observeUserSettings(currentUserId)
                .map { cloud -> cloud }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud settings", root)
                    cloudSyncErrorFlow.value = root.localizedMessage ?: "Ошибка синхронизации настроек"
                }
                .collect { cloudSettings ->
                    cloudSyncErrorFlow.value = null
                    if (cloudSettings != null) {
                        cloudSyncedFlow.value = true
                        if (!cloudHydratedOnce) {
                            cloudHydratedOnce = true
                            val local = prefs.getSnapshot()
                            if (!local.onboardingCompleted && cloudSettings.onboardingCompleted) {
                                applyCloudSettingsLocally(cloudSettings.toAppSettings())
                            }
                        }
                    } else if (!cloudHydratedOnce) {
                        cloudHydratedOnce = true
                        val local = prefs.getSnapshot()
                        if (local.onboardingCompleted) {
                            pushSettingsToCloud(local)
                        }
                    }
                }
        }
    }

    private fun observeCloudChecks() {
        viewModelScope.launch {
            firestoreRepo.observeChecks(currentUserId)
                .map<List<CloudTwilightCheck>, List<TwilightCheckEntity>> { list ->
                    list.map { it.toLocalEntity() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing cloud checks", root)
                    cloudSyncErrorFlow.value = root.localizedMessage ?: "Ошибка загрузки облачного журнала"
                }
                .collect { cloudChecks ->
                    cloudSyncedFlow.value = true
                    cloudSyncErrorFlow.value = null
                    cloudHistoryFlow.value = cloudChecks
                }
        }
    }

    private suspend fun applyCloudSettingsLocally(remote: AppSettings) {
        prefs.setOnboardingCompleted(remote.onboardingCompleted)
        prefs.setDailyMidnightEnabled(remote.dailyMidnightEnabled)
        prefs.setMaxRetryAttempts(remote.maxRetryAttempts)
        prefs.setRetryDelaySeconds(remote.retryDelaySeconds)
        prefs.setTwilightPreference(remote.twilightPreference)
        prefs.setAlarmOffsetMinutes(remote.alarmOffsetMinutes)
        prefs.setAlarmLabel(remote.alarmLabel)
        if (remote.latitude != null && remote.longitude != null) {
            prefs.saveLocation(remote.latitude, remote.longitude, remote.locationName, remote.locationMode)
        } else {
            prefs.setLocationMode(remote.locationMode)
        }
        if (remote.onboardingCompleted && remote.dailyMidnightEnabled) {
            MidnightScheduler.scheduleDailyMidnightCheck(appContext)
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        loadedSettingsFlow,
        permissionsFlow,
        checkDao.observeAllChecks(),
        cloudHistoryFlow,
        checkLiveFlow,
        detectingLocationFlow,
        locationErrorFlow,
        cityQueryFlow,
        cityResultsFlow,
        searchingCitiesFlow,
        citySearchErrorFlow,
        countdownFlow,
        cloudSyncedFlow,
        cloudSyncErrorFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as AppSettings?
        val permissions = values[1] as PermissionStatus
        @Suppress("UNCHECKED_CAST")
        val localHistory = values[2] as List<TwilightCheckEntity>
        @Suppress("UNCHECKED_CAST")
        val cloudHistory = values[3] as List<TwilightCheckEntity>
        val checkLive = values[4] as CheckLiveState
        val detectingLoc = values[5] as Boolean
        val locError = values[6] as String?
        val cityQuery = values[7] as String
        @Suppress("UNCHECKED_CAST")
        val cityResults = values[8] as List<CityOption>
        val searchingCities = values[9] as Boolean
        val cityError = values[10] as String?
        val countdown = values[11] as String
        val cloudSynced = values[12] as Boolean
        val cloudError = values[13] as String?

        val mergedHistory = if (cloudHistory.isNotEmpty()) cloudHistory else localHistory

        MainUiState(
            isLoading = settings == null,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            settings = settings ?: AppSettings(),
            permissions = permissions,
            history = mergedHistory,
            latestCheck = mergedHistory.firstOrNull(),
            checkLiveState = checkLive,
            isDetectingLocation = detectingLoc,
            locationDetectError = locError,
            citySearchQuery = cityQuery,
            citySearchResults = cityResults,
            isSearchingCities = searchingCities,
            citySearchError = cityError,
            countdownToMidnightText = countdown,
            cloudSynced = cloudSynced,
            cloudSyncError = cloudError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MainUiState(
            currentUserId = currentUserId,
            currentUserName = currentUserName
        )
    )

    fun refreshPermissions() {
        permissionsFlow.value = PermissionStatusHelper.getStatus(appContext)
    }

    fun detectLocationAutomatically(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            detectingLocationFlow.value = true
            locationErrorFlow.value = null
            try {
                val detected = container.locationHelper.detectCurrentLocation()
                if (detected != null) {
                    prefs.saveLocation(
                        latitude = detected.latitude,
                        longitude = detected.longitude,
                        name = detected.displayName,
                        mode = LocationMode.AUTO
                    )
                    pushCurrentSnapshotToCloud()
                    onResult(true)
                } else {
                    locationErrorFlow.value =
                        "Не удалось определить координаты автоматически. Пожалуйста, выберите город вручную."
                    onResult(false)
                }
            } finally {
                detectingLocationFlow.value = false
            }
        }
    }

    fun searchCities(query: String) {
        cityQueryFlow.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            cityResultsFlow.value = CuratedCities.presets
            searchingCitiesFlow.value = false
            citySearchErrorFlow.value = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(320L)
            searchingCitiesFlow.value = true
            citySearchErrorFlow.value = null
            try {
                val response = container.geocodingApiService.searchCities(query = trimmed)
                val mapped = response.results.orEmpty().map {
                    CityOption(
                        name = it.name,
                        country = it.country ?: "",
                        region = it.admin1,
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                }
                if (mapped.isNotEmpty()) {
                    cityResultsFlow.value = mapped
                } else {
                    val localMatches = CuratedCities.presets.filter {
                        it.name.contains(trimmed, ignoreCase = true)
                    }
                    cityResultsFlow.value = localMatches
                    if (localMatches.isEmpty()) {
                        citySearchErrorFlow.value =
                            "Город не найден. Попробуйте другое написание или введите координаты."
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                val localMatches = CuratedCities.presets.filter {
                    it.name.contains(trimmed, ignoreCase = true)
                }
                cityResultsFlow.value = localMatches.ifEmpty { CuratedCities.presets }
                citySearchErrorFlow.value =
                    "Нет связи со службой поиска городов — показаны быстрые варианты."
            } finally {
                searchingCitiesFlow.value = false
            }
        }
    }

    fun selectManualCity(city: CityOption) {
        viewModelScope.launch {
            locationErrorFlow.value = null
            prefs.saveLocation(
                latitude = city.latitude,
                longitude = city.longitude,
                name = city.displayTitle,
                mode = LocationMode.MANUAL
            )
            pushCurrentSnapshotToCloud()
        }
    }

    fun selectManualCoordinates(lat: Double, lon: Double, label: String) {
        viewModelScope.launch {
            locationErrorFlow.value = null
            val cleanName = label.trim().ifEmpty {
                String.format(Locale.US, "%.4f°, %.4f°", lat, lon)
            }
            prefs.saveLocation(
                latitude = lat,
                longitude = lon,
                name = cleanName,
                mode = LocationMode.MANUAL
            )
            pushCurrentSnapshotToCloud()
        }
    }

    fun setLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            prefs.setLocationMode(mode)
            pushCurrentSnapshotToCloud()
            if (mode == LocationMode.AUTO && PermissionStatusHelper.hasLocationPermission(appContext)) {
                detectLocationAutomatically()
            }
        }
    }

    fun updateMaxRetryAttempts(attempts: Int) {
        viewModelScope.launch {
            prefs.setMaxRetryAttempts(attempts)
            pushCurrentSnapshotToCloud()
        }
    }

    fun updateRetryDelaySeconds(seconds: Int) {
        viewModelScope.launch {
            prefs.setRetryDelaySeconds(seconds)
            pushCurrentSnapshotToCloud()
        }
    }

    fun updateTwilightPreference(preference: TwilightPreference) {
        viewModelScope.launch {
            prefs.setTwilightPreference(preference)
            pushCurrentSnapshotToCloud()
        }
    }

    fun updateAlarmOffsetMinutes(minutes: Int) {
        viewModelScope.launch {
            prefs.setAlarmOffsetMinutes(minutes)
            pushCurrentSnapshotToCloud()
        }
    }

    fun updateAlarmLabel(label: String) {
        viewModelScope.launch {
            prefs.setAlarmLabel(label)
            pushCurrentSnapshotToCloud()
        }
    }

    fun updateDailyMidnightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDailyMidnightEnabled(enabled)
            if (enabled) {
                MidnightScheduler.scheduleDailyMidnightCheck(appContext)
            } else {
                MidnightScheduler.cancelDailyMidnightCheck(appContext)
            }
            pushCurrentSnapshotToCloud()
        }
    }

    fun completeOnboarding(runInitialCheck: Boolean) {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
            val snapshot = prefs.getSnapshot()
            if (snapshot.dailyMidnightEnabled) {
                MidnightScheduler.scheduleDailyMidnightCheck(appContext)
            }
            pushSettingsToCloud(snapshot)
            if (runInitialCheck) {
                runManualCheckNow()
            }
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(false)
            pushCurrentSnapshotToCloud()
        }
    }

    fun syncNowToCloud() {
        viewModelScope.launch {
            pushCurrentSnapshotToCloud()
        }
    }

    private suspend fun pushCurrentSnapshotToCloud() {
        pushSettingsToCloud(prefs.getSnapshot())
    }

    private suspend fun pushSettingsToCloud(settings: AppSettings) {
        val result = firestoreRepo.saveSettings(
            userId = currentUserId,
            settings = settings,
            displayName = currentUserName
        )
        result.onSuccess {
            cloudSyncedFlow.value = true
            cloudSyncErrorFlow.value = null
        }.onFailure { err ->
            cloudSyncErrorFlow.value = err.localizedMessage ?: "Не удалось сохранить настройки в облако"
        }
    }

    fun runManualCheckNow() {
        if (checkLiveFlow.value is CheckLiveState.Running) return
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val snapshot = prefs.getSnapshot()
            checkLiveFlow.value = CheckLiveState.Running(
                attempt = 1,
                maxAttempts = snapshot.maxRetryAttempts,
                statusMessage = "Подготовка запроса…"
            )
            val outcome = container.twilightCheckEngine.executeTwilightCheck(
                isScheduledMidnight = false,
                onProgress = { attempt, max, msg ->
                    checkLiveFlow.value = CheckLiveState.Running(
                        attempt = attempt.coerceAtLeast(1),
                        maxAttempts = max,
                        statusMessage = msg
                    )
                }
            )
            checkLiveFlow.value = when (outcome) {
                is CheckOutcome.Success -> CheckLiveState.Succeeded(
                    times = outcome.times,
                    attemptsUsed = outcome.attemptsUsed,
                    maxAttempts = outcome.maxAttempts,
                    clockSetSuccess = outcome.clockDispatched,
                    message = outcome.message
                )
                is CheckOutcome.Failure -> CheckLiveState.Failed(
                    attemptsUsed = outcome.attemptsUsed,
                    maxAttempts = outcome.maxAttempts,
                    reason = outcome.reason
                )
            }
        }
    }

    fun dismissCheckBanner() {
        if (checkLiveFlow.value !is CheckLiveState.Running) {
            checkLiveFlow.value = CheckLiveState.Idle
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            checkDao.clearAll()
            val result = firestoreRepo.clearCheckLogs(currentUserId)
            result.onFailure { err ->
                cloudSyncErrorFlow.value = err.localizedMessage ?: "Не удалось очистить журнал в облаке"
            }
        }
    }

    private fun computeCountdownText(): String {
        val now = System.currentTimeMillis()
        val nextMidnight = MidnightScheduler.nextMidnightMillis(now)
        val diffMinutes = ((nextMidnight - now) / 60_000L).coerceAtLeast(0L)
        val hours = diffMinutes / 60L
        val mins = diffMinutes % 60L
        return "через $hours ч $mins мин"
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
