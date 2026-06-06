package com.zhirang.zhanghaoguanjia.view.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.Shop
import com.zhirang.zhanghaoguanjia.bean.dto.AnnouncementDto
import com.zhirang.zhanghaoguanjia.databinding.ActivityHomeBinding
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import com.zhirang.zhanghaoguanjia.engine.EngineUpgradeManager
import com.zhirang.zhanghaoguanjia.engine.EngineVersionChecker
import com.zhirang.zhanghaoguanjia.bean.Platform
import com.zhirang.zhanghaoguanjia.util.AvatarImageLoader
import com.zhirang.zhanghaoguanjia.util.inflate
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry
import com.zhirang.zhanghaoguanjia.util.toast
import com.zhirang.zhanghaoguanjia.view.dialog.DeleteShopSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.EditShopSheetFragment
import com.zhirang.zhanghaoguanjia.view.dialog.EngineUpgradeDialog
import com.zhirang.zhanghaoguanjia.view.logs.LogsActivity
import com.zhirang.zhanghaoguanjia.view.profile.ProfileActivity
import com.zhirang.zhanghaoguanjia.view.splash.EngineInstallActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class HomeActivity : AppCompatActivity() {

    private val viewBinding: ActivityHomeBinding by inflate()
    private lateinit var viewModel: HomeViewModel
    private lateinit var platformAdapter: PlatformSidebarAdapter
    private lateinit var shopAdapter: ShopListAdapter
    private lateinit var swipeHelper: ShopSwipeHelper
    private lateinit var itemTouchHelper: ItemTouchHelper
    private val handler = Handler(Looper.getMainLooper())
    private val pendingRecognitionKeys = mutableSetOf<String>()
    private var shouldRetryPendingOnNextList = false
    private var shouldSyncCloneShopsOnNextList = false
    private var pendingEngineAction: (() -> Unit)? = null
    private var shownAnnouncementId: Long? = null
    private val promptedCloneSwitchKeys = mutableSetOf<String>()
    private var engineUpgradeCheckInFlight = false

    companion object {
        private const val TAG = "HomeActivity"
        private const val SHOP_RECOGNITION_MAX_ATTEMPTS = 12
        private const val SHOP_RECOGNITION_INITIAL_DELAY_MS = 2000L
        private const val SHOP_RECOGNITION_INTERVAL_MS = 5000L

        fun start(context: Context) {
            context.startActivity(Intent(context, HomeActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        setContentView(viewBinding.root)

        initViewModel()
        initPlatformSidebar()
        initShopList()
        initSearch()
        initClickListeners()
        observeData()
        App.ensureEngineConnection()
        checkForEngineUpgrade(force = true)
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
    }

    private fun initPlatformSidebar() {
        platformAdapter = PlatformSidebarAdapter { _, platform ->
            if (!platform.available) {
                toast("该平台的店铺管理功能暂不支持")
                return@PlatformSidebarAdapter
            }
            viewModel.selectPlatform(platform.platform)
            updateShopList()
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
                collapseSwipe()
            },
            onAutoRenewClick = { _, shop ->
                viewModel.updateShop(shop, autoRenew = !shop.autoRenew)
                collapseSwipe()
            },
            onDeleteClick = { _, shop ->
                showDeleteShopSheet(shop)
                collapseSwipe()
            }
        )

        viewBinding.rvShops.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = shopAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        collapseSwipe()
                    }
                }
            })
        }
        viewBinding.swipeRefreshShops.setOnRefreshListener {
            collapseSwipe()
            shouldSyncCloneShopsOnNextList = true
            viewModel.loadShops()
        }

        swipeHelper = ShopSwipeHelper(shopAdapter)
        viewBinding.rvShops.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked != MotionEvent.ACTION_DOWN) return false

                val child = rv.findChildViewUnder(e.x, e.y) ?: return false
                val position = rv.getChildAdapterPosition(child)
                val action = swipeHelper.hitTestAction(rv, child, e.x, e.y)
                if (action != null && position != RecyclerView.NO_POSITION) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    handleSwipeAction(position, action)
                    return true
                }
                if (swipeHelper.getExpandedPosition() != RecyclerView.NO_POSITION &&
                    position != swipeHelper.getExpandedPosition()) {
                    collapseSwipe()
                }
                return false
            }
        })
        itemTouchHelper = ItemTouchHelper(swipeHelper)
        itemTouchHelper.attachToRecyclerView(viewBinding.rvShops)
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

    private fun initClickListeners() {
        // 个人中心入口（左侧栏底部）
        viewBinding.btnMyProfile.setOnClickListener {
            ProfileActivity.start(this)
        }

        // 交易日志按钮
        viewBinding.btnLogs.setOnClickListener {
            LogsActivity.start(this)
        }

        viewBinding.btnAddShop.setOnClickListener {
            addShopForSelectedPlatform()
        }
    }

    private fun observeData() {
        viewModel.computeBalanceLiveData.observe(this) { balance ->
            viewBinding.tvComputeBalance.text = balance.toString()
        }

        viewModel.phoneNumberLiveData.observe(this) { phone ->
            viewBinding.tvPhoneNumber.text = phone
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

        viewModel.shopsLiveData.observe(this) {
            viewBinding.swipeRefreshShops.isRefreshing = false
            updateShopList()
            if (shouldRetryPendingOnNextList) {
                shouldRetryPendingOnNextList = false
                handler.postDelayed({ retryPendingShopRecognition() }, 800)
            }
            if (shouldSyncCloneShopsOnNextList) {
                shouldSyncCloneShopsOnNextList = false
                handler.postDelayed({ syncCloneShopStateForCurrentPlatform() }, 500)
            }
        }

        viewModel.platformsLiveData.observe(this) { platforms ->
            platformAdapter.submitList(platforms)
            updateShopList()
        }

        // 更新平台店铺数量
        viewModel.platformShopCounts.observe(this) { counts ->
            platformAdapter.setShopCounts(counts)
        }

        viewModel.loadErrorLiveData.observe(this) { errorMessage ->
            viewBinding.swipeRefreshShops.isRefreshing = false
            errorMessage?.let {
                toast(it)
            }
        }

        viewModel.operationMessageLiveData.observe(this) { message ->
            message?.let {
                toast(it)
            }
        }

        viewModel.latestAnnouncementLiveData.observe(this) { announcement ->
            announcement?.let { showAnnouncementDialog(it) }
        }

        // Initial load
        viewModel.loadShops()
        loadAnnouncementForOpen()
    }

    private fun loadAnnouncementForOpen() {
        viewModel.loadLatestAnnouncement()
    }

    private fun showAnnouncementDialog(announcement: AnnouncementDto) {
        if (shownAnnouncementId == announcement.id || isFinishing || isDestroyed) {
            return
        }
        shownAnnouncementId = announcement.id
        MaterialAlertDialogBuilder(this)
            .setTitle(announcement.title)
            .setMessage(announcement.content)
            .setPositiveButton(R.string.announcement_dialog_button, null)
            .show()
    }

    private fun updateShopList() {
        val filtered = viewModel.getFilteredShops()
        shopAdapter.submitList(filtered)
    }

    private fun onShopClick(shop: Shop) {
        val packageName = resolveShopPackageName(shop)
        if (packageName.isNullOrEmpty()) {
            toast("该店铺暂无关联应用")
            return
        }

        if (shop.remainingDays <= 0) {
            renewExpiredShopBeforeOpen(shop)
            return
        }
        openPlatformForShop(shop)
    }

    private fun renewExpiredShopBeforeOpen(shop: Shop) {
        if (shop.autoRenew) {
            viewModel.renewShop(shop) { renewedShop ->
                openPlatformForShop(renewedShop)
            }
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("店铺已到期")
            .setMessage("是否扣减 1 点算力为该店铺续期 30 天？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                viewModel.renewShop(shop) { renewedShop ->
                    openPlatformForShop(renewedShop)
                }
            }
            .show()
    }

    private fun openPlatformForShop(shop: Shop) {
        ensureEngineReady {
            openPlatformForShopWithEngine(shop)
        }
    }

    private fun openPlatformForShopWithEngine(shop: Shop) {
        val packageName = resolveShopPackageName(shop)
        if (packageName.isNullOrEmpty()) {
            toast("该店铺暂无关联应用")
            return
        }
        val platformName = resolvePlatformName(shop.platform)

        if (!EngineProxy.isConnected()) {
            retryOpenAfterEngineReconnect(shop)
            return
        }

        var targetUserId = findUserIdForCloneInstance(shop, packageName)
        if (targetUserId != null && EngineProxy.isInstalled(packageName, targetUserId)) {
            if (!launchVirtualApp(packageName, targetUserId, platformName)) {
                return
            }
            if (shop.isNew) {
                reportCloneCreated(shop, targetUserId)
            }
            schedulePendingShopRecognition(shop, targetUserId, showFailureToast = shop.isNew)
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
            toast("检测到您尚未安装 ${platformName}，请前往应用市场下载安装后重试")
            openAppMarket(packageName)
            return
        }

        var createdFreshUser = false
        val installUserId = targetUserId ?: if (shop.isNew) {
            val freshUserId = createFreshVirtualUserId() ?: run {
                toast("创建分身用户失败，请重试")
                return
            }
            createdFreshUser = true
            freshUserId
        } else {
            ensureVirtualUserId()
        }

        toast("正在为您创建 ${platformName} 分身，请稍候…")

        try {
            val result = EngineProxy.installPackageAsUser(packageName, installUserId)
            if (result.success || EngineProxy.isInstalled(packageName, installUserId)) {
                toast("${platformName} 分身创建成功")
                if (shop.isNew) {
                    reportCloneCreated(shop, installUserId)
                }
                if (launchVirtualApp(packageName, installUserId, platformName)) {
                    schedulePendingShopRecognition(shop, installUserId, showFailureToast = shop.isNew)
                }
            } else {
                if (!EngineProxy.isConnected()) {
                    retryOpenAfterEngineReconnect(shop)
                } else {
                    if (createdFreshUser) {
                        EngineProxy.deleteUser(installUserId)
                    }
                    toast("分身创建失败: ${result.msg}")
                }
            }
        } catch (e: Exception) {
            if (createdFreshUser) {
                EngineProxy.deleteUser(installUserId)
            }
            toast("分身创建异常: ${e.message}")
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
            toast("正在重新连接引擎，请稍候")
        } else {
            pendingEngineAction = null
            toast("引擎连接失败，请重试")
        }
    }

    private fun launchVirtualApp(packageName: String, userId: Int, platformName: String): Boolean {
        return try {
            val launchIntent = EngineProxy.getLaunchIntent(packageName, userId)
            if (launchIntent == null) {
                Log.w(TAG, "No virtual launch intent for $packageName user=$userId")
                toast("启动失败，请重新创建分身")
                return false
            }
            toast("正在打开 ${platformName}…")
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch virtual app $packageName user=$userId", e)
            toast("启动失败: ${e.message}")
            false
        }
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
        viewModel.createPendingShop(platformItem)
    }

    private fun reportCloneCreated(pendingShop: Shop, userId: Int) {
        val packageName = resolveShopPackageName(pendingShop) ?: return
        val cloneInstanceId = buildCloneInstanceId(pendingShop, packageName, userId)
        if (cloneInstanceId == pendingShop.cloneInstanceId) {
            return
        }
        viewModel.reportShop(
            pendingShop.copy(
                cloneInstanceId = cloneInstanceId,
                shopName = pendingShop.shopName.ifBlank { "User[0]-未知" },
                shopId = pendingShop.shopId
            ),
            showMessage = false
        )
    }

    private fun schedulePendingShopRecognition(
        pendingShop: Shop,
        userId: Int,
        showFailureToast: Boolean = false
    ) {
        if (!EngineProxy.isConnected()) {
            return
        }
        val key = buildRecognitionKey(pendingShop, userId) ?: return
        if (!pendingRecognitionKeys.add(key)) {
            return
        }
        pollPendingShopRecognition(pendingShop, userId, attempt = 1, showFailureToast = showFailureToast)
    }

    private fun pollPendingShopRecognition(
        pendingShop: Shop,
        userId: Int,
        attempt: Int,
        showFailureToast: Boolean = false
    ) {
        val packageName = resolveShopPackageName(pendingShop) ?: run {
            buildRecognitionKey(pendingShop, userId)?.let { pendingRecognitionKeys.remove(it) }
            return
        }
        val key = buildRecognitionKey(pendingShop, userId)
        EngineProxy.triggerShopIdExtract(packageName, userId)
        val delayMs = if (attempt == 1) SHOP_RECOGNITION_INITIAL_DELAY_MS else SHOP_RECOGNITION_INTERVAL_MS
        handler.postDelayed({
            reportExtractedShopForPending(pendingShop, userId, attempt, showFailureToast)
            if (!EngineProxy.isConnected()) {
                key?.let { pendingRecognitionKeys.remove(it) }
            }
        }, delayMs)
    }

    private fun reportExtractedShopForPending(
        pendingShop: Shop,
        userId: Int,
        attempt: Int,
        showFailureToast: Boolean = false
    ) {
        val packageName = resolveShopPackageName(pendingShop) ?: return
        val key = buildRecognitionKey(pendingShop, userId)
        val shopInfo = EngineProxy.getShopInfo(packageName, userId)
        val shopName = shopInfo?.shopName?.takeIf { it.isNotBlank() }
        val shopId = shopInfo?.shopId?.takeIf { it.isNotBlank() }
        if (shopId == null) {
            if (attempt < SHOP_RECOGNITION_MAX_ATTEMPTS) {
                pollPendingShopRecognition(
                    pendingShop,
                    userId,
                    attempt = attempt + 1,
                    showFailureToast = showFailureToast
                )
            } else {
                key?.let { pendingRecognitionKeys.remove(it) }
                if (showFailureToast) {
                    toast("未获取到店铺信息，请确认已在京东秒送登录后返回重试")
                }
            }
            return
        }
        val platform = pendingShop.platform
        val finalShopName = shopName ?: pendingShop.shopName
            .takeUnless { it.contains("未知") || it.startsWith("User[") }
            ?: "${resolvePlatformName(platform)}-$shopId"

        viewModel.completePendingShop(
            pendingShop,
            Shop(
                id = 0,
                shopName = finalShopName,
                shopId = shopId,
                platform = platform,
                remainingDays = pendingShop.remainingDays,
                autoRenew = pendingShop.autoRenew,
                packageName = packageName,
                cloneInstanceId = buildCloneInstanceId(pendingShop, packageName, userId)
            ),
            showMessage = pendingShop.isNew
        )
        key?.let { pendingRecognitionKeys.remove(it) }
    }

    private fun buildRecognitionKey(shop: Shop, userId: Int): String? {
        val packageName = resolveShopPackageName(shop) ?: return null
        return "${shop.id}:$packageName:$userId"
    }

    private fun buildCloneInstanceId(@Suppress("UNUSED_PARAMETER") shop: Shop, packageName: String, userId: Int): String {
        val appUserId = viewModel.getCurrentUserId()
        val source = "$appUserId:$packageName:$userId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "clone-$digest"
    }

    private fun retryPendingShopRecognition() {
        if (!EngineProxy.isConnected()) {
            if (EngineInstaller.isEngineInstalled(this)) {
                EngineProxy.addServiceAvailableCallback {
                    runOnUiThread { retryPendingShopRecognition() }
                }
                App.ensureEngineConnection()
            }
            return
        }

        shopAdapter.getShops()
            .filter { it.isNew && !resolveShopPackageName(it).isNullOrBlank() }
            .forEach { pendingShop ->
                val packageName = resolveShopPackageName(pendingShop) ?: return@forEach
                val userId = findUserIdForCloneInstance(pendingShop, packageName) ?: return@forEach
                schedulePendingShopRecognition(pendingShop, userId)
            }
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
        val cloneInfos = EngineProxy.refreshShopInfoByPlatform("", packageName)
        if (cloneInfos.isEmpty()) {
            return
        }

        val allShops = viewModel.getAllShops()
        cloneInfos.forEach { shopInfo ->
            val detectedShopId = shopInfo.shopId?.takeIf { it.isNotBlank() } ?: return@forEach
            val detectedPlatform = platformItem.platform
            val cloneOwner = allShops.firstOrNull { shop ->
                resolveShopPackageName(shop) == packageName &&
                        findUserIdForCloneInstance(shop, packageName) == shopInfo.userId
            }
            if (cloneOwner == null || cloneOwner.isNew) {
                return@forEach
            }
            if (cloneOwner.shopId == detectedShopId) {
                return@forEach
            }
            val alreadyExists = allShops.any {
                resolveShopPackageName(it) == packageName &&
                        (it.shopId == detectedShopId || it.shopId == buildPendingSwitchShopId(packageName, detectedShopId))
            }
            if (alreadyExists) {
                return@forEach
            }
            val promptKey = "${cloneOwner.id}:${buildPendingSwitchShopId(packageName, detectedShopId)}"
            if (!promptedCloneSwitchKeys.add(promptKey)) {
                return@forEach
            }
            val detectedShopName = shopInfo.shopName?.takeIf { it.isNotBlank() }
                ?: "${resolvePlatformName(detectedPlatform)}-$detectedShopId"
            showCloneShopSwitchDialog(
                sourceShop = cloneOwner,
                detectedShop = Shop(
                    id = 0,
                    shopName = detectedShopName,
                    shopId = detectedShopId,
                    platform = detectedPlatform,
                    remainingDays = 30,
                    autoRenew = false,
                    packageName = packageName,
                    cloneInstanceId = null
                ),
                promptKey = promptKey
            )
        }
    }

    private fun showCloneShopSwitchDialog(sourceShop: Shop, detectedShop: Shop, promptKey: String) {
        if (isFinishing || isDestroyed) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("检测到店铺切换")
            .setMessage("当前分身「${sourceShop.shopName}」已切换到「${detectedShop.shopName}」。确认后将扣减 1 点算力，并创建一个新的干净分身；当前分身不会被改动。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                viewModel.createPendingShopFromDetectedSwitch(detectedShop) { pendingShop ->
                    prepareCleanCloneForPendingShop(pendingShop)
                }
            }
            .setOnDismissListener { promptedCloneSwitchKeys.remove(promptKey) }
            .show()
    }

    private fun prepareCleanCloneForPendingShop(pendingShop: Shop) {
        ensureEngineReady {
            val packageName = resolveShopPackageName(pendingShop)
            if (packageName.isNullOrBlank()) {
                toast("新店铺暂无关联应用")
                return@ensureEngineReady
            }
            val platformName = resolvePlatformName(pendingShop.platform)
            val isHostInstalled = try {
                packageManager.getPackageInfo(packageName, 0) != null
            } catch (e: Exception) {
                false
            }
            if (!isHostInstalled) {
                toast("新店铺卡片已添加，请先安装 ${platformName} 后再打开")
                openAppMarket(packageName)
                return@ensureEngineReady
            }

            val existingUserId = findUserIdForCloneInstance(pendingShop, packageName)
            val createdFreshUser = existingUserId == null
            val userId = existingUserId ?: createFreshVirtualUserId() ?: run {
                toast("创建分身用户失败，请稍后重试")
                return@ensureEngineReady
            }
            if (EngineProxy.isInstalled(packageName, userId)) {
                if (existingUserId == null) {
                    reportCloneCreated(pendingShop, userId)
                }
                toast("新店铺分身已准备好，请点击 new 店铺登录并切换店铺")
                return@ensureEngineReady
            }
            toast("正在为新店铺创建干净分身，请稍候…")
            try {
                val result = EngineProxy.installPackageAsUser(packageName, userId)
                if (result.success || EngineProxy.isInstalled(packageName, userId)) {
                    reportCloneCreated(pendingShop, userId)
                    toast("新店铺分身已准备好，请点击 new 店铺登录并切换店铺")
                } else {
                    if (createdFreshUser) {
                        EngineProxy.deleteUser(userId)
                    }
                    toast("新分身创建失败: ${result.msg}")
                }
            } catch (e: Exception) {
                if (createdFreshUser) {
                    EngineProxy.deleteUser(userId)
                }
                toast("新分身创建异常: ${e.message}")
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
            users.firstOrNull { buildCloneInstanceId(shop, packageName, it.id) == cloneInstanceId }?.let {
                return it.id
            }
        }
        shop.shopId.takeIf { it.isNotBlank() && it != "-" && !it.startsWith("NEW-") }?.let { realShopId ->
            users.firstOrNull { user ->
                EngineProxy.isInstalled(packageName, user.id) &&
                        EngineProxy.getShopInfo(packageName, user.id)?.shopId == realShopId
            }?.let {
                return it.id
            }
        }
        return users.firstOrNull { EngineProxy.isInstalled(packageName, it.id) }?.id
    }

    private fun resolveShopPackageName(shop: Shop): String? {
        return shop.packageName?.takeIf { it.isNotBlank() }
    }

    private fun resolvePlatformName(platform: Platform): String {
        return PlatformRegistry.displayName(platform)
    }

    private fun buildPendingSwitchShopId(packageName: String, shopId: String): String {
        return "NEW-SWITCH-${sha256(packageName.trim()).take(10)}-${sha256(shopId.trim()).take(16)}"
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ensureEngineReady(action: () -> Unit) {
        if (EngineProxy.isConnected()) {
            action()
            return
        }

        if (!EngineInstaller.isEngineInstalled(this)) {
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
        val sheet = EditShopSheetFragment.newInstance(shop.shopName, shop.autoRenew)
        sheet.setOnSaveListener { name, autoRenew ->
            viewModel.updateShop(shop, name, autoRenew)
        }
        sheet.show(supportFragmentManager, "EditShop")
    }

    private fun showDeleteShopSheet(shop: Shop) {
        val sheet = DeleteShopSheetFragment()
        sheet.setOnDeleteListener {
            viewModel.deleteShop(shop)
        }
        sheet.show(supportFragmentManager, "DeleteShop")
    }

    private fun collapseSwipe() {
        swipeHelper.collapseExpandedItem(viewBinding.rvShops)
    }

    private fun handleSwipeAction(position: Int, action: ShopSwipeHelper.Action) {
        val shop = shopAdapter.getShops().getOrNull(position) ?: return
        when (action) {
            ShopSwipeHelper.Action.EDIT -> showEditShopSheet(shop)
            ShopSwipeHelper.Action.AUTO_RENEW -> viewModel.updateShop(shop, autoRenew = !shop.autoRenew)
            ShopSwipeHelper.Action.DELETE -> showDeleteShopSheet(shop)
        }
        collapseSwipe()
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
                    withContext(Dispatchers.IO) {
                        EngineInstaller.installFromAssets(this@HomeActivity)
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

    override fun onResume() {
        super.onResume()
        shouldRetryPendingOnNextList = true
        shouldSyncCloneShopsOnNextList = true
        App.ensureEngineConnection()
        viewModel.refreshUserInfo()
        viewModel.loadShops()
        checkForEngineUpgrade()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingRecognitionKeys.clear()
        promptedCloneSwitchKeys.clear()
        super.onDestroy()
    }
}
