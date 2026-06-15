package com.zhirang.zhanghaoguanjia.view.home

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.BuildConfig
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.Shop
import com.zhirang.zhanghaoguanjia.bean.dto.AnnouncementDto
import com.zhirang.zhanghaoguanjia.bean.dto.PlatformItemDto
import com.zhirang.zhanghaoguanjia.data.BaseRepository
import com.zhirang.zhanghaoguanjia.data.LoginStateBackupStore
import com.zhirang.zhanghaoguanjia.data.SystemParameterRepository
import com.zhirang.zhanghaoguanjia.data.TokenManager
import com.zhirang.zhanghaoguanjia.data.WechatShareTargetStore
import com.zhirang.zhanghaoguanjia.databinding.ActivityHomeBinding
import com.zhirang.zhanghaoguanjia.engine.EnginePermissionCenter
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import com.zhirang.zhanghaoguanjia.engine.EngineUpgradeManager
import com.zhirang.zhanghaoguanjia.engine.EngineVersionChecker
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.util.AvatarImageLoader
import com.zhirang.zhanghaoguanjia.util.inflate
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry
import com.zhirang.zhanghaoguanjia.util.toast
import com.zhirang.zhanghaoguanjia.view.dialog.AdvancedFeatureSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.DeleteShopSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.EditShopSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.EngineUpgradeDialog
import com.zhirang.zhanghaoguanjia.view.dialog.ShopAuthorizationSheetFragment
import com.zhirang.zhanghaoguanjia.view.logs.LogsActivity
import com.zhirang.zhanghaoguanjia.view.login.LoginActivity
import com.zhirang.zhanghaoguanjia.view.profile.ProfileActivity
import com.zhirang.zhanghaoguanjia.view.splash.EngineInstallActivity
import com.zhirang.zhanghaoguanjia.update.AppUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

class HomeActivity : AppCompatActivity() {

    private data class PreparedShopEnvironment(
        val shop: Shop,
        val packageName: String,
        val userId: Int,
        val platformName: String
    )

    private enum class LoginStateRestoreResult {
        RESTORED,
        NOT_AVAILABLE,
        FAILED
    }

    private val viewBinding: ActivityHomeBinding by inflate()
    private lateinit var viewModel: HomeViewModel
    private lateinit var platformAdapter: PlatformSidebarAdapter
    private lateinit var shopAdapter: ShopListAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val pendingRecognitionKeys = mutableSetOf<String>()
    private val preparingShopKeys = mutableSetOf<String>()
    private val preparedShopKeys = mutableSetOf<String>()
    private val preparedShopUsers = mutableMapOf<String, Int>()
    private val preparingShopCallbacks = mutableMapOf<String, MutableList<(PreparedShopEnvironment?) -> Unit>>()
    private var shouldSyncCloneShopsOnNextList = false
    private var pendingEngineAction: (() -> Unit)? = null
    private var shownAnnouncementId: Long? = null
    private var engineUpgradeCheckInFlight = false
    private var cloneDataMigrationInFlight = false
    private var authReceiverRegistered = false
    private var shopProgressDialog: AlertDialog? = null
    private var shopProgressMessageView: TextView? = null
    private var shopProgressBar: ProgressBar? = null
    private var shopOperationInProgress = false
    private var restoringShopEnvironments = false
    private var pendingPlatformEnvironmentPackage: String? = null
    private var waitingForEngineConnectionToRestore = false
    private var restoreEnvironmentCheckPackage: String? = null
    private var shouldPrepareDefaultPlatformEnvironment = true
    private var defaultPlatformEnvironmentPreparedPackage: String? = null
    private val preparedPlatformEnvironmentPackages = mutableSetOf<String>()
    private var pendingScrollToCloneInstanceId: String? = null
    private val locallyRepairedShopKeys = mutableSetOf<String>()
    private val restoredLoginStateKeys = mutableSetOf<String>()
    private val uploadedLoginStateKeys = mutableSetOf<String>()
    private var suppressNextShopClickAfterSwipeCollapse = false
    private var tickerShouldScroll = false
    private var tickerPausedByTouch = false
    private var tickerScrollStartTime = 0L
    private var tickerScrollDistance = 0f
    private var currentTickerAnnouncement: AnnouncementDto? = null
    private var registrationGiftPromptLoading = false
    private val tickerScrollRunnable = object : Runnable {
        override fun run() {
            if (!tickerShouldScroll || tickerPausedByTouch || isTouchExplorationEnabled()) {
                return
            }
            val elapsed = System.currentTimeMillis() - tickerScrollStartTime
            val cycleMs = tickerCycleMs()
            val progress = (elapsed % cycleMs).toFloat() / cycleMs
            viewBinding.tvTickerText.translationX = -tickerScrollDistance * progress
            handler.postDelayed(this, TICKER_FRAME_DELAY_MS)
        }
    }
    private var pendingEnginePermissionShop: Shop? = null
    private var pendingEnginePermissionFreshToken: String? = null
    private var pendingEnginePermissionPackage: String? = null
    private var pendingEnginePermissionBaseline = false
    private lateinit var enginePermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var cloneDataMigrationLauncher: ActivityResultLauncher<Intent>
    private lateinit var shopSwipeHelper: ShopSwipeHelper
    private lateinit var shopItemTouchHelper: ItemTouchHelper
    private var pendingShopOrderSubmit = false
    private val authExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BaseRepository.ACTION_AUTH_EXPIRED) {
                redirectToLogin()
            }
        }
    }

    companion object {
        private const val TAG = "HomeActivity"
        private const val SHOP_RECOGNITION_AFTER_CLICK_DELAY_MS = 10_000L
        private const val PREF_SUBSCRIPTION_GIFT_PROMPT = "subscription_gift_prompt"
        private const val KEY_PENDING_GIFT_PHONE = "pending_gift_phone"
        private const val KEY_PENDING_GIFT_USER_ID = "pending_gift_user_id"
        private const val KEY_SHOWN_GIFT_USER_PREFIX = "shown_gift_user_"
        private const val TICKER_SCROLL_SPEED_PX_PER_SECOND = 28f
        private const val TICKER_MIN_CYCLE_MS = 8_000L
        private const val TICKER_FRAME_DELAY_MS = 16L
        private const val DEFAULT_REGISTER_TRIAL_SUBSCRIPTION_DAYS = 30
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_SHARE_ACTIVITY = "com.tencent.mm.ui.tools.ShareImgUI"
        private const val WECHAT_SEND_WRAPPER_ACTIVITY = "com.tencent.mm.ui.transmit.SendAppMessageWrapperUI"
        private var sessionAnnouncementsRequested = false
        private val sessionShownAnnouncementIds = mutableSetOf<Long>()

        fun start(context: Context) {
            val intent = Intent(context, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        setContentView(viewBinding.root)

        initEnginePermissionLauncher()
        initCloneDataMigrationLauncher()
        initViewModel()
        initPlatformSidebar()
        initShopList()
        initSearch()
        initTickerBanner()
        initClickListeners()
        observeData()
        App.ensureEngineConnection()
        maybeRunCloneDataMigration()
        maybeRequestBaselineEnginePermissions()
    }

    override fun onStart() {
        super.onStart()
        registerAuthReceiver()
    }

    override fun onResume() {
        super.onResume()
        if (!TokenManager.getInstance().isLoggedIn()) {
            redirectToLogin()
            return
        }
        App.ensureEngineConnection()
        maybeRunCloneDataMigration()
        viewModel.refreshUserInfo()
        viewModel.refreshUserInfoFromServer()
        viewModel.loadShops()
        checkForEngineUpgrade()
        maybeRequestBaselineEnginePermissions()
        startTickerScrollIfNeeded(restart = false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (
            ev.actionMasked == MotionEvent.ACTION_DOWN &&
            ::shopSwipeHelper.isInitialized &&
            shopAdapter.getExpandedShopId() != null
        ) {
            val rv = viewBinding.rvShops
            val rvLocation = IntArray(2)
            rv.getLocationOnScreen(rvLocation)
            val localX = ev.rawX - rvLocation[0]
            val localY = ev.rawY - rvLocation[1]
            if (shopSwipeHelper.hitTestExpandedRepair(rv, localX, localY)) {
                val shop = shopAdapter.getExpandedShop()
                if (shop != null) {
                    shopSwipeHelper.collapseExpandedItem(rv)
                    confirmRepairShop(shop)
                    return true
                }
            }
            if (shopSwipeHelper.hitTestExpandedItem(rv, localX, localY)) {
                shopSwipeHelper.collapseExpandedItem(rv)
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPause() {
        collapseShopRepairSwipe()
        stopTickerScroll()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (authReceiverRegistered) {
            unregisterReceiver(authExpiredReceiver)
            authReceiverRegistered = false
        }
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private fun initEnginePermissionLauncher() {
        enginePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val pendingShop = pendingEnginePermissionShop
            val freshToken = pendingEnginePermissionFreshToken
            val pendingPackage = pendingEnginePermissionPackage
            val wasBaselineRequest = pendingEnginePermissionBaseline
            pendingEnginePermissionShop = null
            pendingEnginePermissionFreshToken = null
            pendingEnginePermissionPackage = null
            pendingEnginePermissionBaseline = false
            if (wasBaselineRequest) {
                EnginePermissionCenter.markBaselinePrompted(this)
                return@registerForActivityResult
            }
            if (pendingShop == null) {
                return@registerForActivityResult
            }
            val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, pendingPackage)
            if (result.resultCode != RESULT_OK ||
                !EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)
            ) {
                finishShopOperation()
                toast("未授权引擎使用必要权限，无法打开店铺")
                return@registerForActivityResult
            }
            openPlatformForShopAfterEnginePermission(pendingShop, freshToken)
        }
    }

    private fun initCloneDataMigrationLauncher() {
        cloneDataMigrationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            finalizeCloneDataMigration()
        }
    }

    private fun maybeRequestBaselineEnginePermissions() {
        handler.post {
            if (!TokenManager.getInstance().isLoggedIn()) {
                return@post
            }
            if (pendingEnginePermissionBaseline || pendingEnginePermissionShop != null) {
                return@post
            }
            if (!EnginePermissionCenter.shouldPromptBaseline(this)) {
                return@post
            }
            pendingEnginePermissionBaseline = true
            try {
                enginePermissionLauncher.launch(EnginePermissionCenter.buildBaselineIntent(this))
            } catch (e: Exception) {
                pendingEnginePermissionBaseline = false
                Log.w(TAG, "Failed to launch baseline engine permission activity", e)
            }
        }
    }

    private fun maybeRunCloneDataMigration() {
        if (cloneDataMigrationInFlight || !TokenManager.getInstance().isLoggedIn()) {
            return
        }
        if (!EngineProxy.isConnected()) {
            EngineProxy.addServiceAvailableCallback {
                runOnUiThread { maybeRunCloneDataMigration() }
            }
            return
        }
        val version = EngineInstaller.getInstalledEngineVersion(this)
        val prefs = getSharedPreferences("clone_data_migration", Context.MODE_PRIVATE)
        val doneVersion = prefs.getInt("scoped_done_engine_version", -1)
        if (version > 0 && doneVersion >= version) {
            return
        }
        cloneDataMigrationInFlight = true
        if (!beginShopOperation("迁移分身数据", "正在迁移分身数据，请稍候…")) {
            cloneDataMigrationInFlight = false
            return
        }
        if (launchEngineCloneDataMigrationActivity()) {
            return
        }
        finalizeCloneDataMigration()
    }

    private fun launchEngineCloneDataMigrationActivity(): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    EngineInstaller.ENGINE_PACKAGE,
                    "top.niunaijun.blackbox.engine.EngineCloneDataMigrationActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            cloneDataMigrationLauncher.launch(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch engine clone data migration activity; falling back to binder migration", e)
            false
        }
    }

    private fun finalizeCloneDataMigration() {
        if (!EngineProxy.isConnected()) {
            EngineProxy.addServiceAvailableCallback {
                runOnUiThread { finalizeCloneDataMigration() }
            }
            return
        }
        val version = EngineInstaller.getInstalledEngineVersion(this)
        val prefs = getSharedPreferences("clone_data_migration", Context.MODE_PRIVATE)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = EngineProxy.migrateCloneDataToScopedStorage()
            withContext(Dispatchers.Main) {
                cloneDataMigrationInFlight = false
                if (result != null) {
                    prefs.edit().putInt("scoped_done_engine_version", version).apply()
                    finishShopOperation()
                } else {
                    finishShopOperation()
                    toast("分身数据迁移失败，请重启后重试")
                }
            }
        }
    }

    private fun initPlatformSidebar() {
        platformAdapter = PlatformSidebarAdapter { _, platform ->
            if (!platform.available) {
                toast("该平台的店铺管理功能暂不支持")
                return@PlatformSidebarAdapter
            }
            viewModel.selectPlatform(platform.platform)
            updateShopList()
            prepareShopEnvironmentsForPlatform(platform.packageName)
        }

        viewBinding.rvPlatforms.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = platformAdapter
        }
    }

    private fun initShopList() {
        shopAdapter = ShopListAdapter(
            onItemClick = { _, shop ->
                onShopClick(shop)
            },
            onEditClick = { _, shop ->
                showEditShopSheet(shop)
            },
            onWechatClick = { _, shop ->
                openWechatShareForShop(shop)
            },
            onQuickShareClick = { _, shop ->
                quickShareToBoundWechat(shop)
            },
            onShopAuthorizationClick = { _, shop ->
                showShopAuthorizationSheet(shop)
            },
            onAdvancedFeatureClick = { _, shop, featureType ->
                handleAdvancedFeatureClick(shop, featureType)
            },
            onAutoRenewClick = { _, shop ->
                handleAutoRenewClick(shop)
            },
            onDeleteClick = { _, shop ->
                showDeleteShopSheet(shop)
            },
            onRepairClick = { _, shop ->
                collapseShopRepairSwipe()
                confirmRepairShop(shop)
            }
        )

        viewBinding.rvShops.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = shopAdapter
        }
        shopAdapter.setOnLongPressDragStart { holder ->
            val position = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: holder.absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                ?: return@setOnLongPressDragStart
            val shopId = shopAdapter.getShopIdAt(position) ?: return@setOnLongPressDragStart
            shopSwipeHelper.collapseExpandedItem(viewBinding.rvShops)
            shopAdapter.setReorderMode(true, shopId, refreshItems = false)
            shopItemTouchHelper.startDrag(holder)
            shopAdapter.applyReorderVisualState(holder)
            viewBinding.rvShops.post {
                shopAdapter.refreshReorderVisualState(excludeShopId = shopId)
            }
        }
        viewBinding.swipeRefreshShops.setOnRefreshListener {
            exitShopReorderMode(submit = false)
            shouldSyncCloneShopsOnNextList = true
            viewModel.loadShops()
        }
        attachShopRepairSwipe()
    }

    private fun attachShopRepairSwipe() {
        shopSwipeHelper = ShopSwipeHelper()
        shopSwipeHelper.bindState(
            shopIdProvider = { position -> shopAdapter.getShopIdAt(position) },
            expandedShopIdProvider = { shopAdapter.getExpandedShopId() },
            onExpandedShopChanged = { shopId -> shopAdapter.setExpandedShopId(shopId) },
            reorderEnabledProvider = { shopAdapter.isReorderMode() },
            onMoveItem = { from, to -> shopAdapter.moveItem(from, to) },
            onDragStarted = { position ->
                shopAdapter.setDraggingShopId(shopAdapter.getShopIdAt(position))
            },
            onDragFinished = {
                submitShopOrderAfterDrag()
            }
        )
        shopItemTouchHelper = ItemTouchHelper(shopSwipeHelper)
        shopItemTouchHelper.attachToRecyclerView(viewBinding.rvShops)
        viewBinding.rvShops.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    shopSwipeHelper.collapseExpandedItem(recyclerView)
                }
            }
        })
        viewBinding.rvShops.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (shopAdapter.getExpandedShopId() == null) {
                    return false
                }
                if (e.actionMasked != MotionEvent.ACTION_DOWN) {
                    return false
                }
                val child = rv.findChildViewUnder(e.x, e.y)
                val hitRepair = shopSwipeHelper.hitTestExpandedRepair(rv, e.x, e.y) ||
                        (child != null && shopSwipeHelper.hitTestRepair(rv, child, e.x, e.y))
                if (hitRepair) {
                    val shop = shopAdapter.getExpandedShop()
                    if (shop != null) {
                        shopSwipeHelper.collapseExpandedItem(rv)
                        confirmRepairShop(shop)
                        return true
                    }
                    return false
                }
                val hitExpandedItem = shopSwipeHelper.hitTestExpandedItem(rv, e.x, e.y)
                shopSwipeHelper.collapseExpandedItem(rv)
                if (hitExpandedItem) {
                    suppressNextShopClickAfterSwipeCollapse = true
                    return true
                }
                return false
            }
        })
    }

    private fun registerAuthReceiver() {
        if (authReceiverRegistered) {
            return
        }
        val filter = IntentFilter(BaseRepository.ACTION_AUTH_EXPIRED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(authExpiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(authExpiredReceiver, filter)
        }
        authReceiverRegistered = true
    }

    private fun redirectToLogin() {
        if (isFinishing || isDestroyed) {
            return
        }
        LoginActivity.startClearingTask(this)
        finish()
    }

    private fun initSearch() {
        viewBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString() ?: "")
                updateShopList()
            }
        })
    }

    private fun initTickerBanner() {
        viewBinding.tickerBanner.setOnClickListener {
            showTickerAnnouncementDialog()
        }
        viewBinding.tickerBanner.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tickerPausedByTouch = true
                    stopTickerScroll()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    tickerPausedByTouch = false
                    startTickerScrollIfNeeded(restart = false)
                }
            }
            false
        }
    }

    private fun initClickListeners() {
        // 个人中心入口（左侧栏底部）
        viewBinding.btnMyProfile.setOnClickListener {
            ProfileActivity.start(this)
        }

        viewBinding.btnAddShop.setOnClickListener {
            addShopForSelectedPlatform()
        }
    }

    private fun observeData() {
        viewModel.computeBalanceLiveData.observe(this) { balance ->
            viewBinding.tvComputeBalance.text = balance.toString()
        }

        viewModel.phoneMinutesBalanceLiveData.observe(this) { balance ->
            viewBinding.tvPhoneMinutesBalance.text = getString(R.string.phone_minutes_balance_home, balance)
        }

        viewModel.phoneNumberLiveData.observe(this) { phone ->
            viewBinding.tvPhoneNumber.text = phone
        }

        viewModel.subscriptionStatusLiveData.observe(this) { status ->
            viewBinding.tvSubscriptionStatus.text = status
        }

        viewModel.activeSubscriptionLiveData.observe(this) { active ->
            if (::shopAdapter.isInitialized) {
                shopAdapter.setSubscriptionDisplayMode(
                    showRemainingDays = !active,
                    showAutoRenewControls = !active
                )
            }
            updateShopList()
        }

        viewModel.displayUsernameLiveData.observe(this) { username ->
            viewBinding.tvHeaderUsername.text = username
            AvatarImageLoader.bind(
                imageView = viewBinding.ivHeaderAvatar,
                fallbackView = viewBinding.tvHeaderInitial,
                avatarUrl = viewModel.avatarUrlLiveData.value,
                initial = username
            )
        }

        viewModel.avatarUrlLiveData.observe(this) { avatarUrl ->
            AvatarImageLoader.bind(
                imageView = viewBinding.ivHeaderAvatar,
                fallbackView = viewBinding.tvHeaderInitial,
                avatarUrl = avatarUrl,
                initial = viewBinding.tvHeaderUsername.text?.toString().orEmpty()
            )
        }

        viewModel.adminLiveData.observe(this) { isAdmin ->
            viewBinding.tvHeaderAdminTag.visibility = if (isAdmin) View.VISIBLE else View.GONE
        }

        viewModel.shopsLiveData.observe(this) {
            viewBinding.swipeRefreshShops.isRefreshing = false
            updateShopList()
            scrollToPendingNewShopIfNeeded()
            pendingPlatformEnvironmentPackage?.let { packageName ->
                prepareShopEnvironmentsForPlatform(packageName)
            }
            prepareDefaultPlatformEnvironmentIfNeeded()
            if (shouldSyncCloneShopsOnNextList) {
                shouldSyncCloneShopsOnNextList = false
                syncCloneShopStateForCurrentPlatform()
            }
        }

        viewModel.platformsLiveData.observe(this) { platforms ->
            platformAdapter.submitList(platforms)
            updateShopList()
            prepareDefaultPlatformEnvironmentIfNeeded()
        }

        viewModel.selectedPlatformLiveData.observe(this) {
            updateShopList()
            prepareDefaultPlatformEnvironmentIfNeeded()
        }

        // 更新平台店铺数量
        viewModel.platformShopCounts.observe(this) { counts ->
            platformAdapter.setShopCounts(counts)
        }

        viewModel.loadErrorLiveData.observe(this) { errorMessage ->
            viewBinding.swipeRefreshShops.isRefreshing = false
            errorMessage?.let {
                toast(it)
                viewModel.clearLoadError()
            }
        }

        viewModel.operationMessageLiveData.observe(this) { message ->
            message?.let {
                toast(it)
                viewModel.clearOperationMessage()
            }
        }

        viewModel.latestAnnouncementLiveData.observe(this) { announcement ->
            announcement?.let { showAnnouncementDialog(it) }
        }

        viewModel.appReleaseAnnouncementLiveData.observe(this) { announcement ->
            announcement?.let { showAnnouncementDialog(it) }
        }

        viewModel.tickerAnnouncementLiveData.observe(this) { announcement ->
            updateTickerBanner(announcement)
        }

        viewModel.advancedFeaturesLiveData.observe(this) { features ->
            shopAdapter.setAdvancedFeatures(features)
        }

        viewModel.loadShops()
        viewModel.loadAppParameters()
        viewModel.loadAdvancedFeatures()
        showRegistrationGiftPromptIfNeeded()
    }

    private fun prepareDefaultPlatformEnvironmentIfNeeded() {
        if (!shouldPrepareDefaultPlatformEnvironment) {
            return
        }
        val platformItem = viewModel.getSelectedPlatformItem() ?: return
        if (!platformItem.available) {
            return
        }
        val packageName = platformItem.packageName?.takeIf { it.isNotBlank() } ?: return
        val hasShopsForPlatform = viewModel.getAllShops().any { shop ->
            (viewModel.hasActiveSubscription() || shop.remainingDays > 0) &&
                    shop.cloneInstanceId?.isNotBlank() == true &&
                    resolveShopPackageName(shop) == packageName
        }
        if (!hasShopsForPlatform) {
            return
        }
        if (defaultPlatformEnvironmentPreparedPackage == packageName) {
            shouldPrepareDefaultPlatformEnvironment = false
            return
        }
        if (preparedPlatformEnvironmentPackages.contains(packageName)) {
            shouldPrepareDefaultPlatformEnvironment = false
            defaultPlatformEnvironmentPreparedPackage = packageName
            return
        }
        prepareShopEnvironmentsForPlatform(packageName)
    }

    private fun prepareShopEnvironmentsForPlatform(packageName: String?) {
        val normalizedPackageName = packageName?.takeIf { it.isNotBlank() } ?: return
        if (preparedPlatformEnvironmentPackages.contains(normalizedPackageName)) {
            pendingPlatformEnvironmentPackage = null
            if (viewModel.getSelectedPlatformItem()?.packageName == normalizedPackageName) {
                shouldPrepareDefaultPlatformEnvironment = false
                defaultPlatformEnvironmentPreparedPackage = normalizedPackageName
            }
            return
        }
        if (restoringShopEnvironments || shopOperationInProgress) {
            pendingPlatformEnvironmentPackage = normalizedPackageName
            return
        }
        if (!EngineProxy.isConnected()) {
            pendingPlatformEnvironmentPackage = normalizedPackageName
            if (EngineInstaller.isEngineInstalled(this) && !waitingForEngineConnectionToRestore) {
                waitingForEngineConnectionToRestore = true
                EngineProxy.addServiceAvailableCallback {
                    runOnUiThread {
                        waitingForEngineConnectionToRestore = false
                        handler.post { prepareShopEnvironmentsForPlatform(normalizedPackageName) }
                    }
                }
                App.ensureEngineConnection()
            }
            return
        }
        if (restoreEnvironmentCheckPackage == normalizedPackageName) {
            return
        }
        restoreEnvironmentCheckPackage = normalizedPackageName
        lifecycleScope.launch(Dispatchers.IO) {
            val pendingShops = getPendingShopEnvironmentsForRestore(normalizedPackageName)
            withContext(Dispatchers.Main) {
                restoreEnvironmentCheckPackage = null
                if (isFinishing || isDestroyed) {
                    return@withContext
                }
                if (pendingShops.isEmpty()) {
                    pendingPlatformEnvironmentPackage = null
                    preparedPlatformEnvironmentPackages.add(normalizedPackageName)
                    if (viewModel.getSelectedPlatformItem()?.packageName == normalizedPackageName) {
                        shouldPrepareDefaultPlatformEnvironment = false
                        defaultPlatformEnvironmentPreparedPackage = normalizedPackageName
                    }
                    return@withContext
                }
                if (restoringShopEnvironments || shopOperationInProgress) {
                    pendingPlatformEnvironmentPackage = normalizedPackageName
                    return@withContext
                }
                if (!beginShopOperation("恢复店铺", "正在恢复店铺环境 0/${pendingShops.size}...")) {
                    pendingPlatformEnvironmentPackage = normalizedPackageName
                    return@withContext
                }
                pendingPlatformEnvironmentPackage = normalizedPackageName
                restoringShopEnvironments = true
                prepareNextLoadedShopEnvironment(pendingShops, 0)
            }
        }
    }

    private fun getPendingShopEnvironmentsForRestore(packageName: String?): List<Shop> {
        return viewModel.getAllShops().filter { shop ->
            if ((!viewModel.hasActiveSubscription() && shop.remainingDays <= 0) ||
                shop.cloneInstanceId?.isNotBlank() != true
            ) {
                return@filter false
            }
            val shopPackageName = resolveShopPackageName(shop) ?: return@filter false
            if (packageName != null && shopPackageName != packageName) {
                return@filter false
            }
            val prepareKey = buildShopPrepareKey(shop, shopPackageName) ?: return@filter false
            val preparedUserId = preparedShopUsers[prepareKey]
                ?: findPreparedUserIdForShop(shop, shopPackageName)
            !preparingShopKeys.contains(prepareKey) &&
                    !locallyRepairedShopKeys.contains(prepareKey) &&
                    preparedUserId == null
        }
    }

    private fun prepareNextLoadedShopEnvironment(shops: List<Shop>, index: Int) {
        if (!restoringShopEnvironments || isFinishing || isDestroyed) {
            return
        }
        if (index >= shops.size) {
            restoringShopEnvironments = false
            pendingPlatformEnvironmentPackage = null
            shops.firstOrNull()?.let { shop ->
                resolveShopPackageName(shop)?.let { preparedPackageName ->
                    preparedPlatformEnvironmentPackages.add(preparedPackageName)
                    if (viewModel.getSelectedPlatformItem()?.packageName == preparedPackageName) {
                        shouldPrepareDefaultPlatformEnvironment = false
                        defaultPlatformEnvironmentPreparedPackage = preparedPackageName
                    }
                }
            }
            finishShopOperation()
            return
        }
        val shop = shops[index]
        val packageName = resolveShopPackageName(shop)
        val prepareKey = packageName?.let { buildShopPrepareKey(shop, it) }
        if (packageName.isNullOrBlank() ||
            prepareKey == null
        ) {
            handler.post { prepareNextLoadedShopEnvironment(shops, index + 1) }
            return
        }
        val displayName = shopDisplayName(shop)
        updateShopProgress("正在恢复店铺环境 ${index + 1}/${shops.size}：$displayName")
        prepareShopEnvironment(
            shop = shop,
            showProgress = false,
            launchAfterReady = false
        ) {
            handler.post { prepareNextLoadedShopEnvironment(shops, index + 1) }
        }
    }

    private fun loadAnnouncementForOpenOnce() {
        if (sessionAnnouncementsRequested) {
            return
        }
        sessionAnnouncementsRequested = true
        viewModel.loadTickerAnnouncement()
        viewModel.loadLatestAnnouncement()
        viewModel.loadAppReleaseAnnouncementIfNeeded()
    }

    private fun updateTickerBanner(announcement: AnnouncementDto?) {
        val content = announcement?.content?.trim().orEmpty()
        if (content.isBlank()) {
            currentTickerAnnouncement = null
            tickerShouldScroll = false
            stopTickerScroll()
            viewBinding.tvTickerText.text = ""
            viewBinding.tickerBanner.visibility = View.GONE
            return
        }
        currentTickerAnnouncement = announcement
        viewBinding.tvTickerText.text = content
        viewBinding.tvTickerText.contentDescription = "滚动播报：$content"
        viewBinding.tickerBanner.contentDescription = "滚动播报：$content"
        viewBinding.tickerBanner.visibility = View.VISIBLE
        viewBinding.tvTickerText.translationX = 0f
        viewBinding.tvTickerText.post {
            configureTickerScroll()
        }
    }

    private fun configureTickerScroll() {
        val textView = viewBinding.tvTickerText
        val availableWidth = viewBinding.tickerBanner.width -
                viewBinding.tickerBanner.paddingStart -
                viewBinding.tickerBanner.paddingEnd
        if (availableWidth <= 0 || textView.text.isNullOrBlank()) {
            tickerShouldScroll = false
            textView.translationX = 0f
            return
        }
        val textWidth = textView.paint.measureText(textView.text.toString())
        tickerShouldScroll = textWidth > availableWidth
        tickerScrollDistance = (textWidth - availableWidth).coerceAtLeast(0f)
        val targetWidth = if (tickerShouldScroll) textWidth.toInt() + 1 else availableWidth
        if (textView.layoutParams.width != targetWidth) {
            textView.layoutParams = textView.layoutParams.apply {
                width = targetWidth
            }
        }
        textView.translationX = 0f
        startTickerScrollIfNeeded(restart = true)
    }

    private fun startTickerScrollIfNeeded(restart: Boolean) {
        stopTickerScroll()
        if (!tickerShouldScroll || tickerPausedByTouch || isTouchExplorationEnabled()) {
            viewBinding.tvTickerText.translationX = 0f
            return
        }
        if (restart) {
            tickerScrollStartTime = System.currentTimeMillis()
        } else {
            val currentOffset = -viewBinding.tvTickerText.translationX
            tickerScrollStartTime = System.currentTimeMillis() -
                    ((currentOffset / tickerScrollDistance.coerceAtLeast(1f)) * tickerCycleMs()).toLong()
        }
        handler.post(tickerScrollRunnable)
    }

    private fun stopTickerScroll() {
        handler.removeCallbacks(tickerScrollRunnable)
    }

    private fun tickerCycleMs(): Long {
        return ((tickerScrollDistance / TICKER_SCROLL_SPEED_PX_PER_SECOND) * 1000f)
            .toLong()
            .coerceAtLeast(TICKER_MIN_CYCLE_MS)
    }

    private fun isTouchExplorationEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }

    private fun showRegistrationGiftPromptIfNeeded() {
        if (registrationGiftPromptLoading) {
            return
        }
        if (!shouldShowRegistrationGiftPrompt()) {
            loadAnnouncementForOpenOnce()
            return
        }
        lifecycleScope.launch {
            registrationGiftPromptLoading = true
            val parameters = try {
                viewModel.getAppParametersForPrompt()
            } finally {
                registrationGiftPromptLoading = false
            }
            showRegistrationGiftPromptIfNeeded(parameters)
        }
    }

    private fun showRegistrationGiftPromptIfNeeded(parameters: Map<String, String>) {
        if (!shouldShowRegistrationGiftPrompt()) {
            loadAnnouncementForOpenOnce()
            return
        }
        val user = TokenManager.getInstance().getUser() ?: return
        val prefs = getSharedPreferences(PREF_SUBSCRIPTION_GIFT_PROMPT, Context.MODE_PRIVATE)
        val shownKey = KEY_SHOWN_GIFT_USER_PREFIX + user.id
        val trialDays = subscriptionTrialDays(parameters)
        MaterialAlertDialogBuilder(this)
            .setMessage("恭喜您注册成功，系统赠送您${trialDays}天的订阅特权免费体验卡，欢迎使用！")
            .setPositiveButton("知道了") { _, _ ->
                prefs.edit()
                    .putBoolean(shownKey, true)
                    .remove(KEY_PENDING_GIFT_PHONE)
                    .remove(KEY_PENDING_GIFT_USER_ID)
                    .apply()
                loadAnnouncementForOpenOnce()
            }
            .setOnCancelListener {
                prefs.edit()
                    .putBoolean(shownKey, true)
                    .remove(KEY_PENDING_GIFT_PHONE)
                    .remove(KEY_PENDING_GIFT_USER_ID)
                    .apply()
                loadAnnouncementForOpenOnce()
            }
            .show()
    }

    private fun subscriptionTrialDays(parameters: Map<String, String>): Int {
        return parameters[SystemParameterRepository.REGISTER_TRIAL_SUBSCRIPTION_DAYS]
            ?.trim()
            ?.toIntOrNull()
            ?.coerceIn(0, 3650)
            ?: DEFAULT_REGISTER_TRIAL_SUBSCRIPTION_DAYS
    }

    private fun shouldShowRegistrationGiftPrompt(): Boolean {
        val user = TokenManager.getInstance().getUser()
        val prefs = getSharedPreferences(PREF_SUBSCRIPTION_GIFT_PROMPT, Context.MODE_PRIVATE)
        val pendingPhone = prefs.getString(KEY_PENDING_GIFT_PHONE, null)?.takeIf { it.isNotBlank() }
        val pendingUserId = prefs.getLong(KEY_PENDING_GIFT_USER_ID, -1L).takeIf { it > 0L }
        if (user == null) {
            return false
        }
        val phoneMatches = pendingPhone != null && pendingPhone == user.phone
        val userMatches = pendingUserId != null && pendingUserId == user.id
        if (!phoneMatches && !userMatches) {
            return false
        }
        val shownKey = KEY_SHOWN_GIFT_USER_PREFIX + user.id
        if (prefs.getBoolean(shownKey, false) || isFinishing || isDestroyed) {
            prefs.edit()
                .remove(KEY_PENDING_GIFT_PHONE)
                .remove(KEY_PENDING_GIFT_USER_ID)
                .apply()
            return false
        }
        return true
    }

    private fun showAnnouncementDialog(announcement: AnnouncementDto) {
        if (shownAnnouncementId == announcement.id ||
            sessionShownAnnouncementIds.contains(announcement.id) ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }
        shownAnnouncementId = announcement.id
        sessionShownAnnouncementIds.add(announcement.id)
        MaterialAlertDialogBuilder(this)
            .setTitle(announcement.displayTitle())
            .setMessage(announcement.content)
            .setNegativeButton("立即升级") { _, _ ->
                checkMainAppUpdateFromAnnouncement()
            }
            .setPositiveButton(R.string.announcement_dialog_button, null)
            .show()
            .also { dialog ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                    ?.visibility = if (announcement.hasMainAppUpgradeAction()) View.VISIBLE else View.GONE
                dialog.findViewById<android.widget.TextView>(android.R.id.message)?.let { messageView ->
                    Linkify.addLinks(messageView, Linkify.WEB_URLS)
                    messageView.movementMethod = LinkMovementMethod.getInstance()
                }
            }
    }

    private fun showTickerAnnouncementDialog() {
        val announcement = currentTickerAnnouncement ?: return
        if (isFinishing || isDestroyed) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("播报内容")
            .setMessage(announcement.content)
            .setPositiveButton(R.string.announcement_dialog_button, null)
            .show()
            .also { dialog ->
                dialog.findViewById<TextView>(android.R.id.message)?.let { messageView ->
                    Linkify.addLinks(messageView, Linkify.WEB_URLS)
                    messageView.movementMethod = LinkMovementMethod.getInstance()
                }
            }
    }

    private fun AnnouncementDto.hasMainAppUpgradeAction(): Boolean {
        return type.equals("APP_RELEASE", ignoreCase = true)
    }

    private fun AnnouncementDto.displayTitle(): String {
        return if (hasMainAppUpgradeAction()) "新版本发布" else title
    }

    private fun checkMainAppUpdateFromAnnouncement() {
        lifecycleScope.launch {
            val result = AppUpdateManager.checkForUpdate(this@HomeActivity)
            result.fold(
                onSuccess = { version ->
                    if (version == null) {
                        toast("当前已是最新版本")
                        return@fold
                    }
                    val changelog = version.changelog?.takeIf { it.isNotBlank() } ?: "暂无更新说明"
                    MaterialAlertDialogBuilder(this@HomeActivity)
                        .setTitle("发现新版本")
                        .setMessage(
                            """
                            当前版本：${AppUpdateManager.currentVersionLabel(this@HomeActivity)}
                            最新版本：${version.versionName} (${version.versionCode})

                            $changelog
                            """.trimIndent()
                        )
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("立即升级") { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val installResult = AppUpdateManager.downloadAndInstall(this@HomeActivity, version)
                                withContext(Dispatchers.Main) {
                                    installResult.fold(
                                        onSuccess = { toast("已开始安装，请在系统弹窗中确认") },
                                        onFailure = { toast(it.message ?: "安装启动失败") }
                                    )
                                }
                            }
                        }
                        .show()
                },
                onFailure = { e -> toast(e.message ?: "检查更新失败") }
            )
        }
    }

    private fun updateShopList() {
        if (shopAdapter.isReorderMode()) {
            return
        }
        collapseShopRepairSwipe()
        val filtered = viewModel.getFilteredShops()
        shopAdapter.submitList(filtered)
    }

    private fun scrollToPendingNewShopIfNeeded() {
        val cloneId = pendingScrollToCloneInstanceId?.takeIf { it.isNotBlank() } ?: return
        val index = shopAdapter.getShops().indexOfFirst { it.cloneInstanceId == cloneId }
        if (index < 0) {
            return
        }
        pendingScrollToCloneInstanceId = null
        viewBinding.rvShops.post {
            viewBinding.rvShops.smoothScrollToPosition(index)
        }
    }

    private fun collapseShopRepairSwipe() {
        if (::shopAdapter.isInitialized && shopAdapter.isReorderMode()) {
            return
        }
        if (::shopSwipeHelper.isInitialized) {
            shopSwipeHelper.collapseExpandedItem(viewBinding.rvShops)
        } else if (::shopAdapter.isInitialized) {
            shopAdapter.clearExpandedShop()
        }
    }

    private fun submitShopOrderAfterDrag() {
        if (!::shopAdapter.isInitialized || pendingShopOrderSubmit) {
            return
        }
        pendingShopOrderSubmit = true
        val orderedShops = shopAdapter.getShops()
        viewBinding.rvShops.post {
            pendingShopOrderSubmit = false
            shopAdapter.setDraggingShopId(null)
            shopAdapter.setReorderMode(false)
            viewModel.reorderShops(orderedShops)
        }
    }

    private fun exitShopReorderMode(submit: Boolean) {
        if (!::shopAdapter.isInitialized || !shopAdapter.isReorderMode()) {
            return
        }
        val orderedShops = shopAdapter.getShops()
        shopAdapter.setDraggingShopId(null)
        shopAdapter.setReorderMode(false)
        if (submit) {
            viewModel.reorderShops(orderedShops)
        }
    }

    private fun onShopClick(shop: Shop) {
        if (::shopAdapter.isInitialized && shopAdapter.isReorderMode()) {
            exitShopReorderMode(submit = true)
            return
        }
        if (suppressNextShopClickAfterSwipeCollapse) {
            suppressNextShopClickAfterSwipeCollapse = false
            return
        }
        val packageName = resolveShopPackageName(shop)
        if (packageName.isNullOrEmpty()) {
            toast("该店铺暂无关联应用")
            return
        }

        if (!viewModel.hasActiveSubscription() && shop.remainingDays <= 0) {
            renewExpiredShopBeforeOpen(shop)
            return
        }
        openPlatformForShop(shop)
    }

    private fun openWechatShareForShop(shop: Shop) {
        if (!viewModel.hasActiveSubscription() && shop.remainingDays <= 0) {
            toast("店铺已到期，请先续期后再使用微信")
            return
        }
        launchHostWechatShare(shop)
        /*
        val wechatShop = findWechatToolShop()
        if (wechatShop == null) {
            toast("请先添加并登录微信店铺")
            return
        }
        if (!beginShopOperation("打开微信", "正在加载微信数据，请稍后…")) {
            return
        }
        val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, WECHAT_PACKAGE)
        if (!EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)) {
            requestEnginePlatformPermission(wechatShop, null, WECHAT_PACKAGE)
            return
        }
        ensureEngineReady(onUnavailable = { finishShopOperation() }) {
            prepareShopEnvironment(
                shop = wechatShop,
                showProgress = true,
                launchAfterReady = false
            ) { prepared ->
                if (prepared == null) {
                    finishShopOperation()
                    toast("微信环境准备失败，请重试")
                    return@prepareShopEnvironment
                }
                launchWechatShare(targetShop = shop, preparedWechat = prepared)
            }
        }
        */
    }

    private fun launchHostWechatShare(shop: Shop) {
        val shareText = buildWechatShareText(shop)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(WECHAT_PACKAGE)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        if (shareIntent.resolveActivity(packageManager) == null) {
            toast("未检测到宿主系统微信，请先安装并登录微信")
            return
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, "分享到微信"))
        }.onFailure { error ->
            Log.w(TAG, "Failed to launch host WeChat share", error)
            toast("系统微信分享打开失败，请重试")
        }
    }

    private fun quickShareToBoundWechat(shop: Shop) {
        if (!viewModel.hasActiveSubscription() && shop.remainingDays <= 0) {
            toast("店铺已到期，请先续期后再使用分享")
            return
        }
        val target = WechatShareTargetStore.readTarget(this, shop.id)
        if (target == null || target.receiverId.isBlank()) {
            toast("请先点击微信绑定接收方")
            return
        }
        if (resolveShopPackageName(shop) == WECHAT_PACKAGE) {
            toast("微信店铺无需绑定自己")
            return
        }
        val wechatShop = findWechatToolShop()
        if (wechatShop == null) {
            toast("请先添加并登录微信店铺")
            return
        }
        if (!beginShopOperation("分享", "正在加载微信数据，请稍后…")) {
            return
        }
        val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, WECHAT_PACKAGE)
        if (!EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)) {
            requestEnginePlatformPermission(wechatShop, null, WECHAT_PACKAGE)
            return
        }
        ensureEngineReady(onUnavailable = { finishShopOperation() }) {
            prepareShopEnvironment(
                shop = wechatShop,
                showProgress = true,
                launchAfterReady = false
            ) { prepared ->
                if (prepared == null) {
                    finishShopOperation()
                    toast("微信环境准备失败，请重试")
                    return@prepareShopEnvironment
                }
                launchBoundWechatShare(shop, prepared, target)
            }
        }
    }

    private fun findWechatToolShop(): Shop? {
        return viewModel.getAllShops()
            .filter { resolveShopPackageName(it) == WECHAT_PACKAGE }
            .sortedWith(
                compareByDescending<Shop> { it.hasVerifiedIdentity }
                    .thenByDescending { it.localVirtualUserId != null }
                    .thenByDescending { it.hasLoginState }
            )
            .firstOrNull()
    }

    private fun launchWechatShare(targetShop: Shop, preparedWechat: PreparedShopEnvironment) {
        updateShopProgress("正在打开微信分享…")
        lifecycleScope.launch(Dispatchers.IO) {
            val captureStarted = EngineProxy.startWechatShareCapture(
                context = applicationContext,
                systemShopId = targetShop.id,
                packageName = WECHAT_PACKAGE,
                userId = preparedWechat.userId,
                timeoutMs = 30_000L,
                onResolved = { target ->
                    Log.d(TAG, "WeChat share target resolved: $target")
                    runOnUiThread {
                        shopAdapter.refreshShop(targetShop.id)
                    }
                },
                onTimeout = { packageName, userId ->
                    Log.w(TAG, "WeChat share target timeout package=$packageName userId=$userId")
                    runOnUiThread {
                        toast("未获取到微信接收方，请确认微信已登录并完成选择")
                    }
                },
                onCancelled = { packageName, userId ->
                    Log.d(TAG, "WeChat share target cancelled package=$packageName userId=$userId")
                },
                onError = { packageName, userId, message ->
                    Log.w(TAG, "WeChat share target error package=$packageName userId=$userId message=$message")
                    runOnUiThread {
                        toast("微信接收方获取失败，请重试")
                    }
                }
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                component = ComponentName(WECHAT_PACKAGE, WECHAT_SHARE_ACTIVITY)
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(Intent.EXTRA_TEXT, buildWechatShareText(targetShop))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d(TAG, "Launching WeChat share in clone user=${preparedWechat.userId} intent=$shareIntent")
            val started = EngineProxy.startActivityAsUser(shareIntent, preparedWechat.userId)
            if (!started && captureStarted) {
                EngineProxy.cancelWechatShareCapture(WECHAT_PACKAGE, preparedWechat.userId)
            }
            withContext(Dispatchers.Main) {
                finishShopOperation()
                if (!started) {
                    toast("微信分享打开失败，请重试")
                }
            }
        }
    }

    private fun launchBoundWechatShare(
        targetShop: Shop,
        preparedWechat: PreparedShopEnvironment,
        target: WechatShareTargetStore.Target
    ) {
        updateShopProgress("正在打开微信发送页…")
        lifecycleScope.launch(Dispatchers.IO) {
            val sendIntent = buildWechatBoundShareIntent(targetShop, target)
            Log.d(
                TAG,
                "Launching bound WeChat share shop=${targetShop.id} user=${preparedWechat.userId} receiver=${target.receiverId} intent=$sendIntent"
            )
            val started = EngineProxy.startActivityAsUser(sendIntent, preparedWechat.userId)
            withContext(Dispatchers.Main) {
                finishShopOperation()
                if (started) {
                    toast("已打开微信发送页")
                } else {
                    toast("微信一键分享打开失败，请重试")
                }
            }
        }
    }

    private fun buildWechatBoundShareIntent(
        targetShop: Shop,
        target: WechatShareTargetStore.Target
    ): Intent {
        val shareText = buildWechatShareText(targetShop)
        return Intent().apply {
            component = ComponentName(WECHAT_PACKAGE, WECHAT_SEND_WRAPPER_ACTIVITY)
            type = "text/plain"
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra("Select_Conv_User", target.receiverId)
            putExtra("_wxtextobject_text", shareText)
            putExtra("_wxobject_description", shareText)
            putExtra("_wxapi_sendmessagetowx_req_media_type", 1)
            putExtra("_wxapi_sendmessagetowx_req_scene", 0)
            putExtra("_wxapi_command_type", 2)
            putExtra("_wxobject_identifier_", "com.tencent.mm.sdk.openapi.WXTextObject")
            putExtra("_mmessage_appPackage", packageName)
            putExtra("_mmessage_sdkVersion", 638058496)
            putExtra("SendAppMessageWrapper_AppId", "")
            putExtra("SendAppMessageWrapper_Scene", 0)
            putExtra("Retr_Msg_Type", 2)
            putExtra("Retr_Msg_content", shareText)
            putExtra("Retr_Msg_thumb_path", "")
            putExtra("Ksnsupload_type", 0)
            putExtra("need_result", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun buildWechatShareText(shop: Shop): String {
        val shopName = shop.shopName.trim().ifBlank { "店铺" }
        return "${shopName}日报"
    }

    private fun renewExpiredShopBeforeOpen(shop: Shop) {
        if (shop.autoRenew) {
            viewModel.renewShopWithToken(shop) { renewedShop, token ->
                openPlatformForShop(renewedShop, token)
            }
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("店铺已到期")
            .setMessage(
                if (viewModel.hasExpiredSubscriptionRecord()) {
                    "订阅已到期，继续使用将扣除1点算力"
                } else {
                    "是否扣减 1 点算力为该店铺续期 30 天？"
                }
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                viewModel.renewShopWithToken(shop) { renewedShop, token ->
                    openPlatformForShop(renewedShop, token)
                }
            }
            .show()
    }

    private fun openPlatformForShop(shop: Shop, freshAuthorizationToken: String? = null) {
        if (!beginShopOperation("打开店铺", "正在加载店铺数据，请稍后…")) {
            return
        }
        val packageName = resolveShopPackageName(shop)
        val requiredPermissions = EnginePermissionCenter.platformRequiredPermissions(this, packageName)
        if (!EnginePermissionCenter.hasEnginePermissions(this, requiredPermissions)) {
            requestEnginePlatformPermission(shop, freshAuthorizationToken, packageName)
            return
        }
        openPlatformForShopAfterEnginePermission(shop, freshAuthorizationToken)
    }

    private fun openPlatformForShopAfterEnginePermission(shop: Shop, freshAuthorizationToken: String? = null) {
        ensureEngineReady(onUnavailable = { finishShopOperation() }) {
            if (freshAuthorizationToken != null) {
                openPlatformForShopWithEngine(shop, freshAuthorizationToken)
                return@ensureEngineReady
            }
            prepareShopEnvironment(
                shop = shop,
                showProgress = true,
                launchAfterReady = true
            )
        }
    }

    private fun requestEnginePlatformPermission(
        shop: Shop,
        freshAuthorizationToken: String?,
        packageName: String?
    ) {
        updateShopProgress("正在请求引擎权限…")
        pendingEnginePermissionShop = shop
        pendingEnginePermissionFreshToken = freshAuthorizationToken
        pendingEnginePermissionPackage = packageName
        try {
            enginePermissionLauncher.launch(EnginePermissionCenter.buildPlatformIntent(this, packageName))
        } catch (e: Exception) {
            pendingEnginePermissionShop = null
            pendingEnginePermissionFreshToken = null
            pendingEnginePermissionPackage = null
            Log.w(TAG, "Failed to launch engine permission activity", e)
            finishShopOperation()
            toast("无法打开引擎权限授权页，请检查引擎是否已安装")
        }
    }

    private fun prepareShopEnvironment(
        shop: Shop,
        showProgress: Boolean,
        launchAfterReady: Boolean,
        onComplete: ((PreparedShopEnvironment?) -> Unit)? = null
    ) {
        val packageName = resolveShopPackageName(shop)
        if (packageName.isNullOrBlank()) {
            if (showProgress) {
                finishShopOperation()
                toast("该店铺暂无关联应用")
            }
            onComplete?.invoke(null)
            return
        }
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        if (cloneInstanceId == null) {
            if (showProgress && launchAfterReady) {
                openPlatformForShopWithEngine(shop)
            }
            onComplete?.invoke(null)
            return
        }
        if (!EngineProxy.isConnected()) {
            if (showProgress) {
                updateShopProgress("正在重新连接引擎，请稍候…")
                retryOpenAfterEngineReconnect(shop)
            }
            onComplete?.invoke(null)
            return
        }
        val prepareKey = buildShopPrepareKey(shop, packageName) ?: run {
            onComplete?.invoke(null)
            return
        }
        preparedShopUsers[prepareKey]?.let { preparedUserId ->
            Log.d(TAG, "Preparing shop environment uses prepared user shop=${shop.id} package=$packageName user=$preparedUserId")
            val prepared = PreparedShopEnvironment(shop, packageName, preparedUserId, resolvePlatformName(shop.platform))
            if (showProgress && launchAfterReady) {
                launchPreparedShopAndSchedule(prepared)
            }
            onComplete?.invoke(prepared)
            return
        }
        if (preparingShopKeys.contains(prepareKey)) {
            onComplete?.let {
                preparingShopCallbacks.getOrPut(prepareKey) { mutableListOf() }.add(it)
            }
            if (showProgress) {
                updateShopProgress("正在准备店铺环境，请稍候…")
                preparingShopCallbacks.getOrPut(prepareKey) { mutableListOf() }.add { prepared ->
                    if (prepared != null && launchAfterReady) {
                        launchPreparedShopAndSchedule(prepared)
                    } else {
                        finishShopOperation()
                        toast("店铺环境准备失败，请重试")
                    }
                }
            }
            return
        }

        preparingShopKeys.add(prepareKey)
        val platformName = resolvePlatformName(shop.platform)
        var failureHandled = false
        Log.d(TAG, "Preparing shop environment shop=${shop.id} package=$packageName clone=$cloneInstanceId progress=$showProgress")
        val complete: (PreparedShopEnvironment?) -> Unit = { prepared ->
            preparingShopKeys.remove(prepareKey)
            if (prepared != null) {
                preparedShopKeys.add(prepareKey)
                preparedShopUsers[prepareKey] = prepared.userId
                Log.d(TAG, "Shop environment ready shop=${shop.id} package=$packageName user=${prepared.userId}")
            } else {
                Log.w(TAG, "Shop environment prepare failed shop=${shop.id} package=$packageName")
            }
            preparingShopCallbacks.remove(prepareKey).orEmpty().forEach { it(prepared) }
            onComplete?.invoke(prepared)
            if (showProgress && launchAfterReady) {
                if (prepared != null) {
                    launchPreparedShopAndSchedule(prepared)
                } else if (!failureHandled) {
                    finishShopOperation()
                    toast("店铺环境准备失败，请重试")
                }
            }
        }

        if (showProgress) {
            updateShopProgress("正在校验店铺环境…")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isHostInstalled = try {
                    packageManager.getPackageInfo(packageName, 0) != null
                } catch (e: Exception) {
                    false
                }
                if (!isHostInstalled) {
                    withContext(Dispatchers.Main) {
                        if (showProgress) {
                            failureHandled = true
                            finishShopOperation()
                            toast("检测到您尚未安装 ${platformName}，请前往应用市场下载安装后重试")
                            openAppMarket(packageName)
                        }
                        complete(null)
                    }
                    return@launch
                }

                val userId = findUserIdForCloneInstance(shop, packageName)
                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        if (showProgress) {
                            failureHandled = true
                            finishShopOperation()
                            toast("创建店铺失败，请重试")
                        }
                        complete(null)
                    }
                    return@launch
                }
                if (showProgress) {
                    withContext(Dispatchers.Main) {
                        updateShopProgress("正在准备店铺目录…")
                    }
                }

                val wasInstalled = EngineProxy.isInstalled(packageName, userId)
                if (!wasInstalled) {
                    if (showProgress) {
                        withContext(Dispatchers.Main) {
                            updateShopProgress("正在安装 ${platformName} 到店铺环境…")
                        }
                    }
                    val result = EngineProxy.installPackageAsUser(packageName, userId)
                    if (!result.success && !EngineProxy.isInstalled(packageName, userId)) {
                        withContext(Dispatchers.Main) {
                            if (showProgress) {
                                failureHandled = true
                                finishShopOperation()
                                toast("店铺环境准备失败: ${result.msg}")
                            }
                            complete(null)
                        }
                        return@launch
                    }
                }
                if (!wasInstalled) {
                    val restoreResult = restoreLoginStateFromServer(
                        shop = shop,
                        packageName = packageName,
                        userId = userId,
                        showProgress = showProgress,
                        reason = "local-absent"
                    )
                    if (restoreResult == LoginStateRestoreResult.FAILED) {
                        withContext(Dispatchers.Main) {
                            complete(null)
                        }
                        return@launch
                    }
                }
                if (hasPreparedLaunchAuthorization(cloneInstanceId, packageName, userId)) {
                    withContext(Dispatchers.Main) {
                        complete(PreparedShopEnvironment(shop, packageName, userId, platformName))
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (showProgress) {
                        updateShopProgress("正在获取店铺授权…")
                    }
                    viewModel.issueShopAuthToken(
                        shop,
                        packageName,
                        userId,
                        onSuccess = { authorizedShop, token, publicKeyId ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val prepared = try {
                                    val authorizedPrepared = writeAuthorizationForPreparedShop(
                                        shop = authorizedShop,
                                        packageName = packageName,
                                        userId = userId,
                                        platformName = platformName,
                                        authorizationToken = token,
                                        publicKeyId = publicKeyId,
                                        showProgress = showProgress
                                    )
                                    authorizedPrepared
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to write prepared shop authorization ${shop.id}", e)
                                    null
                                }
                                withContext(Dispatchers.Main) {
                                    complete(prepared)
                                }
                            }
                        },
                        onFailure = {
                            complete(null)
                        },
                        showError = showProgress
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prepare shop environment ${shop.id}", e)
                withContext(Dispatchers.Main) {
                    if (showProgress) {
                        failureHandled = true
                        finishShopOperation()
                        toast("店铺环境准备异常: ${e.message}")
                    }
                    complete(null)
                }
            }
        }
    }

    private fun openPlatformForShopWithEngine(shop: Shop, freshAuthorizationToken: String? = null) {
        val packageName = resolveShopPackageName(shop)
        if (packageName.isNullOrEmpty()) {
            finishShopOperation()
            toast("该店铺暂无关联应用")
            return
        }
        val platformName = resolvePlatformName(shop.platform)

        if (!EngineProxy.isConnected()) {
            updateShopProgress("正在重新连接引擎，请稍候…")
            retryOpenAfterEngineReconnect(shop)
            return
        }

        updateShopProgress("正在校验店铺环境…")
        var targetUserId = findUserIdForCloneInstance(shop, packageName)
        if (targetUserId != null && EngineProxy.isInstalled(packageName, targetUserId)) {
            if (freshAuthorizationToken != null) {
                writeCloneAuthorization(shop, packageName, targetUserId, freshAuthorizationToken, null)
            }
            launchVirtualApp(shop, packageName, targetUserId, platformName) { launched ->
                if (launched) {
                    if (shop.isNew) {
                        reportCloneCreated(shop, targetUserId)
                    }
                    scheduleShopInfoSyncAfterClick(shop, targetUserId, showFailureToast = shop.isNew)
                }
            }
            return
        }

        // 2. 检查宿主机是否安装了该 APP
        val isHostInstalled = try {
            packageManager.getPackageInfo(packageName, 0) != null
        } catch (e: Exception) {
            false
        }

        if (!isHostInstalled) {
            // 3. 宿主机未安装，提示用户去应用市场
            finishShopOperation()
            toast("检测到您尚未安装 ${platformName}，请前往应用市场下载安装后重试")
            openAppMarket(packageName)
            return
        }

        var createdFreshUser = false
        val installUserId = targetUserId ?: if (shop.isNew) {
            val freshUserId = createFreshVirtualUserId() ?: run {
                finishShopOperation()
                toast("创建店铺失败，请重试")
                return
            }
            createdFreshUser = true
            freshUserId
        } else {
            ensureVirtualUserId()
        }

        updateShopProgress(if (targetUserId == null) "正在创建店铺目录…" else "正在恢复店铺环境…")

        try {
            updateShopProgress("正在安装 ${platformName} 到店铺环境…")
            val result = EngineProxy.installPackageAsUser(packageName, installUserId)
            if (result.success || EngineProxy.isInstalled(packageName, installUserId)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    restoreLoginStateFromServer(
                        shop = shop,
                        packageName = packageName,
                        userId = installUserId,
                        showProgress = true,
                        reason = "local-absent"
                    )
                    withContext(Dispatchers.Main) {
                        updateShopProgress("正在获取店铺授权…")
                        if (shop.isNew) {
                            reportCloneCreated(shop, installUserId)
                        }
                        launchVirtualApp(shop, packageName, installUserId, platformName) { launched ->
                            if (launched) {
                                scheduleShopInfoSyncAfterClick(shop, installUserId, showFailureToast = shop.isNew)
                            }
                        }
                    }
                }
            } else {
                if (!EngineProxy.isConnected()) {
                    updateShopProgress("正在重新连接引擎，请稍候…")
                    retryOpenAfterEngineReconnect(shop)
                } else {
                    if (createdFreshUser) {
                        EngineProxy.deleteUser(installUserId)
                    }
                    finishShopOperation()
                    toast("店铺创建失败: ${result.msg}")
                }
            }
        } catch (e: Exception) {
            if (createdFreshUser) {
                EngineProxy.deleteUser(installUserId)
            }
            finishShopOperation()
            toast("店铺创建异常: ${e.message}")
        }
    }

    private fun retryOpenAfterEngineReconnect(shop: Shop) {
        pendingEngineAction = { openPlatformForShopWithEngine(shop) }
        EngineProxy.addServiceAvailableCallback {
            runOnUiThread {
                val pendingAction = pendingEngineAction
                pendingEngineAction = null
                pendingAction?.invoke()
            }
        }
        val requested = App.ensureEngineConnection()
        if (requested) {
            updateShopProgress("正在重新连接引擎，请稍候…")
        } else {
            pendingEngineAction = null
            finishShopOperation()
            toast("引擎连接失败，请重试")
        }
    }

    private fun launchVirtualApp(
        shop: Shop,
        packageName: String,
        userId: Int,
        platformName: String,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        prepareLaunchAndStart(
            shop = shop,
            packageName = packageName,
            userId = userId,
            platformName = platformName,
            cloneInstanceId = cloneInstanceId,
            onMissingAuthorization = {
                requestAndWriteAuthorizationThenLaunch(
                    shop = shop,
                    packageName = packageName,
                    userId = userId,
                    platformName = platformName,
                    onLaunched = { onResult?.invoke(true) },
                    onAuthorizationFailure = { onResult?.invoke(false) }
                )
            },
            onResult = onResult
        )
    }

    private fun prepareLaunchAndStart(
        shop: Shop,
        packageName: String,
        userId: Int,
        platformName: String,
        cloneInstanceId: String?,
        onMissingAuthorization: () -> Unit,
        onResult: ((Boolean) -> Unit)?
    ) {
        setShopProgressDeterminate(true, 10)
        updateShopProgress("正在加载店铺数据，请稍后…")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val preparation = EngineProxy.prepareLaunch(packageName, userId)
                if (preparation == null && !EngineProxy.isConnected()) {
                    withContext(Dispatchers.Main) {
                        finishShopOperation()
                        toast("引擎连接失败，请重试")
                        onResult?.invoke(false)
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) {
                        onResult?.invoke(false)
                        return@withContext
                    }
                    val progressAfterPrepare = if (preparation?.singleInstanceMode == true) 75 else 55
                    setShopProgressDeterminate(true, progressAfterPrepare)
                    val message = if (preparation?.singleInstanceMode == true) {
                        "正在切换店铺运行环境…"
                    } else {
                        "正在校验店铺启动环境…"
                    }
                    updateShopProgress(message)
                }

                if (preparation?.success == false) {
                    withContext(Dispatchers.Main) {
                        finishShopOperation()
                        toast("加载店铺数据失败，请重试")
                        onResult?.invoke(false)
                    }
                    return@launch
                }

                val launchIntent = if (cloneInstanceId.isNullOrBlank()) {
                    EngineProxy.peekLaunchIntent(packageName, userId)
                } else {
                    EngineProxy.peekAuthorizedLaunchIntent(cloneInstanceId, packageName, userId)
                }

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) {
                        onResult?.invoke(false)
                        return@withContext
                    }
                    if (launchIntent == null) {
                        if (cloneInstanceId.isNullOrBlank()) {
                            finishShopOperation()
                            toast("启动失败，请重新创建店铺")
                            onResult?.invoke(false)
                        } else {
                            onMissingAuthorization()
                        }
                        return@withContext
                    }
                    setShopProgressDeterminate(true, 90)
                    updateShopProgress("正在打开 ${platformName}…")
                    startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    setShopProgressDeterminate(true, 100)
                    finishShopOperation()
                    onResult?.invoke(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare and launch shop ${shop.id} package=$packageName user=$userId", e)
                withContext(Dispatchers.Main) {
                    finishShopOperation()
                    toast("启动失败: ${e.message}")
                    onResult?.invoke(false)
                }
            }
        }
    }

    private fun writeAuthorizationForPreparedShop(
        shop: Shop,
        packageName: String,
        userId: Int,
        platformName: String,
        authorizationToken: String,
        publicKeyId: String?,
        showProgress: Boolean
    ): PreparedShopEnvironment? {
        if (authorizationToken.isBlank()) {
            return null
        }
        if (showProgress) {
            runOnUiThread { updateShopProgress("正在写入店铺授权…") }
        }
        if (!writeCloneAuthorization(shop, packageName, userId, authorizationToken, publicKeyId)) {
            Log.w(TAG, "Failed to write clone authorization for $packageName user=$userId shop=${shop.id}")
            return null
        }
        if (showProgress) {
            runOnUiThread { updateShopProgress("正在校验店铺授权…") }
        }
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() } ?: return null
        if (!hasPreparedLaunchAuthorization(cloneInstanceId, packageName, userId)) {
            return null
        }
        return PreparedShopEnvironment(shop, packageName, userId, platformName)
    }

    private fun launchPreparedShopAndSchedule(prepared: PreparedShopEnvironment) {
        launchPreparedShop(prepared) { launched ->
            if (launched) {
                scheduleShopInfoSyncAfterClick(prepared.shop, prepared.userId, showFailureToast = prepared.shop.isNew)
            }
        }
    }

    private fun launchPreparedShop(prepared: PreparedShopEnvironment, onResult: (Boolean) -> Unit) {
        val prepareKey = buildShopPrepareKey(prepared.shop, prepared.packageName)
        val cloneInstanceId = prepared.shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        if (cloneInstanceId == null) {
            finishShopOperation()
            toast("店铺授权校验失败，请点击修复店铺后重试")
            onResult(false)
            return
        }
        prepareLaunchAndStart(
            shop = prepared.shop,
            packageName = prepared.packageName,
            userId = prepared.userId,
            platformName = prepared.platformName,
            cloneInstanceId = cloneInstanceId,
            onMissingAuthorization = {
                prepareKey?.let {
                    preparedShopKeys.remove(it)
                    preparedShopUsers.remove(it)
                }
                finishShopOperation()
                toast("店铺授权校验失败，请点击修复店铺后重试")
                onResult(false)
            },
            onResult = { launched ->
                if (!launched) {
                    prepareKey?.let {
                        preparedShopKeys.remove(it)
                        preparedShopUsers.remove(it)
                    }
                }
                onResult(launched)
            }
        )
    }

    private suspend fun restoreLoginStateFromServer(
        shop: Shop,
        packageName: String,
        userId: Int,
        showProgress: Boolean,
        reason: String
    ): LoginStateRestoreResult {
        val key = buildShopPrepareKey(shop, packageName)?.let { "$it:$userId:$reason" }
        if (key != null && restoredLoginStateKeys.contains(key)) {
            Log.d(TAG, "restoreLoginState skipped cached shop=${shop.id} package=$packageName user=$userId reason=$reason")
            return LoginStateRestoreResult.RESTORED
        }
        val defaultProfile = EngineProxy.defaultLoginStateProfile(packageName)
        if (defaultProfile == null) {
            Log.d(TAG, "restoreLoginState skipped no profile shop=${shop.id} package=$packageName user=$userId")
            return LoginStateRestoreResult.NOT_AVAILABLE
        }
        Log.d(
            TAG,
            "restoreLoginState allowed shop=${shop.id} package=$packageName user=$userId reason=$reason hasServer=${shop.hasLoginState} profile=$defaultProfile"
        )
        if (showProgress) {
            withContext(Dispatchers.Main) { updateShopProgress("正在恢复店铺登录态…") }
        }
        val result = viewModel.downloadLoginState(shop.id)
        result.exceptionOrNull()?.let { error ->
            Log.w(TAG, "restoreLoginState download failed shop=${shop.id} package=$packageName user=$userId reason=$reason: ${error.message}")
            return LoginStateRestoreResult.FAILED
        }
        val download = result.getOrNull()
        if (download == null || download.bytes.isEmpty()) {
            Log.d(TAG, "restoreLoginState no server artifact shop=${shop.id} package=$packageName user=$userId reason=$reason")
            return LoginStateRestoreResult.NOT_AVAILABLE
        }
        val profile = download.profile ?: defaultProfile
        val staged = LoginStateBackupStore.stageForRestore(shop.id, packageName, userId, profile, download.bytes)
        Log.d(
            TAG,
            "restoreLoginState downloaded shop=${shop.id} package=$packageName user=$userId reason=$reason profile=$profile bytes=${download.bytes.size}"
        )
        val restored = EngineProxy.restoreLoginState(
            packageName,
            userId,
            profile,
            download.bytes
        )
        LoginStateBackupStore.markRestoreFinishedAndDelete(staged, restored)
        if (restored) {
            Log.d(TAG, "restoreLoginState restored shop=${shop.id} package=$packageName user=$userId reason=$reason")
            key?.let { restoredLoginStateKeys.add(it) }
            return LoginStateRestoreResult.RESTORED
        } else {
            Log.w(TAG, "restoreLoginState restore rejected shop=${shop.id} package=$packageName user=$userId reason=$reason")
            return LoginStateRestoreResult.FAILED
        }
    }

    private fun exportAndUploadLoginState(shop: Shop, packageName: String, userId: Int) {
        val key = buildShopPrepareKey(shop, packageName)?.let { "$it:$userId" } ?: return
        if (!uploadedLoginStateKeys.add(key)) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val profile = EngineProxy.defaultLoginStateProfile(packageName) ?: run {
                uploadedLoginStateKeys.remove(key)
                return@launch
            }
            val artifact = EngineProxy.exportLoginState(packageName, userId, profile) ?: run {
                uploadedLoginStateKeys.remove(key)
                return@launch
            }
            val createdAt = System.currentTimeMillis()
            val manifest = JSONObject()
                .put("systemShopId", shop.id)
                .put("packageName", packageName)
                .put("profileId", profile)
                .put("localVirtualUserId", userId)
                .put("artifactCreatedAtEpochMillis", createdAt)
                .put("bytes", artifact.size)
            val staged = LoginStateBackupStore.stageForUpload(
                shopId = shop.id,
                packageName = packageName,
                userId = userId,
                profile = profile,
                artifact = artifact,
                manifest = manifest
            )
            val uploaded = viewModel.uploadLoginStateFile(shop.id, profile, manifest.toString(), staged.file)
            if (uploaded) {
                LoginStateBackupStore.markUploadedAndDelete(staged)
            } else {
                uploadedLoginStateKeys.remove(key)
            }
        }
    }

    private fun requestAndWriteAuthorizationThenLaunch(
        shop: Shop,
        packageName: String,
        userId: Int,
        platformName: String,
        onLaunched: (() -> Unit)? = null,
        onAuthorizationFailure: (() -> Unit)? = null
    ) {
        updateShopProgress("正在获取店铺授权…")
        viewModel.issueShopAuthToken(
            shop,
            packageName,
            userId,
            onSuccess = { authorizedShop, token, publicKeyId ->
                writeAuthorizationAndMaybeLaunch(
                    shop = authorizedShop,
                    packageName = packageName,
                    userId = userId,
                    platformName = platformName,
                    authorizationToken = token,
                    publicKeyId = publicKeyId,
                    launchAfterWrite = true,
                    onLaunched = onLaunched,
                    onAuthorizationFailure = onAuthorizationFailure
                )
            },
            onFailure = { message ->
                finishShopOperation()
                onAuthorizationFailure?.invoke()
                toast(message)
            }
        )
    }

    private fun writeAuthorizationAndMaybeLaunch(
        shop: Shop,
        packageName: String,
        userId: Int,
        platformName: String,
        authorizationToken: String,
        publicKeyId: String?,
        launchAfterWrite: Boolean,
        onLaunched: (() -> Unit)? = null,
        onAuthorizationFailure: (() -> Unit)? = null
    ) {
        if (authorizationToken.isBlank()) {
            finishShopOperation()
            onAuthorizationFailure?.invoke()
            toast("服务器未返回店铺授权，请重试")
            return
        }
        updateShopProgress("正在写入店铺授权…")
        if (!writeCloneAuthorization(shop, packageName, userId, authorizationToken, publicKeyId)) {
            Log.w(TAG, "Failed to write clone authorization for $packageName user=$userId shop=${shop.id}")
            finishShopOperation()
            onAuthorizationFailure?.invoke()
            toast("店铺授权写入失败，请重试")
            return
        }
        if (!launchAfterWrite) {
            finishShopOperation()
            onLaunched?.invoke()
            return
        }
        updateShopProgress("正在校验店铺授权…")
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        if (cloneInstanceId == null ||
            !hasPreparedLaunchAuthorization(cloneInstanceId, packageName, userId)
        ) {
            Log.w(TAG, "Authorized launch intent missing for $packageName user=$userId shop=${shop.id}")
            finishShopOperation()
            onAuthorizationFailure?.invoke()
            toast("店铺授权校验失败，请点击修复店铺后重试")
            return
        }
        Log.d(TAG, "Authorized launch intent resolved for $packageName user=$userId shop=${shop.id}")
        prepareLaunchAndStart(
            shop = shop,
            packageName = packageName,
            userId = userId,
            platformName = platformName,
            cloneInstanceId = cloneInstanceId,
            onMissingAuthorization = {
                finishShopOperation()
                onAuthorizationFailure?.invoke()
                toast("店铺授权校验失败，请点击修复店铺后重试")
            },
            onResult = { launched ->
                if (launched) {
                    onLaunched?.invoke()
                } else {
                    onAuthorizationFailure?.invoke()
                }
            }
        )
    }

    private fun writeCloneAuthorization(
        shop: Shop,
        packageName: String,
        userId: Int,
        authorizationToken: String,
        publicKeyId: String?
    ): Boolean {
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() } ?: return false
        return EngineProxy.writeCloneAuthorization(
            cloneInstanceId = cloneInstanceId,
            packageName = packageName,
            serverUserId = viewModel.getCurrentUserId(),
            phone = viewModel.getCurrentUserPhone(),
            userId = userId,
            publicKeyId = publicKeyId ?: "rsa_2026_01",
            authorizationToken = authorizationToken
        )
    }

    private fun markShopEnvironmentPrepared(shop: Shop, packageName: String, userId: Int) {
        buildShopPrepareKey(shop, packageName)?.let { key ->
            preparedShopKeys.add(key)
            preparedShopUsers[key] = userId
        }
    }

    private fun buildShopPrepareKey(shop: Shop, packageName: String): String? {
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() } ?: return null
        return "${viewModel.getCurrentUserId()}:$packageName:$cloneInstanceId"
    }

    private fun findPreparedUserIdForShop(shop: Shop, packageName: String): Int? {
        val prepareKey = buildShopPrepareKey(shop, packageName) ?: return null
        preparedShopUsers[prepareKey]?.let { return it }
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() } ?: return null
        if (!EngineProxy.isConnected()) {
            return null
        }
        val userId = findExistingUserIdForCloneInstance(shop, packageName) ?: return null
        val isReady = EngineProxy.isInstalled(packageName, userId) &&
                hasPreparedLaunchAuthorization(cloneInstanceId, packageName, userId)
        if (!isReady) {
            return null
        }
        markShopEnvironmentPrepared(shop, packageName, userId)
        return userId
    }

    private fun hasPreparedLaunchAuthorization(
        cloneInstanceId: String,
        packageName: String,
        userId: Int
    ): Boolean {
        return if (requiresLightweightLaunchCheck(packageName)) {
            EngineProxy.isCloneAuthorized(cloneInstanceId, packageName, viewModel.getCurrentUserId(), userId)
        } else {
            EngineProxy.peekAuthorizedLaunchIntent(cloneInstanceId, packageName, userId) != null
        }
    }

    private fun requiresLightweightLaunchCheck(packageName: String): Boolean {
        return packageName == "com.bytedance.ls.merchant"
    }

    private fun addShopForSelectedPlatform() {
        val platformItem = viewModel.getSelectedPlatformItem()
        if (platformItem == null) {
            toast("暂无可用平台")
            return
        }
        if (!platformItem.available) {
            toast("该平台的店铺管理功能暂不支持")
            return
        }
        val packageName = platformItem.packageName?.takeIf { it.isNotBlank() }
        if (packageName == null) {
            toast("该平台暂无关联应用")
            return
        }
        if (viewModel.isCurrentUserActiveSubscriber()) {
            createCloneAndPendingShop(platformItem, launchAfterCreate = false)
            return
        }
        if (viewModel.getCurrentComputeBalance() < 1) {
            toast("算力余额不足")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("添加店铺")
            .setMessage("创建新店铺需要扣除 1 点算力，确认继续吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                createCloneAndPendingShop(platformItem, launchAfterCreate = false)
            }
            .show()
    }

    private fun createCloneAndPendingShop(platformItem: PlatformItemDto, launchAfterCreate: Boolean) {
        if (!beginShopOperation("创建店铺", "正在连接引擎，请稍候…")) {
            return
        }
        ensureEngineReady(onUnavailable = { finishShopOperation() }) {
            val packageName = platformItem.packageName?.takeIf { it.isNotBlank() }
            if (packageName == null) {
                finishShopOperation()
                toast("该平台暂无关联应用")
                return@ensureEngineReady
            }
            val platformName = platformItem.displayName.ifBlank { resolvePlatformName(platformItem.platform) }
            updateShopProgress("正在检查本机应用…")
            val isHostInstalled = try {
                packageManager.getPackageInfo(packageName, 0) != null
            } catch (e: Exception) {
                false
            }
            if (!isHostInstalled) {
                finishShopOperation()
                toast("检测到您尚未安装 ${platformName}，请前往应用市场下载安装后重试")
                openAppMarket(packageName)
                return@ensureEngineReady
            }

            lifecycleScope.launch(Dispatchers.IO) {
                var userId: Int? = null
                try {
                    withContext(Dispatchers.Main) {
                        updateShopProgress("正在创建店铺目录…")
                    }
                    userId = createFreshVirtualUserId()
                    val createdUserId = userId
                    if (createdUserId == null) {
                        withContext(Dispatchers.Main) {
                            finishShopOperation()
                            toast("创建店铺失败，请稍后重试")
                        }
                        return@launch
                    }

                    if (!EngineProxy.isInstalled(packageName, createdUserId)) {
                        withContext(Dispatchers.Main) {
                            updateShopProgress("正在安装 ${platformName} 到店铺环境…")
                        }
                        val result = EngineProxy.installPackageAsUser(packageName, createdUserId)
                        if (!result.success && !EngineProxy.isInstalled(packageName, createdUserId)) {
                            EngineProxy.deleteUser(createdUserId)
                            withContext(Dispatchers.Main) {
                                finishShopOperation()
                                toast("店铺创建失败: ${result.msg}")
                            }
                            return@launch
                        }
                    }

                    withContext(Dispatchers.Main) {
                        invalidatePreparedPlatformEnvironment(packageName)
                    }
                    withContext(Dispatchers.Main) {
                        updateShopProgress("正在上报服务器并扣减算力…")
                        viewModel.createPendingShopWithClone(
                            platformItem = platformItem,
                            localUserId = createdUserId,
                            onSuccess = { pendingShop, createResult ->
                                val cloneInstanceId = pendingShop.cloneInstanceId?.takeIf { it.isNotBlank() }
                                if (cloneInstanceId == null) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        EngineProxy.deleteUser(createdUserId)
                                    }
                                    finishShopOperation()
                                    toast("服务器未返回店铺标识，请重试")
                                    return@createPendingShopWithClone
                                }
                                lifecycleScope.launch(Dispatchers.IO) {
                                    EngineProxy.bindCloneUser(cloneInstanceId, packageName, viewModel.getCurrentUserId(), createdUserId)
                                    withContext(Dispatchers.Main) {
                                        if (launchAfterCreate) {
                                            writeAuthorizationAndMaybeLaunch(
                                                shop = pendingShop,
                                                packageName = packageName,
                                                userId = createdUserId,
                                                platformName = platformName,
                                                authorizationToken = createResult.authorizationToken,
                                                publicKeyId = createResult.publicKeyId,
                                                launchAfterWrite = true,
                                                onLaunched = {
                                                    scheduleShopInfoSyncAfterClick(pendingShop, createdUserId, showFailureToast = true)
                                                }
                                            )
                                        } else {
                                            writeAuthorizationAndMaybeLaunch(
                                                shop = pendingShop,
                                                packageName = packageName,
                                                userId = createdUserId,
                                                platformName = platformName,
                                                authorizationToken = createResult.authorizationToken,
                                                publicKeyId = createResult.publicKeyId,
                                                launchAfterWrite = false,
                                                onLaunched = {
                                                    pendingScrollToCloneInstanceId = cloneInstanceId
                                                    updateShopProgress("新店铺已准备好…")
                                                    viewModel.loadShops()
                                                    handler.postDelayed({
                                                        scrollToPendingNewShopIfNeeded()
                                                        toast("新店铺已准备好，请点击 new 店铺登录")
                                                    }, 250)
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onFailure = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    EngineProxy.deleteUser(createdUserId)
                                }
                                finishShopOperation()
                            }
                        )
                    }
                } catch (e: Exception) {
                    userId?.let { EngineProxy.deleteUser(it) }
                    withContext(Dispatchers.Main) {
                        finishShopOperation()
                        toast("新店铺创建异常: ${e.message}")
                    }
                }
            }
        }
    }

    private fun reportCloneCreated(pendingShop: Shop, userId: Int) {
        val packageName = resolveShopPackageName(pendingShop) ?: return
        val cloneInstanceId = pendingShop.cloneInstanceId?.takeIf { it.isNotBlank() }
            ?: buildLegacyCloneInstanceId(packageName, userId)
        EngineProxy.bindCloneUser(cloneInstanceId, packageName, viewModel.getCurrentUserId(), userId)
        if (cloneInstanceId == pendingShop.cloneInstanceId) {
            return
        }
        viewModel.reportShop(
            pendingShop.copy(
                cloneInstanceId = cloneInstanceId,
                shopName = pendingShop.shopName.ifBlank { "新增店铺-[1]" },
                shopId = pendingShop.shopId,
                localVirtualUserId = userId
            ),
            showMessage = false,
            requireVerifiedIdentity = false
        )
    }

    private fun scheduleShopInfoSyncAfterClick(
        shop: Shop,
        userId: Int,
        showFailureToast: Boolean = false
    ) {
        val key = buildRecognitionKey(shop, userId) ?: return
        if (!pendingRecognitionKeys.add(key)) {
            return
        }
        handler.postDelayed({
            syncShopInfoFromEngine(shop, userId, showFailureToast)
        }, SHOP_RECOGNITION_AFTER_CLICK_DELAY_MS)
    }

    private fun syncShopInfoFromEngine(
        shop: Shop,
        userId: Int,
        showFailureToast: Boolean = false
    ) {
        val packageName = resolveShopPackageName(shop)
        val key = buildRecognitionKey(shop, userId)
        if (packageName == null) {
            key?.let { pendingRecognitionKeys.remove(it) }
            return
        }
        if (!EngineProxy.isConnected()) {
            if (EngineInstaller.isEngineInstalled(this)) {
                EngineProxy.addServiceAvailableCallback {
                    runOnUiThread { syncShopInfoFromEngine(shop, userId, showFailureToast) }
                }
                if (!App.ensureEngineConnection()) {
                    key?.let { pendingRecognitionKeys.remove(it) }
                }
            } else {
                key?.let { pendingRecognitionKeys.remove(it) }
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val shopInfo = EngineProxy.triggerShopIdExtract(packageName, userId)
            withContext(Dispatchers.Main) {
                key?.let { pendingRecognitionKeys.remove(it) }
                reportDetectedShopInfo(shop, packageName, userId, shopInfo, showFailureToast)
            }
        }
    }

    private fun reportDetectedShopInfo(
        sourceShop: Shop,
        packageName: String,
        userId: Int,
        shopInfo: top.niunaijun.blackbox.entity.pm.ShopInfo?,
        showFailureToast: Boolean = false
    ) {
        val shopName = shopInfo?.shopName?.takeIf(::isVerifiedExtractedShopName)
        val shopId = shopInfo?.shopId?.takeIf(::isVerifiedExtractedShopId)
        if (shopId == null || shopName == null) {
            if (showFailureToast) {
                toast("未获取到已验证店铺信息，请确认登录后下拉刷新")
            }
            return
        }
        viewModel.markLocalIdentityVerified(sourceShop, packageName, userId, true)
        buildShopPrepareKey(sourceShop, packageName)?.let { locallyRepairedShopKeys.remove(it) }
        if (sourceShop.hasVerifiedIdentity &&
            sourceShop.shopId == shopId &&
            sourceShop.shopName == shopName &&
            sourceShop.localVirtualUserId == userId
        ) {
            exportAndUploadLoginState(sourceShop, packageName, userId)
            return
        }
        viewModel.completePendingShop(
            sourceShop,
            Shop(
                id = sourceShop.id,
                shopName = shopName,
                shopId = shopId,
                platform = sourceShop.platform,
                remainingDays = sourceShop.remainingDays,
                autoRenew = sourceShop.autoRenew,
                packageName = packageName,
                cloneInstanceId = sourceShop.cloneInstanceId?.takeIf { it.isNotBlank() },
                localVirtualUserId = userId
            ),
            showMessage = sourceShop.isNew
        )
        exportAndUploadLoginState(sourceShop, packageName, userId)
    }

    private fun buildRecognitionKey(shop: Shop, userId: Int): String? {
        val packageName = resolveShopPackageName(shop) ?: return null
        return "${shop.id}:$packageName:$userId"
    }

    private fun buildLegacyCloneInstanceId(packageName: String, userId: Int): String {
        val appUserId = viewModel.getCurrentUserId()
        val source = "$appUserId:$packageName:$userId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "clone-$digest"
    }

    private fun syncCloneShopStateForCurrentPlatform() {
        if (!EngineProxy.isConnected()) {
            if (EngineInstaller.isEngineInstalled(this)) {
                EngineProxy.addServiceAvailableCallback {
                    runOnUiThread { syncCloneShopStateForCurrentPlatform() }
                }
                App.ensureEngineConnection()
            }
            return
        }

        val platformItem = viewModel.getSelectedPlatformItem() ?: return
        if (!platformItem.available) {
            return
        }
        val packageName = platformItem.packageName ?: return
        val allShops = viewModel.getAllShops().filter { shop ->
            resolveShopPackageName(shop) == packageName && shop.cloneInstanceId?.isNotBlank() == true
        }
        if (allShops.isEmpty()) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            allShops.forEach { shop ->
                val userId = findExistingUserIdForCloneInstance(shop, packageName)
                    ?: findUserIdForCloneInstance(shop, packageName)
                    ?: return@forEach
                val shopInfo = EngineProxy.triggerShopIdExtract(packageName, userId)
                withContext(Dispatchers.Main) {
                    reportDetectedShopInfo(shop, packageName, userId, shopInfo, showFailureToast = false)
                }
            }
        }
    }

    private fun ensureVirtualUserId(): Int {
        val users = EngineProxy.getUsers()
        return if (users.isEmpty()) {
            EngineProxy.createUser(0)?.id ?: 0
        } else {
            users.minOf { it.id }
        }
    }

    private fun createFreshVirtualUserId(): Int? {
        val usedIds = EngineProxy.getUsers().map { it.id }.toSet()
        val nextUserId = (0..9999).firstOrNull { it !in usedIds } ?: return null
        return EngineProxy.createUser(nextUserId)?.id
    }

    private fun findUserIdForCloneInstance(shop: Shop, packageName: String): Int? {
        val users = EngineProxy.getUsers()
        shop.cloneInstanceId?.takeIf { it.isNotBlank() }?.let { cloneInstanceId ->
            val currentUserId = viewModel.getCurrentUserId()
            EngineProxy.findCloneUserId(cloneInstanceId, packageName, currentUserId)?.let {
                return it
            }
            users.firstOrNull {
                EngineProxy.isCloneAuthorized(cloneInstanceId, packageName, currentUserId, it.id)
            }?.let {
                EngineProxy.bindCloneUser(cloneInstanceId, packageName, currentUserId, it.id)
                return it.id
            }
            shop.localVirtualUserId?.takeIf { it >= 0 }?.let { serverUserId ->
                if (users.any { it.id == serverUserId }) {
                    EngineProxy.bindCloneUser(cloneInstanceId, packageName, currentUserId, serverUserId)
                    return serverUserId
                }
            }
            users.firstOrNull { buildLegacyCloneInstanceId(packageName, it.id) == cloneInstanceId }?.let {
                EngineProxy.bindCloneUser(cloneInstanceId, packageName, currentUserId, it.id)
                return it.id
            }
            EngineProxy.ensureCloneUser(cloneInstanceId, packageName, currentUserId)?.let {
                return it
            }
        }
        shop.shopId.takeIf {
            it.isNotBlank() && it != "-" && !it.startsWith("NEW-") && !it.startsWith("phase13-")
        }?.let { realShopId ->
            users.firstOrNull { user ->
                EngineProxy.isInstalled(packageName, user.id) &&
                        EngineProxy.getShopInfo(packageName, user.id)?.shopId == realShopId
            }?.let {
                return it.id
            }
        }
        return users.firstOrNull { EngineProxy.isInstalled(packageName, it.id) }?.id
    }

    private fun findExistingUserIdForCloneInstance(shop: Shop, packageName: String): Int? {
        val users = EngineProxy.getUsers()
        shop.cloneInstanceId?.takeIf { it.isNotBlank() }?.let { cloneInstanceId ->
            val currentUserId = viewModel.getCurrentUserId()
            EngineProxy.findCloneUserId(cloneInstanceId, packageName, currentUserId)?.let {
                return it
            }
            users.firstOrNull {
                EngineProxy.isCloneAuthorized(cloneInstanceId, packageName, currentUserId, it.id)
            }?.let {
                return it.id
            }
            shop.localVirtualUserId?.takeIf { it >= 0 }?.let { serverUserId ->
                if (users.any { it.id == serverUserId }) {
                    return serverUserId
                }
            }
            users.firstOrNull { buildLegacyCloneInstanceId(packageName, it.id) == cloneInstanceId }?.let {
                return it.id
            }
        }
        shop.shopId.takeIf {
            it.isNotBlank() && it != "-" && !it.startsWith("NEW-") && !it.startsWith("phase13-")
        }?.let { realShopId ->
            users.firstOrNull { user ->
                EngineProxy.isInstalled(packageName, user.id) &&
                        EngineProxy.getShopInfo(packageName, user.id)?.shopId == realShopId
            }?.let {
                return it.id
            }
        }
        return null
    }

    private fun beginShopOperation(title: String, message: String): Boolean {
        if (shopOperationInProgress) {
            toast("店铺处理中，请稍候")
            return false
        }
        shopOperationInProgress = true
        showShopProgress(title, message)
        return true
    }

    private fun showShopProgress(title: String, message: String) {
        if (shopProgressDialog?.isShowing == true) {
            shopProgressDialog?.setTitle(title)
            shopProgressMessageView?.text = message
            setShopProgressDeterminate(false, 0)
            return
        }
        val content = layoutInflater.inflate(R.layout.dialog_shop_operation_progress, null)
        shopProgressBar = content.findViewById<ProgressBar>(R.id.shopOperationProgress).apply {
            isIndeterminate = true
            progress = 0
            max = 100
        }
        shopProgressMessageView = content.findViewById<TextView>(R.id.shopOperationMessage).apply {
            text = message
        }
        shopProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(content)
            .setCancelable(false)
            .create()
            .also {
                it.setCanceledOnTouchOutside(false)
                it.show()
            }
    }

    private fun updateShopProgress(message: String) {
        shopProgressMessageView?.text = message
    }

    private fun setShopProgressDeterminate(determinate: Boolean, progress: Int) {
        shopProgressBar?.let {
            it.isIndeterminate = !determinate
            if (determinate) {
                it.progress = progress.coerceIn(0, 100)
            }
        }
    }

    private fun finishShopOperation() {
        shopOperationInProgress = false
        shopProgressDialog?.dismiss()
        shopProgressDialog = null
        shopProgressMessageView = null
        shopProgressBar = null
    }

    private fun resolveShopPackageName(shop: Shop): String? {
        return shop.packageName?.takeIf { it.isNotBlank() }
    }

    private fun resolvePlatformName(platform: Platform): String {
        return PlatformRegistry.displayName(platform)
    }

    private fun shopDisplayName(shop: Shop): String {
        return shop.shopName.takeIf { it.isNotBlank() }
            ?: shop.shopId.takeIf { it.isNotBlank() && it != "-" }
            ?: resolvePlatformName(shop.platform)
    }

    private fun isVerifiedExtractedShopId(value: String): Boolean {
        return value.isNotBlank()
                && value != "-"
                && !value.startsWith("NEW-")
                && !value.startsWith("phase13-")
    }

    private fun isVerifiedExtractedShopName(value: String): Boolean {
        return value.isNotBlank()
                && !value.startsWith("新增店铺-[")
                && !value.startsWith("NEW-")
                && !value.startsWith("phase13-")
                && !value.startsWith("User[")
                && !value.startsWith("未知")
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ensureEngineReady(onUnavailable: (() -> Unit)? = null, action: () -> Unit) {
        if (EngineProxy.isConnected()) {
            action()
            return
        }

        if (!EngineInstaller.isEngineInstalled(this)) {
            onUnavailable?.invoke()
            toast("引擎未安装，请先完成引擎安装")
            startActivity(Intent(this, EngineInstallActivity::class.java))
            return
        }

        pendingEngineAction = action
        EngineProxy.addServiceAvailableCallback {
            runOnUiThread {
                val pendingAction = pendingEngineAction
                pendingEngineAction = null
                pendingAction?.invoke()
            }
        }

        val requested = App.ensureEngineConnection()
        if (requested) {
            toast("正在连接引擎，请稍候")
        } else if (!EngineProxy.isConnected()) {
            Log.w(TAG, "Engine installed but bind request failed")
            pendingEngineAction = null
            onUnavailable?.invoke()
            toast("引擎连接失败，请重试")
        }
    }

    private fun openAppMarket(packageName: String) {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        try {
            startActivity(marketIntent)
        } catch (e: Exception) {
            runCatching {
                startActivity(webIntent)
            }
        }
    }

    private fun showEditShopSheet(shop: Shop) {
        val showAutoRenew = !viewModel.isCurrentUserActiveSubscriber()
        val sheet = EditShopSheetFragment.newInstance(
            shop.shopName,
            shop.shopId,
            shop.autoRenew,
            shop.remark,
            showAutoRenew = showAutoRenew
        )
        sheet.setOnSaveListener { _, _, autoRenew, remark ->
            viewModel.updateShop(shop, autoRenew = autoRenew, remark = remark)
        }
        sheet.show(supportFragmentManager, "EditShop")
    }

    private fun handleAdvancedFeatureClick(shop: Shop, featureType: AdvancedFeatureType) {
        val feature = viewModel.getAdvancedFeature(featureType.code)
        val packageName = resolveShopPackageName(shop)
        if (feature == null || !feature.online || !feature.supportsPackage(packageName)) {
            toast(getString(R.string.advanced_feature_unavailable))
            return
        }
        val title = feature.title.takeIf { it.isNotBlank() }
            ?: getString(featureType.fallbackTitleResId)
        val cost = feature.monthlyComputeCost.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(getString(R.string.advanced_feature_confirm_message, cost))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                showAdvancedFeatureSheet(title, featureType.code)
            }
            .show()
    }

    private fun showAdvancedFeatureSheet(title: String, code: String) {
        AdvancedFeatureSheetFragment
            .newInstance(title, code)
            .show(supportFragmentManager, "AdvancedFeature-$code")
    }

    private fun showShopAuthorizationSheet(shop: Shop) {
        val userPhone = TokenManager.getInstance().getUser()?.phone.orEmpty()
        val authorizationShopId = shop.shopId
            .takeIf { it.isNotBlank() && it != "-" }
            ?: "system-${shop.id}"
        val authorizationUrl = PlatformRegistry.authorizationUrl(shop.platform)
        ShopAuthorizationSheetFragment
            .newInstance(
                title = getString(R.string.shop_authorization_title),
                streamUrl = BuildConfig.ZR_STREAM_URL,
                controlUrl = BuildConfig.ZR_CONTROL_URL,
                shopName = shopDisplayName(shop),
                userPhone = userPhone,
                shopId = authorizationShopId,
                authorizationUrl = authorizationUrl
            )
            .show(supportFragmentManager, "ShopAuthorization")
    }

    private fun handleAutoRenewClick(shop: Shop) {
        if (viewModel.isCurrentUserActiveSubscriber()) {
            return
        }
        if (shop.autoRenew) {
            viewModel.updateShop(shop, autoRenew = false)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("开启自动续时")
            .setMessage("该店铺有效期为0时自动扣除1点算力，确认要开启吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                viewModel.updateShop(shop, autoRenew = true)
            }
            .show()
    }

    private fun showDeleteShopSheet(shop: Shop) {
        val sheet = DeleteShopSheetFragment()
        sheet.setOnDeleteListener {
            deleteShopAndClone(shop)
        }
        sheet.show(supportFragmentManager, "DeleteShop")
    }

    private fun confirmRepairShop(shop: Shop) {
        MaterialAlertDialogBuilder(this)
            .setTitle("修复店铺")
            .setMessage("本操作会重置该店铺的本机数据，并尝试使用服务器备份登录态恢复。仅在店铺打不开或登录态异常时使用，是否继续？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                repairShopLocalData(shop)
            }
            .show()
    }

    private fun repairShopLocalData(shop: Shop) {
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        val packageName = resolveShopPackageName(shop)
        if (cloneInstanceId == null || packageName.isNullOrBlank()) {
            toast("该店铺暂无可修复的本地数据")
            return
        }
        if (!beginShopOperation("修复店铺", "正在连接引擎，请稍候…")) {
            return
        }
        ensureEngineReady(onUnavailable = { finishShopOperation() }) {
            val userId = findExistingUserIdForCloneInstance(shop, packageName)
                ?: findUserIdForCloneInstance(shop, packageName)
            if (userId == null) {
                clearPreparedShopEnvironment(shop, packageName)
                finishLocalRepair(shop, packageName, null, LoginStateRestoreResult.NOT_AVAILABLE)
                return@ensureEngineReady
            }
            try {
                updateShopProgress("正在清除店铺本地数据…")
                EngineProxy.stopPackage(packageName, userId)
                EngineProxy.clearClonePackageData(cloneInstanceId, packageName, viewModel.getCurrentUserId(), userId)
                clearPreparedShopEnvironment(shop, packageName)
                lifecycleScope.launch(Dispatchers.IO) {
                    val restoreResult = restoreLoginStateFromServer(
                        shop = shop,
                        packageName = packageName,
                        userId = userId,
                        showProgress = true,
                        reason = "repair"
                    )
                    withContext(Dispatchers.Main) {
                        finishLocalRepair(shop, packageName, userId, restoreResult)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to repair local shop data ${shop.id}", e)
                finishShopOperation()
                toast("修复店铺失败: ${e.message}")
            }
        }
    }

    private fun finishLocalRepair(
        shop: Shop,
        packageName: String,
        userId: Int?,
        restoreResult: LoginStateRestoreResult = LoginStateRestoreResult.NOT_AVAILABLE
    ) {
        runOnUiThread {
            updateShopProgress("修复完成")
            clearPreparedShopEnvironment(shop, packageName)
            invalidatePreparedPlatformEnvironment(packageName)
            (userId ?: shop.localVirtualUserId)?.let { localUserId ->
                viewModel.markLocalIdentityVerified(shop, packageName, localUserId, false)
            }
            buildShopPrepareKey(shop, packageName)?.let { locallyRepairedShopKeys.add(it) }
            buildShopPrepareKey(shop, packageName)?.let { key ->
                restoredLoginStateKeys.removeAll { it.startsWith("$key:") }
                uploadedLoginStateKeys.removeAll { it.startsWith("$key:") }
            }
            if (pendingPlatformEnvironmentPackage == packageName) {
                pendingPlatformEnvironmentPackage = null
            }
            if (restoreEnvironmentCheckPackage == packageName) {
                restoreEnvironmentCheckPackage = null
            }
            finishShopOperation()
            val message = when (restoreResult) {
                LoginStateRestoreResult.RESTORED -> "店铺已修复，请重新点击店铺打开"
                LoginStateRestoreResult.NOT_AVAILABLE -> "本机店铺数据已清除，暂无服务器备份，请重新登录店铺"
                LoginStateRestoreResult.FAILED -> "服务器备份恢复失败，请稍后重试或重新登录店铺"
            }
            toast(message)
        }
    }

    private fun clearPreparedShopEnvironment(shop: Shop, packageName: String) {
        buildShopPrepareKey(shop, packageName)?.let { prepareKey ->
            preparingShopKeys.remove(prepareKey)
            preparedShopKeys.remove(prepareKey)
            preparedShopUsers.remove(prepareKey)
            preparingShopCallbacks.remove(prepareKey)
        }
    }

    private fun invalidatePreparedPlatformEnvironment(packageName: String?) {
        val normalizedPackageName = packageName?.takeIf { it.isNotBlank() } ?: return
        preparedPlatformEnvironmentPackages.remove(normalizedPackageName)
        if (defaultPlatformEnvironmentPreparedPackage == normalizedPackageName) {
            defaultPlatformEnvironmentPreparedPackage = null
        }
        if (viewModel.getSelectedPlatformItem()?.packageName == normalizedPackageName) {
            shouldPrepareDefaultPlatformEnvironment = true
        }
    }

    private fun deleteShopAndClone(shop: Shop) {
        val cloneInstanceId = shop.cloneInstanceId?.takeIf { it.isNotBlank() }
        val packageName = resolveShopPackageName(shop)
        if (cloneInstanceId == null || packageName.isNullOrBlank()) {
            viewModel.deleteShop(shop)
            return
        }
        ensureEngineReady {
            val userId = findUserIdForCloneInstance(shop, packageName)
            try {
                if (userId != null) {
                    EngineProxy.clearClonePackageData(cloneInstanceId, packageName, viewModel.getCurrentUserId(), userId)
                    EngineProxy.uninstallPackageAsUser(packageName, userId)
                }
                EngineProxy.clearCloneUser(cloneInstanceId, packageName, viewModel.getCurrentUserId())
                if (userId != null) {
                    EngineProxy.deleteUser(userId)
                }
                invalidatePreparedPlatformEnvironment(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear clone before deleting shop ${shop.id}", e)
            } finally {
                viewModel.deleteShop(shop)
            }
        }
    }

    private fun checkForEngineUpgrade(force: Boolean = false) {
        if (engineUpgradeCheckInFlight) {
            return
        }
        engineUpgradeCheckInFlight = true
        lifecycleScope.launch {
            try {
                val upgradeInfo = withContext(Dispatchers.IO) {
                    EngineVersionChecker.checkForUpgrade(this@HomeActivity, force)
                } ?: return@launch

                if (upgradeInfo.downloadUrl.isBlank()) {
                    val result = withContext(Dispatchers.IO) {
                        EngineInstaller.installFromAssets(this@HomeActivity)
                    }
                    result.onFailure {
                        Log.e(TAG, "Built-in engine install failed: ${it.message}", it)
                    }
                    return@launch
                }
                if (EngineVersionChecker.isVersionSkipped(this@HomeActivity, upgradeInfo.versionCode)) {
                    return@launch
                }
                showEngineUpgradeDialog(upgradeInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking engine upgrade: ${e.message}", e)
            } finally {
                engineUpgradeCheckInFlight = false
            }
        }
    }

    private fun showEngineUpgradeDialog(upgradeInfo: EngineVersionChecker.UpgradeInfo) {
        if (isFinishing || isDestroyed) {
            return
        }
        if (supportFragmentManager.findFragmentByTag("EngineUpgradeDialog") != null) {
            return
        }
        val currentVersion = EngineInstaller.getInstalledEngineVersion(this)
        var dialog: EngineUpgradeDialog? = null
        dialog = EngineUpgradeDialog.show(
            supportFragmentManager,
            upgradeInfo,
            currentVersion,
            object : EngineUpgradeDialog.UpgradeDialogListener {
                override fun onUpgradeNow(versionCode: Int) {
                    if (!EngineInstaller.canInstallUnknownApps(this@HomeActivity)) {
                        toast("请先允许账号管家安装未知应用")
                        EngineInstaller.openInstallPermissionSettings(this@HomeActivity)
                        dialog?.showError("请授权后重新点击升级")
                        return
                    }
                    lifecycleScope.launch {
                        dialog?.showInstalling()
                        val result = withContext(Dispatchers.IO) {
                            EngineUpgradeManager.downloadAndInstall(this@HomeActivity, upgradeInfo)
                        }
                        result.fold(
                            onSuccess = {
                                toast("已开始安装引擎，请在系统弹窗中确认")
                                dialog?.dismissSafely()
                            },
                            onFailure = { e ->
                                dialog?.showError(e.message ?: "引擎升级失败")
                            }
                        )
                    }
                }

                override fun onUpgradeLater(versionCode: Int) {
                    EngineVersionChecker.skipVersion(this@HomeActivity, versionCode)
                }

                override fun onExitApp() {
                    finish()
                }
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingRecognitionKeys.clear()
        preparingShopKeys.clear()
        preparedShopKeys.clear()
        preparedShopUsers.clear()
        preparingShopCallbacks.clear()
        locallyRepairedShopKeys.clear()
        restoredLoginStateKeys.clear()
        uploadedLoginStateKeys.clear()
        stopTickerScroll()
        pendingPlatformEnvironmentPackage = null
        restoreEnvironmentCheckPackage = null
        waitingForEngineConnectionToRestore = false
        finishShopOperation()
        super.onDestroy()
    }
}
