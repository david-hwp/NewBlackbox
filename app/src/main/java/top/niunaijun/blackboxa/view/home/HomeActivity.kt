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
    private var shouldRetryPendingOnNextList = false
    private var pendingEngineAction: (() -> Unit)? = null
    private var shownAnnouncementId: Long? = null
    private var shouldCheckAnnouncementOnResume = false
    private var suppressNextAnnouncementOnResume = false

    companion object {
        private const val TAG = "HomeActivity"

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
            suppressNextAnnouncementOnResume = true
            ProfileActivity.start(this)
        }

        // 交易日志按钮
        viewBinding.btnLogs.setOnClickListener {
            suppressNextAnnouncementOnResume = true
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
        shownAnnouncementId = null
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

        openPlatformForShop(shop)
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
                schedulePendingShopRecognition(shop, targetUserId, showFailureToast = true)
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
                if (launchVirtualApp(packageName, targetUserId, platformName) && shop.isNew) {
                    reportCloneCreated(shop, targetUserId)
                    schedulePendingShopRecognition(shop, targetUserId, showFailureToast = true)
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
            suppressNextAnnouncementOnResume = true
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
            toast("${platformItem.displayName} 暂未开放")
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
        val packageName = pendingShop.packageName ?: return
        EngineProxy.triggerShopIdExtract(packageName, userId)
        handler.postDelayed({
            reportExtractedShopForPending(pendingShop, userId, showFailureToast)
        }, 1500)
    }

    private fun reportExtractedShopForPending(
        pendingShop: Shop,
        userId: Int,
        showFailureToast: Boolean = false
    ) {
        val packageName = pendingShop.packageName ?: return
        val shopInfo = EngineProxy.getShopInfo(packageName, userId)
        val shopName = shopInfo?.shopName?.takeIf { it.isNotBlank() }
        val shopId = shopInfo?.shopId?.takeIf { it.isNotBlank() }
        if (shopName == null || shopId == null) {
            if (showFailureToast) {
                toast("未获取到店铺信息，请登录后重试")
            }
            return
        }
        val platform = shopInfo.platform.takeIf { it.isNotBlank() }?.let {
            top.niunaijun.blackboxa.bean.Platform.fromId(it)
        } ?: pendingShop.platform

        viewModel.completePendingShop(
            pendingShop,
            Shop(
                id = 0,
                shopName = shopName,
                shopId = shopId,
                platform = platform,
                remainingDays = 30,
                autoRenew = false,
                packageName = packageName,
                cloneInstanceId = buildCloneInstanceId(pendingShop, packageName, userId)
            )
        )
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
            suppressNextAnnouncementOnResume = true
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
            suppressNextAnnouncementOnResume = true
            startActivity(marketIntent)
        } catch (e: Exception) {
            runCatching {
                suppressNextAnnouncementOnResume = true
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
        // Refresh data when returning
        if (shouldCheckAnnouncementOnResume) {
            shouldCheckAnnouncementOnResume = false
            loadAnnouncementForOpen()
        }
        shouldRetryPendingOnNextList = true
        App.ensureEngineConnection()
        viewModel.refreshUserInfo()
        viewModel.loadShops()
    }

    override fun onStop() {
        super.onStop()
        if (suppressNextAnnouncementOnResume) {
            suppressNextAnnouncementOnResume = false
        } else {
            shouldCheckAnnouncementOnResume = true
        }
    }
}
