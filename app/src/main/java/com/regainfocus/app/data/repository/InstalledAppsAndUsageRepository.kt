package com.regainfocus.app.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.view.inputmethod.InputMethodManager
import com.regainfocus.app.data.local.AppDailyLimitEntity
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.domain.model.AppUsageItem
import com.regainfocus.app.domain.model.DailyUsagePoint
import com.regainfocus.app.domain.model.InstalledAppInfo
import com.regainfocus.app.domain.model.ScreenTimeOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class InstalledAppsAndUsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val labelCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var cachedInstalledApps: List<InstalledAppInfo>? = null

    fun hasUsageStatsPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getLauncherPackages(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    fun getImePackages(): Set<String> {
        val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        return imm?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
    }

    fun getAppLabel(packageName: String): String {
        labelCache[packageName]?.let { return it }
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString().also { labelCache[packageName] = it }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName).also { iconCache[packageName] = it }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getLaunchableApps(forceRefresh: Boolean = false): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedInstalledApps?.let { return@withContext it }
        }
        val launcherPackages = getLauncherPackages()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(mainIntent, 0)
        val apps = resolved
            .mapNotNull { ri -> ri.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { info ->
                info.packageName != appContext.packageName &&
                    info.packageName !in launcherPackages &&
                    info.packageName != "com.android.dialer" &&
                    info.packageName != "com.google.android.dialer"
            }
            .map { info ->
                val label = packageManager.getApplicationLabel(info).toString()
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull()
                if (icon != null) iconCache[info.packageName] = icon
                labelCache[info.packageName] = label
                InstalledAppInfo(
                    packageName = info.packageName,
                    label = label,
                    category = resolveCategoryLabel(info),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                    icon = icon
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        cachedInstalledApps = apps
        apps
    }

    private fun resolveCategoryLabel(info: ApplicationInfo): String {
        val cat = info.category
        return when (cat) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            else -> {
                val pkg = info.packageName.lowercase(Locale.US)
                when {
                    listOf("instagram", "tiktok", "musically", "snapchat", "facebook", "twitter", "reddit", "threads", "discord").any { pkg.contains(it) } -> "Social"
                    listOf("youtube", "netflix", "primevideo", "hotstar", "twitch").any { pkg.contains(it) } -> "Video"
                    listOf("game", "supercell", "roblox", "pubg", "bgmi", "freefire", "minecraft").any { pkg.contains(it) } -> "Games"
                    listOf("chrome", "browser", "firefox", "brave", "opera", "edge").any { pkg.contains(it) } -> "Browser"
                    else -> "Apps"
                }
            }
        }
    }

    /**
     * Queries exact today foreground usage for a specific package or all packages via UsageEvents,
     * used both by Screen-Time UI and real-time Per-App Daily Limit enforcement.
     */
    fun queryRangeUsageByPackage(startMillis: Long, endMillis: Long): Pair<Map<String, Long>, Map<String, Int>> {
        if (!hasUsageStatsPermission() || endMillis <= startMillis) {
            return emptyMap<String, Long>() to emptyMap()
        }
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap<String, Long>() to emptyMap()

        val launchers = getLauncherPackages()
        val imes = getImePackages()

        val durations = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        val activeStartByPkg = HashMap<String, Long>()

        val events = usm.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == "com.android.systemui" || pkg in launchers || pkg in imes) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!activeStartByPkg.containsKey(pkg)) {
                        activeStartByPkg[pkg] = event.timeStamp
                        launches[pkg] = (launches[pkg] ?: 0) + 1
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val started = activeStartByPkg.remove(pkg)
                    if (started != null && event.timeStamp > started) {
                        durations[pkg] = (durations[pkg] ?: 0L) + (event.timeStamp - started)
                    }
                }
            }
        }

        // Close any currently-foreground app interval at endMillis
        for ((pkg, started) in activeStartByPkg) {
            if (endMillis > started) {
                durations[pkg] = (durations[pkg] ?: 0L) + (endMillis - started)
            }
        }

        // Fallback if OS purged raw events (e.g., older than ~7 days): use aggregated queryUsageStats
        if (durations.isEmpty()) {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis).orEmpty()
            for (s in stats) {
                val pkg = s.packageName ?: continue
                if (pkg == "com.android.systemui" || pkg in launchers || pkg in imes) continue
                if (s.totalTimeInForeground > 0L) {
                    durations[pkg] = (durations[pkg] ?: 0L) + s.totalTimeInForeground
                }
            }
        }

        return durations to launches
    }

    fun getRecentForegroundPackage(lookbackMillis: Long = 4_000L): Pair<String, String?>? {
        if (!hasUsageStatsPermission()) return null
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - lookbackMillis, now) ?: return null
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestClass: String? = null
        var latestTs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestTs) {
                latestTs = event.timeStamp
                latestPkg = event.packageName
                latestClass = event.className
            }
        }
        return latestPkg?.let { it to latestClass }
    }

    suspend fun loadScreenTimeOverview(
        limits: List<AppDailyLimitEntity>,
        sessions: List<FocusSessionEntity>,
        dailyScreenTimeGoalMinutes: Int
    ): ScreenTimeOverview = withContext(Dispatchers.IO) {
        val permitted = hasUsageStatsPermission()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowMillis = System.currentTimeMillis()
        val todayStartMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

        val focusMinutesByDay = HashMap<Long, Int>()
        for (s in sessions) {
            val d = Instant.ofEpochMilli(s.startTimeEpochMillis).atZone(zone).toLocalDate().toEpochDay()
            focusMinutesByDay[d] = (focusMinutesByDay[d] ?: 0) + (s.effectiveWorkSeconds / 60L).toInt()
        }

        if (!permitted) {
            val empty7 = (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                DailyUsagePoint(
                    epochDay = date.toEpochDay(),
                    dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    screenTimeMinutes = 0,
                    focusMinutes = focusMinutesByDay[date.toEpochDay()] ?: 0,
                    goalMinutes = dailyScreenTimeGoalMinutes
                )
            }
            val empty30 = (29 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                DailyUsagePoint(
                    epochDay = date.toEpochDay(),
                    dayLabel = "${date.dayOfMonth}",
                    screenTimeMinutes = 0,
                    focusMinutes = focusMinutesByDay[date.toEpochDay()] ?: 0,
                    goalMinutes = dailyScreenTimeGoalMinutes
                )
            }
            return@withContext ScreenTimeOverview(
                hasUsagePermission = false,
                todayTotalMillis = 0L,
                weeklyTotalMillis = 0L,
                monthlyTotalMillis = 0L,
                dailyAverageWeekMillis = 0L,
                hourlyMinutesToday = List(24) { 0 },
                topAppsToday = emptyList(),
                topAppsWeek = emptyList(),
                topAppsMonth = emptyList(),
                last7DaysScreenMinutes = empty7,
                last30DaysScreenMinutes = empty30
            )
        }

        val limitMap = limits.associateBy { it.packageName }

        // Today + Hourly buckets
        val (todayMap, todayLaunches) = queryRangeUsageByPackage(todayStartMillis, nowMillis)
        val todayTotalMillis = todayMap.values.sum()

        val hourlyBuckets = IntArray(24)
        val currentHour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
        for (h in 0..currentHour) {
            val hStart = todayStartMillis + h * 3_600_000L
            val hEnd = minOf(nowMillis, hStart + 3_600_000L)
            if (hEnd > hStart) {
                val (hMap, _) = queryRangeUsageByPackage(hStart, hEnd)
                hourlyBuckets[h] = (hMap.values.sum() / 60_000L).toInt().coerceIn(0, 60)
            }
        }

        // Daily points for last 30 days (using UsageStatsManager INTERVAL_DAILY for historical days + exact UsageEvents for recent days)
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val launchers = getLauncherPackages()
        val imes = getImePackages()

        val weekPkgTotals = HashMap<String, Long>()
        val monthPkgTotals = HashMap<String, Long>()
        val points30 = ArrayList<DailyUsagePoint>(30)

        for (offset in 29 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = if (offset == 0) nowMillis else date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val dayPkgMap: Map<String, Long> = if (offset <= 6) {
                queryRangeUsageByPackage(dayStart, dayEnd).first
            } else {
                val raw = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd).orEmpty()
                val m = HashMap<String, Long>()
                for (st in raw) {
                    val p = st.packageName ?: continue
                    if (p == "com.android.systemui" || p in launchers || p in imes) continue
                    // Filter stats bucket whose lastTimeUsed falls inside this day
                    if (st.totalTimeInForeground > 0L && st.lastTimeUsed in dayStart..dayEnd) {
                        m[p] = max(m[p] ?: 0L, st.totalTimeInForeground)
                    }
                }
                m
            }

            for ((pkg, dur) in dayPkgMap) {
                monthPkgTotals[pkg] = (monthPkgTotals[pkg] ?: 0L) + dur
                if (offset <= 6) {
                    weekPkgTotals[pkg] = (weekPkgTotals[pkg] ?: 0L) + dur
                }
            }

            val screenMins = (dayPkgMap.values.sum() / 60_000L).toInt()
            val label = if (offset <= 6) {
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } else {
                "${date.dayOfMonth}"
            }
            points30.add(
                DailyUsagePoint(
                    epochDay = date.toEpochDay(),
                    dayLabel = label,
                    screenTimeMinutes = screenMins,
                    focusMinutes = focusMinutesByDay[date.toEpochDay()] ?: 0,
                    goalMinutes = dailyScreenTimeGoalMinutes
                )
            )
        }

        val points7 = points30.takeLast(7)
        val weeklyMillis = weekPkgTotals.values.sum()
        val monthlyMillis = monthPkgTotals.values.sum()

        fun buildTopList(source: Map<String, Long>, launchMap: Map<String, Int>): List<AppUsageItem> {
            val total = source.values.sum().coerceAtLeast(1L)
            return source.entries
                .filter { it.value >= 30_000L } // >= 30s
                .sortedByDescending { it.value }
                .take(25)
                .map { (pkg, dur) ->
                    val lim = limitMap[pkg]
                    AppUsageItem(
                        packageName = pkg,
                        appLabel = getAppLabel(pkg),
                        usageMillis = dur,
                        launchCount = launchMap[pkg] ?: 0,
                        fractionOfTotal = (dur.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        dailyLimitMinutes = lim?.takeIf { it.isEnabled }?.dailyLimitMinutes,
                        isStrictLimitLocked = (lim?.strictLockedUntilMillis ?: 0L) > nowMillis,
                        icon = getAppIcon(pkg)
                    )
                }
        }

        ScreenTimeOverview(
            hasUsagePermission = true,
            todayTotalMillis = todayTotalMillis,
            weeklyTotalMillis = weeklyMillis,
            monthlyTotalMillis = monthlyMillis,
            dailyAverageWeekMillis = weeklyMillis / 7L,
            hourlyMinutesToday = hourlyBuckets.toList(),
            topAppsToday = buildTopList(todayMap, todayLaunches),
            topAppsWeek = buildTopList(weekPkgTotals, emptyMap()),
            topAppsMonth = buildTopList(monthPkgTotals, emptyMap()),
            last7DaysScreenMinutes = points7,
            last30DaysScreenMinutes = points30
        )
    }

    fun getExceededDailyLimitPackages(limits: List<AppDailyLimitEntity>): Set<String> {
        val enabled = limits.filter { it.isEnabled && it.dailyLimitMinutes > 0 }
        if (enabled.isEmpty() || !hasUsageStatsPermission()) return emptySet()

        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val (todayUsage, _) = queryRangeUsageByPackage(todayStart, System.currentTimeMillis())

        return enabled
            .filter { lim -> (todayUsage[lim.packageName] ?: 0L) >= lim.dailyLimitMinutes * 60_000L }
            .map { it.packageName }
            .toSet()
    }
}
