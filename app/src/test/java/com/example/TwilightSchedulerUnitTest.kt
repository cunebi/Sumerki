package com.example

import com.example.data.model.TwilightPreference
import com.example.data.remote.SunriseSunsetResults
import com.example.scheduler.MidnightScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TwilightSchedulerUnitTest {

    @Test
    fun nextMidnightMillis_isExactZeroZeroOfFollowingDay() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 23)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 45)
            set(Calendar.MILLISECOND, 123)
        }

        val nextMidnight = MidnightScheduler.nextMidnightMillis(now.timeInMillis)
        val result = Calendar.getInstance().apply { timeInMillis = nextMidnight }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, result.get(Calendar.MONTH))
        assertEquals(24, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(0, result.get(Calendar.MILLISECOND))
        assertTrue(nextMidnight > now.timeInMillis)
    }

    @Test
    fun parseIsoUtc_rejectsPolar1970PlaceholderAndAcceptsValidDate() {
        val validIso = "2026-06-21T01:15:00+00:00"
        val polarIso = "1970-01-01T00:00:01+00:00"

        assertNotNull(parseIsoForTest(validIso))
        assertNull(parseIsoForTest(polarIso))
        assertEquals(TwilightPreference.EARLIEST_AUTO.shortTitleRu, "Самые ранние")
    }

    private fun parseIsoForTest(iso: String): Date? {
        val normalized = if (iso.length >= 6 && iso[iso.length - 3] == ':') {
            iso.substring(0, iso.length - 3) + iso.substring(iso.length - 2)
        } else iso
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = parser.parse(normalized) ?: return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = parsed }
        return if (cal.get(Calendar.YEAR) < 2000) null else parsed
    }

    @Test
    fun sunriseSunsetResults_holdsAllTwilightStages() {
        val res = SunriseSunsetResults(
            sunrise = "2026-11-23T05:18:00+00:00",
            sunset = "2026-11-23T13:10:00+00:00",
            civilTwilightBegin = "2026-11-23T04:36:00+00:00",
            nauticalTwilightBegin = "2026-11-23T03:50:00+00:00",
            astronomicalTwilightBegin = "2026-11-23T03:05:00+00:00"
        )
        assertNotNull(res.astronomicalTwilightBegin)
        assertNotNull(res.nauticalTwilightBegin)
        assertNotNull(res.civilTwilightBegin)
        assertNotNull(res.sunrise)
    }

    @Test
    fun moscowTwilightFormatsInLocationTimezoneEvenWhenDeviceIsPst() {
        val originalDefault = TimeZone.getDefault()
        try {
            // Simulate Android Emulator running in America/Los_Angeles (-08:00)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            // 02:59 UTC is 05:59 MSK (Europe/Moscow), NOT 18:59 PST!
            val utcIso = "2026-11-23T02:59:00+00:00"
            val parsed = parseIsoForTest(utcIso)!!
            val moscowTz = TimeZone.getTimeZone("Europe/Moscow")

            val cal = Calendar.getInstance(moscowTz).apply { time = parsed }
            assertEquals(5, cal.get(Calendar.HOUR_OF_DAY))
            assertEquals(59, cal.get(Calendar.MINUTE))

            val formatted = SimpleDateFormat("HH:mm", Locale.US).apply {
                timeZone = moscowTz
            }.format(parsed)
            assertEquals("05:59", formatted)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
