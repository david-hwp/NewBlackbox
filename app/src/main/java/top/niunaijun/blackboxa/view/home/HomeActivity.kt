package top.niunaijun.blackboxa.view.home

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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.bean.Shop
import top.niunaijun.blackboxa.bean.dto.AnnouncementDto
import top.niunaijun.blackboxa.databinding.ActivityHomeBinding
import top.niunaijun.blackboxa.engine.EngineInstaller
import top.niunaijun.blackboxa.engine.EngineProxy
import top.niunaijun.blackboxa.bean.Platform
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.dialog.DeleteShopSheetFragment
import top.niunaijun.blackboxa.view.dialog.EditShopSheetFragment
import top.niunaijun.blackboxa.view.logs.LogsActivity
import top.niunaijun.blackboxa.view.profile.ProfileActivity
import top.niunaijun.blackboxa.view.splash.EngineInstallActivity
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
            viewBinding.tvHeaderInitial.text = username.firstOrNull()?.toString() ?: "我"
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
        val packageName = shop.packageName
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
        val packageName = shop.packageName
        if (packageName.isNullOrEmpty()) {
            toast("该店铺暂无关联应用")
            return
        }
        val platformName = shop.platform.displayName

        val targetUserId = ensureVirtualUserId()
        if (!EngineProxy.isConnected()) {
            retryOpenAfterEngineReconnect(shop)
            return
        }

        // 1. 检查 BlackBox 中是否已安装该应用的分身
        if (EngineProxy.isInstalled(packageName, targetUserId)) {
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

        // 4. 宿主机已安装，自动创建分身
        toast("正在为您创建 ${platformName} 分身，请稍候…")

        try {
            // 安装分身（从系统已安装应用）
            val result = EngineProxy.installPackageAsUser(packageName, targetUserId)
            if (result.success) {
                toast("${platformName} 分身创建成功")
                if (launchVirtualApp(packageName, targetUserId, platformName)) {
                    if (shop.isNew) {
                        reportCloneCreated(shop, targetUserId)
                    }
                    schedulePendingShopRecognition(shop, targetUserId, showFailureToast = shop.isNew)
                }
            } else {
                if (!EngineProxy.isConnected()) {
                    retryOpenAfterEngineReconnect(shop)
                } else {
                    toast("分身创建失败: ${result.msg}")
                }
            }
        } catch (e: Exception) {
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
        val packageName = pendingShop.packageName ?: return
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
        val packageName = pendingShop.packageName ?: run {
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
        val packageName = pendingShop.packageName ?: return
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
        val platform = shopInfo.platform.takeIf { it.isNotBlank() }?.let {
            top.niunaijun.blackboxa.bean.Platform.fromId(it)
        } ?: pendingShop.platform
        val finalShopName = shopName ?: pendingShop.shopName
            .takeUnless { it.contains("未知") || it.startsWith("User[") }
            ?: "${platform.displayName}-$shopId"

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
        val packageName = shop.packageName ?: return null
        return "${shop.id}:$packageName:$userId"
    }

    private fun buildCloneInstanceId(shop: Shop, packageName: String, userId: Int): String {
        val appUserId = viewModel.getCurrentUserId()
        val source = "$appUserId:${shop.platform.id}:$packageName:$userId"
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

        val userId = ensureVirtualUserId()
        shopAdapter.getShops()
            .filter { it.isNew && !it.packageName.isNullOrBlank() }
            .forEach { pendingShop ->
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
        val cloneInfos = EngineProxy.refreshShopInfoByPlatform(platformItem.platform.id, packageName)
        if (cloneInfos.isEmpty()) {
            return
        }

        val allShops = viewModel.getAllShops()
        cloneInfos.forEach { shopInfo ->
            val detectedShopId = shopInfo.shopId?.takeIf { it.isNotBlank() } ?: return@forEach
            val detectedPlatform = shopInfo.platform?.takeIf { it.isNotBlank() }?.let { Platform.fromId(it) }
                ?: platformItem.platform
            val cloneOwner = allShops.firstOrNull { shop ->
                shop.packageName == packageName &&
                        shop.cloneInstanceId == buildCloneInstanceId(shop, packageName, shopInfo.userId)
            }
            if (cloneOwner == null || cloneOwner.isNew) {
                return@forEach
            }
            if (cloneOwner.shopId == detectedShopId && cloneOwner.platform == detectedPlatform) {
                return@forEach
            }
            val alreadyExists = allShops.any {
                it.shopId == detectedShopId && it.platform == detectedPlatform && !it.isNew
            }
            if (alreadyExists) {
                return@forEach
            }
            val promptKey = "${cloneOwner.id}:${detectedPlatform.id}:$detectedShopId"
            if (!promptedCloneSwitchKeys.add(promptKey)) {
                return@forEach
            }
            val detectedShopName = shopInfo.shopName?.takeIf { it.isNotBlank() }
                ?: "${detectedPlatform.displayName}-$detectedShopId"
            showCloneShopSwitchDialog(
                sourceShop = cloneOwner,
                detectedShop = Shop(
                    id = 0,
                    shopName = detectedShopName,
                    shopId = detectedShopId,
                    platform = detectedPlatform,
                    remainingDays = 30,
                    autoRenew = cloneOwner.autoRenew,
                    packageName = packageName,
                    cloneInstanceId = cloneOwner.cloneInstanceId
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
            .setMessage("当前分身已切换到「${detectedShop.shopName}」，是否扣减 1 点算力并新增该店铺？")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                viewModel.completePendingShop(
                    sourceShop,
                    detectedShop,
                    showMessage = true
                )
            }
            .setOnDismissListener { promptedCloneSwitchKeys.remove(promptKey) }
            .show()
    }

    private fun ensureVirtualUserId(): Int {
        val users = EngineProxy.getUsers()
        return if (users.isEmpty()) {
            EngineProxy.createUser(0)?.id ?: 0
        } else {
            users[0].id
        }
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

    override fun onResume() {
        super.onResume()
        shouldRetryPendingOnNextList = true
        shouldSyncCloneShopsOnNextList = true
        App.ensureEngineConnection()
        viewModel.refreshUserInfo()
        viewModel.loadShops()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingRecognitionKeys.clear()
        promptedCloneSwitchKeys.clear()
        super.onDestroy()
    }
}
