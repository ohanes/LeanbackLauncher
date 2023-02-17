package com.amazon.tv.leanbacklauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.amazon.tv.firetv.leanbacklauncher.apps.AppCategory
import com.amazon.tv.firetv.leanbacklauncher.util.FireTVUtils
import com.amazon.tv.firetv.leanbacklauncher.util.SettingsUtil.SettingsType
import com.amazon.tv.firetv.leanbacklauncher.util.SharedPreferencesUtil
import com.amazon.tv.leanbacklauncher.BuildConfig
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.util.CSyncTask
import com.amazon.tv.leanbacklauncher.util.Util
import java.util.*

class LaunchPointList(ctx: Context) {
    private var mAllLaunchPoints: MutableList<LaunchPoint>
    private val mCachedActions: Queue<CachedAction>
    private val mContext: Context
    private var mExcludeChannelActivities = false
    private val mInstallingLaunchPoints: MutableList<LaunchPoint>
    private var mIsReady = false
    private val mListeners: MutableList<Listener>
    private val mLock: Any
    private val mNonUpdatableBlacklist: HashMap<String?, Int>
    private var mSettingsLaunchPoints: ArrayList<LaunchPoint> = arrayListOf()
    private var mShouldNotify = false
    private val mUpdatableBlacklist: HashMap<String?, Int>
    private var rawFilter: RawFilter
    private var prefUtil: SharedPreferencesUtil? = null

//    private val mainScope = CoroutineScope(Dispatchers.Main)
//    private val defaultScope = CoroutineScope(Dispatchers.Default)

    companion object {
        private val TAG by lazy {
            if (BuildConfig.DEBUG) ("[*]" + LaunchPointList::class.java.simpleName).take(
                21
            ) else LaunchPointList::class.java.simpleName
        }
    }

    init {
        mCachedActions = LinkedList()
        mListeners = LinkedList()
        mAllLaunchPoints = LinkedList()
        mInstallingLaunchPoints = LinkedList()
        mUpdatableBlacklist = HashMap()
        mNonUpdatableBlacklist = HashMap()
        mLock = Any()
        mContext = ctx
        rawFilter = object : RawFilter() {
            override fun include(point: ResolveInfo?): Boolean {
                return when {
                    point?.toString()
                        ?.contains("com.amazon.tv.leanbacklauncher.MainActivity") == true -> true
                    point?.toString()
                        ?.contains("com.amazon.tv.launcher/.ui.DebugActivity") == true -> true
                    point?.activityInfo?.packageName?.startsWith("com.amazon.avod") == true -> true
                    point?.activityInfo?.packageName?.startsWith("com.amazon.bueller") == true -> true
                    point?.activityInfo?.packageName?.startsWith("com.amazon.ftv.screensaver") == true -> true
                    else -> false
                }
            }
        }
        if (prefUtil == null) {
            prefUtil = SharedPreferencesUtil.instance(mContext)
        }
    }

    interface Listener {
        fun onLaunchPointListGeneratorReady()
        fun onLaunchPointsAddedOrUpdated(arrayList: ArrayList<LaunchPoint>)
        fun onLaunchPointsRemoved(arrayList: ArrayList<LaunchPoint>)
        fun onSettingsChanged()
    }

    abstract class RawFilter {
        abstract fun include(point: ResolveInfo?): Boolean
    }

    private inner class CachedAction {
        var mAction: Int
        var mLaunchPoint: LaunchPoint? = null
        var mPkgName: String? = null
        var mSuccess: Boolean
        var mUpdatable: Boolean

        constructor(action: Int, pkgName: String?) {
            mSuccess = false
            mUpdatable = true
            mAction = action
            mPkgName = pkgName
        }

        constructor(action: Int, pkgName: String?, updatable: Boolean) : this(action, pkgName) {
            mUpdatable = updatable
        }

        constructor(action: Int, launchPoint: LaunchPoint?) {
            mSuccess = false
            mUpdatable = true
            mAction = action
            mLaunchPoint = launchPoint
        }

        constructor(action: Int, launchPoint: LaunchPoint?, success: Boolean) : this(
            action,
            launchPoint
        ) {
            mSuccess = success
        }

//        @SuppressLint("PrivateResource")
//        fun apply() {
//            when (mAction) {
//                0 -> {
//                    addOrUpdatePackage(mPkgName)
//                    removePackage(mPkgName)
//                    addToBlacklist(mPkgName, mUpdatable)
//                    this@LaunchPointList.removeFromBlacklist(mPkgName, mUpdatable)
//                    addOrUpdateInstallingLaunchPoint(mLaunchPoint)
//                    removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                }
//                1 -> {
//                    removePackage(mPkgName)
//                    addToBlacklist(mPkgName, mUpdatable)
//                    this@LaunchPointList.removeFromBlacklist(mPkgName, mUpdatable)
//                    addOrUpdateInstallingLaunchPoint(mLaunchPoint)
//                    removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                }
//                2 -> {
//                    addToBlacklist(mPkgName, mUpdatable)
//                    this@LaunchPointList.removeFromBlacklist(mPkgName, mUpdatable)
//                    addOrUpdateInstallingLaunchPoint(mLaunchPoint)
//                    removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                }
//                3 -> {
//                    this@LaunchPointList.removeFromBlacklist(mPkgName, mUpdatable)
//                    addOrUpdateInstallingLaunchPoint(mLaunchPoint)
//                    removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                }
//                4 -> {
//                    addOrUpdateInstallingLaunchPoint(mLaunchPoint)
//                    removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                }
//                5 -> removeInstallingLaunchPoint(mLaunchPoint, mSuccess)
//                else -> {
//                }
//            }
//        }
    }

    private inner class CreateLaunchPointListTask(private val mFilterChannelsActivities: Boolean) :
        CSyncTask<Void?, Void?, List<LaunchPoint>?>("CreateLaunchPointList") {
        override fun doInBackground(vararg params: Void?): List<LaunchPoint> {
            val mainIntent = Intent("android.intent.action.MAIN")
            mainIntent.addCategory("android.intent.category.LAUNCHER")
            val tvIntent = Intent("android.intent.action.MAIN")
            tvIntent.addCategory("android.intent.category.LEANBACK_LAUNCHER")
            val launcherItems: MutableList<LaunchPoint> = LinkedList()
            val pkgMan = mContext.packageManager
            val normLaunchPoints = pkgMan.queryIntentActivities(
                mainIntent, PackageManager.GET_META_DATA
            )
            val tvLaunchPoints = pkgMan.queryIntentActivities(
                tvIntent, PackageManager.GET_META_DATA
            )
            val rawComponents: MutableMap<String, String> = HashMap()
            val allLaunchPoints: MutableList<ResolveInfo> = ArrayList()
            if (tvLaunchPoints.size > 0) {
                for (itemTvLaunchPoint in tvLaunchPoints) {
                    if (itemTvLaunchPoint.activityInfo != null && itemTvLaunchPoint.activityInfo.packageName != null && itemTvLaunchPoint.activityInfo.name != null) {
                        rawComponents[itemTvLaunchPoint.activityInfo.packageName] =
                            itemTvLaunchPoint.activityInfo.name
                        allLaunchPoints.add(itemTvLaunchPoint)
                    }
                }
            }
            if (normLaunchPoints.size > 0) {
                for (itemRawLaunchPoint in normLaunchPoints) {
                    if (itemRawLaunchPoint.activityInfo != null && itemRawLaunchPoint.activityInfo.packageName != null && itemRawLaunchPoint.activityInfo.name != null) {
                        // any system app that isn't TV-optimized likely isn't something the user needs or wants [except for Amazon Music & Photos (which apparently don't get leanback launchers :\)]
                        if ((prefUtil!!.isAllAppsShown()) ||
                            !Util.isSystemApp(mContext, itemRawLaunchPoint.activityInfo.packageName)
                        ) {
                            if (!rawComponents.containsKey(itemRawLaunchPoint.activityInfo.packageName) &&
                                itemRawLaunchPoint.activityInfo.packageName != mContext.packageName &&
                                !rawFilter.include(itemRawLaunchPoint) // filter
                            ) {
                                allLaunchPoints.add(itemRawLaunchPoint)
                            } // TODO optimize & don't hardcode
                        }
                    }
                }
            }
            for (x in 0 until allLaunchPoints.size) {
                val info = allLaunchPoints[x]
                val activityInfo = info.activityInfo
                if (activityInfo != null) {
                    launcherItems.add(LaunchPoint(mContext, pkgMan, info))
                }
            }
            return launcherItems
        }

        override fun onPostExecute(launcherItems: List<LaunchPoint>?) {
            synchronized(mLock) {
                mAllLaunchPoints = ArrayList()
                launcherItems?.let {
                    mAllLaunchPoints.addAll(it)
                }
            }
            synchronized(mCachedActions) {
                Log.i(TAG, "mCachedActions is empty: " + mCachedActions.isEmpty())
                mIsReady = true
                mShouldNotify = true
                for (onLaunchPointListGeneratorReady in mListeners) {
                    // Log.i(TAG, "onLaunchPointListGeneratorReady->className:" + onLaunchPointListGeneratorReady.javaClass.name)
                    onLaunchPointListGeneratorReady.onLaunchPointListGeneratorReady()
                }
            }
        }
    }

//    private suspend fun createLaunchPointList(mFilterChannelsActivities: Boolean): List<LaunchPoint> {
//        val mainIntent = Intent("android.intent.action.MAIN")
//        mainIntent.addCategory("android.intent.category.LAUNCHER")
//        val tvIntent = Intent("android.intent.action.MAIN")
//        tvIntent.addCategory("android.intent.category.LEANBACK_LAUNCHER")
//        val launcherItems: MutableList<LaunchPoint> = LinkedList()
//        // This is CPU intensive task so run it on the Default dispatcher
//        val deferred = defaultScope.async {
//            val pkgMan = mContext.packageManager
//            val normLaunchPoints = pkgMan.queryIntentActivities(
//                mainIntent, PackageManager.GET_META_DATA
//            )
//            val tvLaunchPoints = pkgMan.queryIntentActivities(
//                tvIntent, PackageManager.GET_META_DATA
//            )
//            val rawComponents: MutableMap<String, String> = HashMap()
//            val allLaunchPoints: MutableList<ResolveInfo> = ArrayList()
//            if (tvLaunchPoints.size > 0) {
//                for (itemTvLaunchPoint in tvLaunchPoints) {
//                    if (itemTvLaunchPoint.activityInfo != null && itemTvLaunchPoint.activityInfo.packageName != null && itemTvLaunchPoint.activityInfo.name != null) {
//                        rawComponents[itemTvLaunchPoint.activityInfo.packageName] =
//                            itemTvLaunchPoint.activityInfo.name
//                        allLaunchPoints.add(itemTvLaunchPoint)
//                    }
//                }
//            }
//            if (normLaunchPoints.size > 0) {
//                for (itemRawLaunchPoint in normLaunchPoints) {
//                    if (itemRawLaunchPoint.activityInfo != null && itemRawLaunchPoint.activityInfo.packageName != null && itemRawLaunchPoint.activityInfo.name != null) {
//                        // any system app that isn't TV-optimized likely isn't something the user needs or wants [except for Amazon Music & Photos (which apparently don't get leanback launchers :\)]
//                        if ((prefUtil!!.isAllAppsShown()) ||
//                            !Util.isSystemApp(mContext, itemRawLaunchPoint.activityInfo.packageName)
//                        ) {
//                            if (!rawComponents.containsKey(itemRawLaunchPoint.activityInfo.packageName) &&
//                                itemRawLaunchPoint.activityInfo.packageName != mContext.packageName &&
//                                !rawFilter.include(itemRawLaunchPoint) // filter
//                            ) {
//                                allLaunchPoints.add(itemRawLaunchPoint)
//                            }
//                        }
//                    }
//                }
//            }
//            for (x in 0 until allLaunchPoints.size) {
//                val info = allLaunchPoints[x]
//                val activityInfo = info.activityInfo
//                if (activityInfo != null) {
//                    launcherItems.add(LaunchPoint(mContext, pkgMan, info))
//                }
//            }
//        }
//        deferred.await()
//        synchronized(mLock) {
//            mAllLaunchPoints = ArrayList()
//            mAllLaunchPoints.addAll(launcherItems)
//        }
//        synchronized(mCachedActions) {
//            Log.i(TAG, "mCachedActions is empty: " + mCachedActions.isEmpty())
//            mIsReady = true
//            mShouldNotify = true
//            for (onLaunchPointListGeneratorReady in mListeners) {
//                // Log.i(TAG, "onLaunchPointListGeneratorReady->className:" + onLaunchPointListGeneratorReady.javaClass.name)
//                onLaunchPointListGeneratorReady.onLaunchPointListGeneratorReady()
//            }
//        }
//        return launcherItems
//    }

    fun setExcludeChannelActivities(excludeChannelActivities: Boolean) {
        if (mExcludeChannelActivities != excludeChannelActivities) {
            mExcludeChannelActivities = excludeChannelActivities
            refreshLaunchPointList()
        }
    }

    fun registerChangeListener(listener: Listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener)
        }
    }

    fun addOrUpdatePackage(pkgName: String?) {
        if (!pkgName.isNullOrEmpty()) {
            synchronized(mCachedActions) {
                if (mIsReady) {
                    synchronized(mLock) {
                        // TODO: sync with CreateLaunchPointListTask
                        val launchPoints = createLaunchPoints(pkgName)
                        if (launchPoints.isNotEmpty()) {
                            // remove every launcher with this package
                            val filter = mAllLaunchPoints.filter {
                                pkgName == it.packageName
                            }
                            mAllLaunchPoints.removeAll(filter)
                            mAllLaunchPoints.addAll(launchPoints)
                            if (!isBlacklisted(pkgName) && mShouldNotify) {
                                for (cl in mListeners) {
                                    cl.onLaunchPointsAddedOrUpdated(launchPoints)
                                }
                            }
                        }
                        if (packageHasSettingsEntry(pkgName)) {
                            for (cl2 in mListeners) {
                                cl2.onSettingsChanged()
                            }
                        }
                    }
                    return
                }
                mCachedActions.add(CachedAction(0, pkgName))
            }
        }
    }

    fun removePackage(pkgName: String?) {
        if (!pkgName.isNullOrEmpty()) {
            synchronized(mCachedActions) {
                if (mIsReady) {
                    synchronized(mLock) {
                        val removedLaunchPoints = ArrayList<LaunchPoint>()
                        getLaunchPointsByPackage(
                            mInstallingLaunchPoints,
                            removedLaunchPoints,
                            pkgName,
                            true
                        )
                        getLaunchPointsByPackage(
                            mAllLaunchPoints,
                            removedLaunchPoints,
                            pkgName,
                            true
                        )
                        if (!(removedLaunchPoints.isEmpty() || isBlacklisted(pkgName))) {
                            if (mShouldNotify) {
                                for (cl in mListeners) {
                                    cl.onLaunchPointsRemoved(removedLaunchPoints)
                                }
                            }
                        }
                        if (packageHasSettingsEntry(pkgName)) {
                            for (cl2 in mListeners) {
                                cl2.onSettingsChanged()
                            }
                        }
                    }
                    return
                }
                mCachedActions.add(CachedAction(1, pkgName))
            }
        }
    }

    @JvmOverloads
    fun addToBlacklist(pkgName: String?, updatable: Boolean = true): Boolean {
        if (pkgName.isNullOrEmpty()) {
            return false
        }
        synchronized(mCachedActions) {
            if (mIsReady) {
                var added = false
                synchronized(mLock) {
                    val blacklist = if (updatable) mUpdatableBlacklist else mNonUpdatableBlacklist
                    var occurrences = blacklist[pkgName]
                    val otherOccurrences =
                        (if (updatable) mNonUpdatableBlacklist else mUpdatableBlacklist)[pkgName]
                    if (occurrences == null || occurrences <= 0) {
                        occurrences = 0
                        if (otherOccurrences == null || otherOccurrences <= 0) {
                            added = true
                            val blacklistedLaunchPoints = ArrayList<LaunchPoint>()
                            getLaunchPointsByPackage(
                                mInstallingLaunchPoints,
                                blacklistedLaunchPoints,
                                pkgName,
                                false
                            )
                            getLaunchPointsByPackage(
                                mAllLaunchPoints,
                                blacklistedLaunchPoints,
                                pkgName,
                                false
                            )
                            if (blacklistedLaunchPoints.isNotEmpty() && mShouldNotify) {
                                for (cl in mListeners) {
                                    cl.onLaunchPointsRemoved(blacklistedLaunchPoints)
                                }
                            }
                        }
                    }
                    occurrences += 1
                    blacklist.put(pkgName, occurrences)
                }
                return added
            }
            mCachedActions.add(CachedAction(2, pkgName, updatable))
            return false
        }
    }

    fun removeFromBlacklist(pkgName: String?): Boolean {
        return removeFromBlacklist(pkgName, force = false, updatable = true)
    }

    fun removeFromBlacklist(pkgName: String?, updatable: Boolean): Boolean {
        return removeFromBlacklist(pkgName, false, updatable)
    }

    private fun removeFromBlacklist(pkgName: String?, force: Boolean, updatable: Boolean): Boolean {
        if (pkgName.isNullOrEmpty()) {
            return false
        }
        synchronized(mCachedActions) {
            if (mIsReady) {
                var removed = false
                synchronized(mLock) {
                    val blacklist = if (updatable) mUpdatableBlacklist else mNonUpdatableBlacklist
                    var occurrences = blacklist[pkgName]
                    val otherOccurrences =
                        (if (updatable) mNonUpdatableBlacklist else mUpdatableBlacklist)[pkgName]
                    if (occurrences != null) {
                        occurrences -= 1
                        if (occurrences <= 0 || force) {
                            blacklist.remove(pkgName)
                            if (otherOccurrences == null) {
                                removed = true
                                val blacklistedLaunchPoints = ArrayList<LaunchPoint>()
                                getLaunchPointsByPackage(
                                    mInstallingLaunchPoints,
                                    blacklistedLaunchPoints,
                                    pkgName,
                                    false
                                )
                                getLaunchPointsByPackage(
                                    mAllLaunchPoints,
                                    blacklistedLaunchPoints,
                                    pkgName,
                                    false
                                )
                                if (blacklistedLaunchPoints.isNotEmpty() && mShouldNotify) {
                                    for (cl in mListeners) {
                                        cl.onLaunchPointsAddedOrUpdated(blacklistedLaunchPoints)
                                    }
                                }
                            }
                        } else {
                            blacklist[pkgName] = occurrences
                        }
                    }
                }
                return removed
            }
            mCachedActions.add(CachedAction(3, pkgName, updatable))
            return false
        }
    }

    fun addOrUpdateInstallingLaunchPoint(launchPoint: LaunchPoint?) {
        if (launchPoint != null) {
            synchronized(mCachedActions) {
                if (mIsReady) {
                    val pkgName = launchPoint.packageName
                    val launchPoints = ArrayList<LaunchPoint>()
                    synchronized(mLock) {
                        getLaunchPointsByPackage(
                            mInstallingLaunchPoints,
                            launchPoints,
                            pkgName,
                            true
                        )
                        getLaunchPointsByPackage(mAllLaunchPoints, launchPoints, pkgName, true)
                        for (i in launchPoints.indices) {
                            launchPoints[i].setInstallationState(launchPoint)
                        }
                        if (launchPoints.isEmpty()) {
                            launchPoints.add(launchPoint)
                        }
                        mInstallingLaunchPoints.addAll(launchPoints)
                        if (!isBlacklisted(pkgName) && mShouldNotify) {
                            for (cl in mListeners) {
                                cl.onLaunchPointsAddedOrUpdated(launchPoints)
                            }
                        }
                    }
                    return
                }
                mCachedActions.add(CachedAction(4, launchPoint))
            }
        }
    }

    fun removeInstallingLaunchPoint(launchPoint: LaunchPoint?, success: Boolean) {
        if (launchPoint != null) {
            synchronized(mCachedActions) {
                if (mIsReady) {
                    if (!success) {
                        addOrUpdatePackage(launchPoint.packageName)
                    }
                    return
                }
                mCachedActions.add(CachedAction(5, launchPoint, success))
            }
        }
    }

    private fun getLaunchPointsByPackage(
        parentList: MutableList<LaunchPoint>,
        removeLaunchPoints: MutableList<LaunchPoint>,
        pkgName: String?,
        remove: Boolean
    ): List<LaunchPoint> {
        var rLaunchPoints: MutableList<LaunchPoint>? = removeLaunchPoints
        if (rLaunchPoints == null) {
            rLaunchPoints = ArrayList()
        }
        val itt = parentList.iterator()
        while (itt.hasNext()) {
            val lp = itt.next()
            if (pkgName.equals(lp.packageName)) {
                rLaunchPoints.add(lp)
                if (remove) {
                    itt.remove()
                }
            }
        }
        return rLaunchPoints
    }

    val allLaunchPoints: ArrayList<LaunchPoint>
        get() {
            val allLaunchPoints = arrayListOf<LaunchPoint>()
            if (mAllLaunchPoints.size > 0) {
                for (lp in mAllLaunchPoints) {
                    if (!isBlacklisted(lp.packageName)) {
                        allLaunchPoints.add(lp)
                    }
                }
            }
            return allLaunchPoints
        }

    fun getLaunchPointsByCategory(vararg types: AppCategory): ArrayList<LaunchPoint> {
        val launchPoints = ArrayList<LaunchPoint>()
        synchronized(mLock) {
            for (category in types) {
                getLaunchPointsLocked(mInstallingLaunchPoints, launchPoints, category)
                getLaunchPointsLocked(mAllLaunchPoints, launchPoints, category)
            }
        }
        return launchPoints
    }

    // todo clean up the AppCategory mess
    private fun getLaunchPointsLocked(
        parentList: List<LaunchPoint>,
        childList: MutableList<LaunchPoint>,
        category: AppCategory
    ) {
        when (category) {
            AppCategory.VIDEO -> for (lp in parentList) {
                if (!isFavorited(lp.packageName) && !isBlacklisted(lp.packageName) && lp.appCategory == AppCategory.VIDEO) {
                    childList.add(lp)
                }
            }
            AppCategory.TV -> for (lp in parentList) {
                if (!isFavorited(lp.packageName) && !isBlacklisted(lp.packageName) && lp.appCategory == AppCategory.TV) {
                    childList.add(lp)
                }
            }
            AppCategory.SPORTS -> for (lp in parentList) {
                if (!isFavorited(lp.packageName) && !isBlacklisted(lp.packageName) && lp.isGame) {
                    childList.add(lp)
                }
            }
            AppCategory.OTHER -> for (lp in parentList) {
                if (!isFavorited(lp.packageName) && !isBlacklisted(lp.packageName) && lp.appCategory == AppCategory.OTHER) {
                    childList.add(lp)
                }
            }
            AppCategory.SETTINGS -> childList.addAll(getSettingsLaunchPoints(false))
        }
    }

    fun getSettingsLaunchPoints(force: Boolean): ArrayList<LaunchPoint> {
        if (force || mSettingsLaunchPoints.isEmpty()) {
            mSettingsLaunchPoints = createSettingsList()
        }
//        for (lp in mSettingsLaunchPoints) {
//            Log.d(TAG, "***** dump: ${lp.dump()}")
//        }
        return ArrayList(mSettingsLaunchPoints)
    }

    fun refreshLaunchPointList() {
        Log.i(TAG, "refreshLaunchPointList")
        synchronized(mCachedActions) {
            mIsReady = false
            mShouldNotify = false
        }
        CreateLaunchPointListTask(mExcludeChannelActivities).execute()
        //mainScope.launch { createLaunchPointList(mExcludeChannelActivities) }
    }

    val isReady: Boolean
        get() {
            var z: Boolean
            synchronized(mCachedActions) { z = mIsReady }
            return z
        }

    private fun createLaunchPoints(pkgName: String?): ArrayList<LaunchPoint> {
        var rawItt: Iterator<ResolveInfo>
        val launchPoints = ArrayList<LaunchPoint>()
        val pkgMan = mContext.packageManager
        // tv LaunchPoints
        val tvIntent = Intent(Intent.ACTION_MAIN)
        tvIntent.setPackage(pkgName).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        var rawLaunchPoints = pkgMan.queryIntentActivities(
            tvIntent, PackageManager.GET_META_DATA
        )
        rawItt = rawLaunchPoints.iterator()
        while (rawItt.hasNext()) {
            launchPoints.add(LaunchPoint(mContext, pkgMan, rawItt.next()))
        }
        // normal LaunchPoints
        if (launchPoints.isEmpty()) {
            val mainIntent = Intent(Intent.ACTION_MAIN)
            mainIntent.setPackage(pkgName).addCategory(Intent.CATEGORY_LAUNCHER)
            rawLaunchPoints = pkgMan.queryIntentActivities(
                mainIntent, PackageManager.GET_META_DATA
            )
            rawItt = rawLaunchPoints.iterator()
            while (rawItt.hasNext()) {
                launchPoints.add(LaunchPoint(mContext, pkgMan, rawItt.next()))
            }
        }
        // TODO: leanback settings launchpoints / onSettingsChanged()
//        val settingsIntent = Intent(Intent.ACTION_MAIN)
//        settingsIntent.setPackage(pkgName).addCategory("android.intent.category.LEANBACK_SETTINGS")
//        rawLaunchPoints = pkgMan.queryIntentActivities(settingsIntent, PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA)
//        rawItt = rawLaunchPoints.iterator()
//        while (rawItt.hasNext()) {
//            launchPoints.add(LaunchPoint(mContext, pkgMan, rawItt.next()))
//        }

        return launchPoints
    }

    private val channelActivities: Set<ComponentName>
        @RequiresApi(Build.VERSION_CODES.N)
        get() {
            val channelActivities = HashSet<ComponentName>()
            for (info in mContext.packageManager.queryIntentActivities(
                Intent(
                    "android.intent.action.VIEW",
                    TvContract.buildChannelUri(0)
                ), PackageManager.MATCH_DISABLED_COMPONENTS
            )) {
                if (info.activityInfo != null) {
                    channelActivities.add(
                        ComponentName(
                            info.activityInfo.packageName,
                            info.activityInfo.name
                        )
                    )
                }
            }
            return channelActivities
        }

    private fun createSettingsList(): ArrayList<LaunchPoint> {
        var lp: LaunchPoint
        // LEANBACK_SETTINGS
        val mainIntent =
            Intent("android.intent.action.MAIN").addCategory("android.intent.category.LEANBACK_SETTINGS")
        val settingsItems = ArrayList<LaunchPoint>()
        val pkgMan = mContext.packageManager
        val rawLaunchPoints = pkgMan.queryIntentActivities(
            mainIntent, PackageManager.GET_META_DATA
        )
        val specialEntries = HashMap<ComponentName?, Int>()
        var component = searchComponentForAction("android.settings.WIFI_SETTINGS")
        specialEntries[component] = SettingsType.WIFI.code
        if (Util.isPackageEnabled(mContext, "com.android.tv.settings")) {
            component =
                ComponentName.unflattenFromString("com.android.tv.settings/.connectivity.NetworkActivity")
            specialEntries[component] = SettingsType.WIFI.code
        }
        for (ptr in rawLaunchPoints.indices) {
            val info = rawLaunchPoints[ptr]
            val comp = getComponentName(info)
            var type = -1
            if (specialEntries.containsKey(comp)) {
                type = specialEntries[comp]!! // WI-FI
            }
            if (info.activityInfo != null) {
                lp = LaunchPoint(mContext, pkgMan, info, false, type)
                lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                // lp.setPriority(0);
                settingsItems.add(lp)
            }
        }
//        // LAUNCHER SETTINGS // 4
//        if (mContext.resources.getBoolean(R.bool.full_screen_settings_enabled)) {
//            val setIntent = Intent()
//            setIntent.action = "com.amazon.tv.leanbacklauncher.SETTINGS"
//            setIntent.component =
//                ComponentName.unflattenFromString(mContext.packageName + "/.settings.LegacyHomeScreenSettingsActivity")
//            lp = LaunchPoint(
//                mContext,
//                mContext.getString(R.string.launcher_settings),
//                ContextCompat.getDrawable(mContext, R.drawable.ic_settings_launcher),
//                setIntent,
//                ContextCompat.getColor(mContext, R.color.settings_dialog_bg_protection)
//            )
//            lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
//            lp.settingsType = SettingsType.APP_CONFIGURE.code
//            lp.priority = -4
//            settingsItems.add(lp)
//        }
//        if (mContext.resources.getBoolean(R.bool.side_panel_settings_enabled)) {
//            val ssIntent = Intent()
//            ssIntent.component =
//                ComponentName.unflattenFromString(mContext.packageName + "/.settings.SettingsActivity")
//            val info = pkgMan.resolveActivity(
//                ssIntent,
//                PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
//            )
//            if (info?.activityInfo != null) {
//                lp = LaunchPoint(mContext, pkgMan, info, false, SettingsType.APP_CONFIGURE.code)
//                lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
//                lp.priority = -4
//                settingsItems.add(lp)
//            }
//        }

        // NOTIFICATIONS // 3
        if (FireTVUtils.isAmazonNotificationsEnabled(mContext)) {
            lp = LaunchPoint(
                mContext,
                mContext.getString(R.string.notifications),
                ContextCompat.getDrawable(mContext, R.drawable.ic_settings_notification),
                FireTVUtils.notificationCenterIntent,
                Color.BLACK
            )
            lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            lp.settingsType = SettingsType.NOTIFICATIONS.code
            lp.priority = -3
            settingsItems.add(lp)
        }
        // SYS SETTINGS // 1
        if (FireTVUtils.isAmazonLauncherEnabled(mContext) and !Util.isPackageEnabled(
                mContext,
                "com.android.tv.settings"
            )
        ) {
            lp = LaunchPoint(
                mContext,
                mContext.getString(R.string.system_settings),
                ContextCompat.getDrawable(mContext, R.drawable.ic_settings_settings),
                FireTVUtils.systemSettingsIntent,
                Color.BLACK
            )
            lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            lp.settingsType = SettingsType.UNKNOWN.code
            lp.priority = -1
            settingsItems.add(lp)
        }
        // NETWORK (AMAZON) // 2
        if (Util.isPackageEnabled(mContext, "com.amazon.tv.settings.v2") and !Util.isPackageEnabled(
                mContext,
                "com.android.tv.settings"
            )
        ) {
            val wintent = Intent()
            wintent.component =
                ComponentName.unflattenFromString("com.amazon.tv.settings.v2/.tv.network.NetworkActivity")
            lp = LaunchPoint(
                mContext,
                mContext.getString(R.string.settings_network),
                ContextCompat.getDrawable(mContext, R.drawable.network_state_disconnected),
                wintent,
                Color.BLACK
            )
            lp.addLaunchIntentFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            lp.settingsType = SettingsType.WIFI.code
            lp.priority = -2
            settingsItems.add(lp)
        }
        return settingsItems
    }

    private fun packageHasSettingsEntry(packageName: String?): Boolean {
        mSettingsLaunchPoints.let { slp ->
            for (i in slp.indices) {
                if (slp[i].packageName.equals(packageName)) {
                    return true
                }
            }
        }
        val mainIntent = Intent("android.intent.action.MAIN")
        mainIntent.addCategory("android.intent.category.PREFERENCE")
        val rawLaunchPoints = mContext.packageManager.queryIntentActivities(
            mainIntent, PackageManager.GET_META_DATA
        )
        for (ptr in 0 until rawLaunchPoints.size) {
            val info = rawLaunchPoints[ptr]
            if (info.activityInfo != null) {
                // boolean system = (info.activityInfo.applicationInfo.flags & 1) != 0
                // Why discriminate against user-space settings app?
                if ( /* system && */
                    info.activityInfo.applicationInfo.packageName.equals(packageName)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun getComponentName(info: ResolveInfo?): ComponentName? {
        return if (info == null) {
            null
        } else ComponentName(info.activityInfo.applicationInfo.packageName, info.activityInfo.name)
    }

    private fun searchComponentForAction(action: String): ComponentName? {
        val aIntent = Intent(action)
        val launchPoints = mContext.packageManager.queryIntentActivities(
            aIntent,
            PackageManager.GET_META_DATA
        )
        if (launchPoints.size > 0) {
            val size = launchPoints.size
            for (ptr in 0 until size) {
                val info = launchPoints[ptr]
                // todo fix this
                // if (info.activityInfo != null && info.activityInfo.packageName.contains("com.amazon.tv.leanbacklauncher")) {
                if (info.activityInfo != null) {
                    return getComponentName(info)
                }
            }
        }
        return null
    }

    // TODO relocate this hiding code
    private fun isFavorited(pkgName: String?): Boolean { // Filter favorities only when row enabled
        return prefUtil!!.isFavorite(pkgName) && prefUtil!!.areFavoritesEnabled()
    }

    private fun isBlacklisted(pkgName: String?): Boolean {
        return prefUtil!!.isHidden(pkgName) || mUpdatableBlacklist.containsKey(pkgName) || mNonUpdatableBlacklist.containsKey(
            pkgName
        )
    }

}