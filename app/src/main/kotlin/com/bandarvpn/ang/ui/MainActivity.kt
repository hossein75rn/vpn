package com.bandarvpn.ang.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandarvpn.ang.AppConfig
import com.bandarvpn.ang.AppConfig.ANG_PACKAGE
import com.bandarvpn.ang.BuildConfig
import com.bandarvpn.ang.R
import com.bandarvpn.ang.data.RetrofitInstance
import com.bandarvpn.ang.data.api.ApiService
import com.bandarvpn.ang.data.model.VpnConfigs
import com.bandarvpn.ang.data.utils.StoreStringPreference
import com.bandarvpn.ang.databinding.ActivityMainBinding
import com.bandarvpn.ang.dto.EConfigType
import com.bandarvpn.ang.extension.toast
import com.bandarvpn.ang.helper.SimpleItemTouchHelperCallback
import com.bandarvpn.ang.service.V2RayServiceManager
import com.bandarvpn.ang.util.*
import com.bandarvpn.ang.viewmodel.MainViewModel
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import me.drakeet.support.toast.ToastCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private val TAG = "hossein75rn"
    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private var api: ApiService? = null
    private var storeShareKey: StoreStringPreference? = null
    private var uuid: String? = null
    private var firstLogin:String?=null
    private var vpnConfigs: VpnConfigs? = null
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    @SuppressLint("Recycle")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        init()
        if (firstLogin.isNullOrBlank() || firstLogin.isNullOrEmpty()){
            storeShareKey?.save("first","true")
            refreshServers()
        }
        mainStorage.putBoolean("firstload",false)

        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
        binding.fabRefresh.setOnClickListener {
            val anim = ObjectAnimator.ofFloat(it, "rotation", 0f, 360f)
            anim.repeatCount = ValueAnimator.REVERSE // repeat indefinitely
            anim.setDuration(1000)
            anim.start()
            refreshServers()
        }

        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
        binding.version.text = "v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})"

        setupViewModel()
        copyAssets()
        migrateLegacy()
        //todo

    }

    private fun refreshServers() {
        MmkvManager.removeAllServer()
        initLinkMaker()
    }

    private fun initLinkMaker() {
        api!!.config(uuid).enqueue(object : Callback<VpnConfigs?> {
            override fun onResponse(call: Call<VpnConfigs?>, response: Response<VpnConfigs?>) {
                if (response.body() == null) {
                    ToastError()
                    return
                }
                val resp:VpnConfigs = response.body()!!
                if (resp.status == "success") {
                    importBatchConfig(resp.configs)

                } else {
                    ToastError()
                }
            }

            override fun onFailure(call: Call<VpnConfigs?>, t: Throwable) {
                Log.w(TAG, "onFailure: ", t)
            }
        })
    }

    private fun init() {
        api = RetrofitInstance.Instance().create(ApiService::class.java)
        storeShareKey = StoreStringPreference(this)
        uuid = storeShareKey!!.read("uuid")
        firstLogin = storeShareKey!!.read("first")
        vpnConfigs = null
    }
    private fun ToastError() {
        Toast.makeText(this@MainActivity, "خطایی رخ داد بروزرسانی کنید", Toast.LENGTH_SHORT)
            .show()
    }
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                animateRun()
                setTestState(getString(R.string.connection_connected))
            } else {
                binding.fab.pauseAnimation()
                animatStop()
                setTestState(getString(R.string.connection_not_connected))
            }
            hideCircle()
        }
        mainViewModel.startListenBroadcast()
    }

    private fun animateRun() {
        binding.fab.playAnimation()
        binding.fab.animate()
            .scaleX(2.5f)
            .scaleY(2.5f)
            .setDuration(500)
            .start()
    }
    private fun animatStop() {
        binding.fab.animate()
            .scaleX(-2.5f)
            .scaleY(-2.5f)
            .setDuration(500)
            .start()
        binding.fab.pauseAnimation()
    }
    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                        ?.filter { geo.contains(it) }
                        ?.filter { !File(extFolder, it).exists() }
                        ?.forEach {
                            val target = File(extFolder, it)
                            assets.open(it).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.i(ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}")
                        }
            } catch (e: Exception) {
                Log.e(ANG_PACKAGE, "asset copy failed", e)
            }
        }
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    startV2Ray()
                }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode(true)
            true
        }
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, mainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            mainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeAllServer()
                        mainViewModel.reloadServerList()
                    }
                    .show()
            true
        }

        R.id.del_invalid_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    mainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            mainViewModel.reloadServerList()
            true
        }
        R.id.filter_config -> {
            mainViewModel.filterConfig(this)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        )
    }

    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        if (forConfig)
                            scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                        else
                            scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        val subid2 = if(subid.isNullOrEmpty()){
            mainViewModel.subscriptionId
        }else{
            subid
        }
        val append = subid.isNullOrEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }
    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first)
                        || TextUtils.isEmpty(it.second.remarks)
                        || TextUtils.isEmpty(it.second.url)
                ) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = Utils.idnToASCII(it.second.url)
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use { input ->
                                importCustomizeConfig(input?.bufferedReader()?.readText())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            mainViewModel.reloadServerList()
            toast(R.string.toast_success)
            //adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        binding.fabProgressCircle.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        try {
                            if (binding.fabProgressCircle.isShown) {
                                binding.fabProgressCircle.hide()
                            }
                        } catch (e: Exception) {
                            Log.w(ANG_PACKAGE, e)
                        }
                    }
        } catch (e: Exception) {
            Log.d(ANG_PACKAGE, e.toString())
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
