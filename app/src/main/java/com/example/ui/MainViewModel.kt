package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TwilightApp
import com.example.data.local.TwilightCheckEntity
import com.example.data.model.AppSettings
import com.example.data.model.CheckLiveState
import com.example.data.model.CityOption
import com.example.data.model.LocationMode
import com.example.data.model.PermissionStatus
import com.example.data.model.TwilightPreference
import com.example.data.remote.CuratedCities
import com.example.domain.CheckOutcome
import com.example.scheduler.MidnightScheduler
import com.example.system.PermissionStatusHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class MainUiState(
    val isLoading: Boolean = true,
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
    val countdownToMidnightText: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TwilightApp
    private val container = app.container
    private val prefs = container.preferencesRepository
    private val checkDao = container.checkDao

    private val permissionsFlow = MutableStateFlow(PermissionStatusHelper.getStatus(app))
    private val checkLiveFlow = MutableStateFlow<CheckLiveState>(CheckLiveState.Idle)
    private val detectingLocationFlow = MutableStateFlow(false)
    private val locationErrorFlow = MutableStateFlow<String?>(null)
    private val cityQueryFlow = MutableStateFlow("")
    private val cityResultsFlow = MutableStateFlow(CuratedCities.presets)
    private val searchingCitiesFlow = MutableStateFlow(false)
    private val citySearchErrorFlow = MutableStateFlow<String?>(null)
    private val countdownFlow = MutableStateFlow(computeCountdownText())
    private val loadedSettingsFlow = MutableStateFlow<AppSettings?>(null)

    private var searchJob: Job? = null
    private var checkJob: Job? = null

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
    }

    val uiState: StateFlow<MainUiState> = combine(
        loadedSettingsFlow,
        permissionsFlow,
        checkDao.observeAllChecks(),
        checkLiveFlow,
        detectingLocationFlow,
        locationErrorFlow,
        cityQueryFlow,
        cityResultsFlow,
        searchingCitiesFlow,
        citySearchErrorFlow,
        countdownFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as AppSettings?
        val permissions = values[1] as PermissionStatus
        @Suppress("UNCHECKED_CAST")
        val history = values[2] as List<TwilightCheckEntity>
        val checkLive = values[3] as CheckLiveState
        val detectingLoc = values[4] as Boolean
        val locError = values[5] as String?
        val cityQuery = values[6] as String
        @Suppress("UNCHECKED_CAST")
        val cityResults = values[7] as List<CityOption>
        val searchingCities = values[8] as Boolean
        val cityError = values[9] as String?
        val countdown = values[10] as String

        MainUiState(
            isLoading = settings == null,
            settings = settings ?: AppSettings(),
            permissions = permissions,
            history = history,
            latestCheck = history.firstOrNull(),
            checkLiveState = checkLive,
            isDetectingLocation = detectingLoc,
            locationDetectError = locError,
            citySearchQuery = cityQuery,
            citySearchResults = cityResults,
            isSearchingCities = searchingCities,
            citySearchError = cityError,
            countdownToMidnightText = countdown
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MainUiState()
    )

    fun refreshPermissions() {
        permissionsFlow.value = PermissionStatusHelper.getStatus(app)
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
                        citySearchErrorFlow.value = "Город не найден. Попробуйте другое написание или введите координаты."
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                val localMatches = CuratedCities.presets.filter {
                    it.name.contains(trimmed, ignoreCase = true)
                }
                cityResultsFlow.value = localMatches.ifEmpty { CuratedCities.presets }
                citySearchErrorFlow.value = "Нет связи со службой поиска городов — показаны быстрые варианты."
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
        }
    }

    fun setLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            prefs.setLocationMode(mode)
            if (mode == LocationMode.AUTO && PermissionStatusHelper.hasLocationPermission(app)) {
                detectLocationAutomatically()
            }
        }
    }

    fun updateMaxRetryAttempts(attempts: Int) {
        viewModelScope.launch { prefs.setMaxRetryAttempts(attempts) }
    }

    fun updateRetryDelaySeconds(seconds: Int) {
        viewModelScope.launch { prefs.setRetryDelaySeconds(seconds) }
    }

    fun updateTwilightPreference(preference: TwilightPreference) {
        viewModelScope.launch { prefs.setTwilightPreference(preference) }
    }

    fun updateAlarmOffsetMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setAlarmOffsetMinutes(minutes) }
    }

    fun updateAlarmLabel(label: String) {
        viewModelScope.launch { prefs.setAlarmLabel(label) }
    }

    fun updateDailyMidnightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDailyMidnightEnabled(enabled)
            if (enabled) {
                MidnightScheduler.scheduleDailyMidnightCheck(app)
            } else {
                MidnightScheduler.cancelDailyMidnightCheck(app)
            }
        }
    }

    fun completeOnboarding(runInitialCheck: Boolean) {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
            val snapshot = prefs.getSnapshot()
            if (snapshot.dailyMidnightEnabled) {
                MidnightScheduler.scheduleDailyMidnightCheck(app)
            }
            if (runInitialCheck) {
                runManualCheckNow()
            }
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(false)
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
}
