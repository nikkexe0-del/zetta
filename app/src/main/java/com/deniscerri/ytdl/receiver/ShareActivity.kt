package com.deniscerri.ytdl.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.CookieViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.BaseActivity
import com.deniscerri.ytdl.util.Extensions.extractURL
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.ThemeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class ShareActivity : BaseActivity() {

    lateinit var context: Context
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var cookieViewModel: CookieViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var navController: NavController
    private var quickDownload by Delegates.notNull<Boolean>()
    private var silentMode = false

    private lateinit var wm: WindowManager
    private lateinit var myView: View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.updateTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            v.setPadding(0, 0, 0, 0)
            insets
        }

        context = baseContext
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // zetta: a shared link should never open the app. Queue it and let the notification
        // shade carry the progress. Everything below is skipped so no window is ever inflated.
        if (shouldHandleSilently(intent)) {
            silentMode = true
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
            downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
            cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
            cookieViewModel.updateCookiesFile()
            askPermissions()
            downloadSilently(intent)
            return
        }

        if (Settings.canDrawOverlays(this)){
            val params = WindowManager.LayoutParams(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                PixelFormat.TRANSLUCENT
            )
            wm = getSystemService(WINDOW_SERVICE) as WindowManager

            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            myView = inflater.inflate(R.layout.activity_share, null)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            wm.addView(myView, params)

            // window.addFlags(
            //     WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            //             or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            //             or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // )
            //
            // val params = window.attributes
            // params.alpha = 0f
            // window.attributes = params
            setContentView(R.layout.activity_share)

        }else{
            window.run {
                setBackgroundDrawable(ColorDrawable(0))
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                } else {
                    setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }

            setContentView(R.layout.activity_share)
        }

        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        cookieViewModel = ViewModelProvider(this)[CookieViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(this)[DownloadCardViewModel::class.java]

        cookieViewModel.updateCookiesFile()
        val intent = intent
        handleIntents(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (silentMode || shouldHandleSilently(intent)) {
            silentMode = true
            downloadSilently(intent)
            return
        }
        handleIntents(intent)
    }

    /**
     * True when this intent is a plain shared/opened link and the user has left the
     * "silent share download" preference on (the default).
     */
    private fun shouldHandleSilently(intent: Intent) : Boolean {
        if (!sharedPreferences.getBoolean("silent_share_download", true)) return false
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return false
        // an explicit type/background override from a shortcut should follow its own path
        if (intent.hasExtra("TYPE")) return false
        val raw = when (action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString
        } ?: return false
        return raw.extractURL().isNotBlank()
    }

    /**
     * Queues the shared link straight into the existing download pipeline. No UI, no bottom
     * sheet, no toast. A notification is posted immediately so there is no dead air before
     * the download worker takes over.
     */
    private fun downloadSilently(intent: Intent) {
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString
        }
        if (raw == null) {
            finishAndRemoveTask()
            return
        }
        val inputQuery = raw.extractURL()

        // Temporary notification id for the resolving phase, before a download row (and therefore a
        // real id) exists. Cancelled as soon as the queued notification takes over.
        val resolvingId = RESOLVING_NOTIFICATION_BASE + (inputQuery.hashCode() and 0xFFFF)
        val notificationUtil = NotificationUtil(applicationContext)
        var resolvingTicker: Job? = null

        lifecycleScope.launch {
            runCatching {
                val existingResults = withContext(Dispatchers.IO) {
                    resultViewModel.getAllByURL(inputQuery)
                }

                var result = existingResults.firstOrNull()

                if (result == null || looksUnresolved(result.title, inputQuery)) {
                    // Animate while yt-dlp resolves the link, so the shade never shows a bare URL.
                    resolvingTicker = launch {
                        var step = 0
                        while (isActive) {
                            notificationUtil.createSharedLinkQueuedNotification(
                                resolvingId, null, inputQuery, step
                            )
                            step++
                            delay(WARMUP_TICK_MS)
                        }
                    }

                    // Bounded: if metadata is slow or fails we still queue the download rather
                    // than leaving the user with nothing.
                    var fetched: ResultItem? = null
                    withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                        runCatching {
                            resultViewModel.parseQueries(listOf(inputQuery)) { list ->
                                fetched = list.firstOrNull()
                            }
                        }
                    }
                    if (fetched != null) result = fetched

                    resolvingTicker?.cancel()
                    resolvingTicker = null
                    notificationUtil.cancelDownloadNotification(resolvingId)
                }

                val resolved = result ?: downloadViewModel.createEmptyResultItem(inputQuery)

                val downloadType = DownloadType.valueOf(
                    downloadViewModel.getDownloadType(url = resolved.url).toString()
                )

                withContext(Dispatchers.IO) {
                    val downloadItem = downloadViewModel.createDownloadItemFromResult(
                        result = resolved,
                        givenType = downloadType
                    )
                    downloadViewModel.queueDownloads(listOf(downloadItem))

                    runCatching {
                        startWarmupTicker(
                            applicationContext,
                            downloadItem.id,
                            downloadItem.title,
                            inputQuery
                        )
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
            resolvingTicker?.cancel()
            runCatching { notificationUtil.cancelDownloadNotification(resolvingId) }
            finishAndRemoveTask()
        }
    }

    /** A result whose title is still just the link hasn't been resolved yet. */
    private fun looksUnresolved(title: String?, url: String) : Boolean =
        title.isNullOrBlank() || title == url ||
                title.startsWith("http://") || title.startsWith("https://")


    /**
     * Keeps the notification alive and animated between "queued" and the download worker posting
     * real progress. Runs on a scope tied to the app rather than this activity, because the
     * activity finishes immediately in silent mode.
     *
     * Stops as soon as the item leaves the queued state so it never fights the worker's own
     * progress updates, and is hard-capped so it can't tick forever if something goes wrong.
     */
    private fun startWarmupTicker(context: Context, itemId: Long, initialTitle: String, url: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val notificationUtil = NotificationUtil(context)
                val dao = DBManager.getInstance(context).downloadDao
                var step = 0
                while (step < MAX_WARMUP_TICKS) {
                    val item = runCatching { dao.getDownloadById(itemId) }.getOrNull() ?: break
                    if (item.status != DownloadRepository.Status.Queued.toString() &&
                        item.status != DownloadRepository.Status.Scheduled.toString()) break

                    notificationUtil.createSharedLinkQueuedNotification(
                        itemId.toInt(),
                        item.title.ifBlank { initialTitle },
                        url,
                        step
                    )
                    step++
                    delay(WARMUP_TICK_MS)
                }
            }
        }
    }

    private fun handleIntents(intent: Intent) {
        askPermissions()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        navController = navHostFragment.findNavController()
        navController.addOnDestinationChangedListener(object: NavController.OnDestinationChangedListener{
            @SuppressLint("RestrictedApi")
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                navController.removeOnDestinationChangedListener(this)
                CoroutineScope(SupervisorJob()).launch {
                    navController.currentBackStack.collectLatest {
                        if (it.isEmpty()){
                            this@ShareActivity.finish()
                        }
                    }
                }
            }
        })

        val action = intent.action
        Log.e("aa", intent.toString())
        if (Intent.ACTION_SEND == action || Intent.ACTION_VIEW == action) {
            if (intent.getStringExtra(Intent.EXTRA_TEXT) == null && Intent.ACTION_SEND == action){
                intent.setClass(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
                return
            }

            runCatching { supportFragmentManager.popBackStack() }

            quickDownload = intent.getBooleanExtra("quick_download", sharedPreferences.getBoolean("quick_download", false) || sharedPreferences.getString("preferred_download_type", "video") == "command")
            val data = when(action){
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)!!
                else -> intent.dataString!!
            }

            val inputQuery = data.extractURL()
            val ai = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)

            val type = intent.getStringExtra("TYPE")
            val background = intent.getBooleanExtra("BACKGROUND", ai.metaData?.getBoolean("quick_run_background", false) == true)

            lifecycleScope.launch {
                val result: ResultItem
                val existingResults = withContext(Dispatchers.IO){
                    resultViewModel.getAllByURL(inputQuery)
                }

                if (existingResults.isEmpty() || existingResults.size > 1) {
                    resultViewModel.deleteAll()
                    result = downloadViewModel.createEmptyResultItem(inputQuery)
                }else{
                    result = existingResults.first()
                }

                val downloadType = DownloadType.valueOf(type ?: downloadViewModel.getDownloadType(url = result.url).toString())
                if (sharedPreferences.getBoolean("download_card", true) && !background){

                    downloadCardViewModel.setResultItem(result)
                    downloadCardViewModel.setDownloadItem(null)
                    val bundle = Bundle()
                    bundle.putSerializable("type", downloadType)
                    navController.setGraph(R.navigation.share_nav_graph, bundle)
                }else{
                    Toast.makeText(this@ShareActivity, "${getString(R.string.downloading)} $inputQuery", Toast.LENGTH_SHORT).show()

                    lifecycleScope.launch(Dispatchers.IO){
                        val downloadItem = downloadViewModel.createDownloadItemFromResult(
                            result = result,
                            givenType = downloadType)

                        downloadViewModel.queueDownloads(listOf(downloadItem))
                    }
                    this@ShareActivity.finish()
                }
            }
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!silentMode) startActivity(Intent(this, MainActivity::class.java))
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wm.isInitialized && ::myView.isInitialized) {
            try {
                wm.removeView(myView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (silentMode) return
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }

    companion object {
        private const val WARMUP_TICK_MS = 750L
        private const val MAX_WARMUP_TICKS = 40
        private const val METADATA_TIMEOUT_MS = 25_000L
        private const val RESOLVING_NOTIFICATION_BASE = 900_000
    }
}
