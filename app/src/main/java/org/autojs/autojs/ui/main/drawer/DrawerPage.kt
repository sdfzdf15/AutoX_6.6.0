package org.autojs.autojs.ui.main.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import coil.compose.rememberAsyncImagePainter
import com.stardust.app.GlobalAppContext
import com.stardust.app.isOpPermissionGranted
import com.stardust.app.permission.DrawOverlaysPermission
import com.stardust.app.permission.DrawOverlaysPermission.launchCanDrawOverlaysSettings
import com.stardust.app.permission.PermissionsSettingsUtil
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.notification.NotificationListenerService
import com.stardust.toast
import com.stardust.util.IntentUtil
import com.stardust.view.accessibility.AccessibilityService
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import org.autojs.autojs.Pref
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.devplugin.DevPlugin
import org.autojs.autojs.external.foreground.ForegroundService
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.tool.WifiTool
import org.autojs.autojs.ui.build.MyTextField
import org.autojs.autojs.ui.compose.theme.AutoXJsTheme
import org.autojs.autojs.ui.compose.widget.MyAlertDialog1
import org.autojs.autojs.ui.compose.widget.MyIcon
import org.autojs.autojs.ui.compose.widget.MySwitch
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.settings.SettingsActivity
import org.autojs.autoxjs.R  // <---- 这里修复了
import org.joda.time.DateTimeZone
import org.joda.time.Instant


private const val TAG = "DrawerPage"
private const val URL_DEV_PLUGIN = "https://github.com/kkevsekk1/Auto.js-VSCode-Extension"
private const val PROJECT_ADDRESS = "https://github.com/kkevsekk1/AutoX"
private const val DOWNLOAD_ADDRESS = "https://github.com/kkevsekk1/AutoX/releases"
private const val FEEDBACK_ADDRESS = "https://github.com/kkevsekk1/AutoX/issues"

@Composable
fun DrawerPage() {
    val context = LocalContext.current
    rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Spacer(
            modifier = Modifier
                .windowInsetsTopHeight(WindowInsets.statusBars)
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberAsyncImagePainter(R.drawable.autojs_logo1),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
            }
            Text(text = stringResource(id = R.string.text_service))
            AccessibilityServiceSwitch()
            StableModeSwitch()
            NotificationUsageRightSwitch()
            ForegroundServiceSwitch()
            UsageStatsPermissionSwitch()

            Text(text = stringResource(id = R.string.text_script_record))
            FloatingWindowSwitch()
            VolumeDownControlSwitch()
            AutoBackupSwitch()


            IgnoreBatteryOptimizationSwitch()
            Text(text = "自启动管理")
            // 🔥 后台弹出界面（Android10+）
            BackgroundPopupSwitch()
            BootAutoStartSwitch()


            Text(text = stringResource(id = R.string.text_others))
            ConnectComputerSwitch()


            USBDebugSwitch()

            SwitchTimedTaskScheduler()
            ProjectAddress(context)
            DownloadLink(context)
            Feedback(context)
            CheckForUpdate()
            AppDetailsSettings(context)
        }
        Spacer(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(AutoXJsTheme.colors.divider)
        )
        BottomButtons()
        Spacer(
            modifier = Modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
        )
    }
}

@Composable
fun IgnoreBatteryOptimizationSwitch() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 开关状态
    val isIgnored = remember { mutableStateOf(false) }

    // 刷新：读取系统真实权限
    fun refreshBatteryState() {
        isIgnored.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            false
        }
    }

    // 页面初始化 & 返回时自动刷新
    LaunchedEffect(Unit) {
        refreshBatteryState()
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            refreshBatteryState()
        }
    }

    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_debug),
                null
            )
        },
        text = { Text("忽略电池优化") },
        checked = isIgnored.value,

        // ==============================
        // 🔥 你要的完整逻辑 在这里
        // ==============================
        onCheckedChange = { currentClickState: Boolean ->

            // ==============================
            // 情况1：开关原本是开启的（已忽略）===>手动关闭
            // ==============================
            if (!currentClickState) {
                // 用户想关闭 → 跳设置界面
                try {
                    // 🔥 跳系统电池优化列表设置页面（不会弹框）
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(fallback)
                } catch (e2: Exception) {
                    Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                }
            }

            // ==============================
            // 情况2：开关原本是关闭的（未忽略）===>手动开启
            // ==============================
            else {
                // 用户想开启 → 弹出授权框
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}


// ==============================
// 🔥 后台弹出界面权限开关（最终版）
// 功能：仅 Android 10+ 系统才需要申请，Android 5-9 自动允许
// ==============================
@Composable
fun BackgroundPopupSwitch() {

    // ========================
    // 1. 获取上下文 & 生命周期
    // ========================
    // context：上下文，用于跳转系统设置、Toast、获取权限等
    val context = LocalContext.current

    // lifecycleOwner：页面生命周期（用于页面返回时自动刷新开关状态）
    val lifecycleOwner = LocalLifecycleOwner.current

    // ========================
    // 2. 权限检测方法
    // 作用：检查当前 APP 是否拥有「后台弹出界面」权限
    // ========================
    fun hasBackgroundPopupPermission(): Boolean {
        // ✅ 如果系统版本 < Android 10（API 29）
        // 直接返回 true = 有权限（5-9 原生系统不需要这个权限） // Android 9 及以下直接返回 true = 有权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }

        // ✅ Android 10+ 开始检查厂商定制权限
        return try {
            // 获取系统权限管理服务
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            // 获取当前应用的 UID（唯一标识）
            val uid = context.applicationInfo.uid

            // 获取当前应用包名
            val packageName = context.packageName

            // 后台弹出界面 对应的 op 代码（小米/华为/OPPO/VIVO 通用）
            val OP_BACKGROUND_START_ACTIVITY = 10021

            // 通过反射调用系统隐藏方法 checkOpNoThrow
            // 作用：检查是否允许后台启动Activity
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",       // 方法名
                Int::class.javaPrimitiveType,  // 参数1：op 代码
                Int::class.javaPrimitiveType,  // 参数2：uid
                String::class.java             // 参数3：包名
            )

            // 执行方法，获取结果
            val result =
                method.invoke(appOps, OP_BACKGROUND_START_ACTIVITY, uid, packageName) as Int

            // 返回权限状态：
            // MODE_ALLOWED = 已允许
            // MODE_ERRORED / MODE_IGNORED = 未允许
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            // 异常处理：
            // 1. 厂商没有这个权限
            // 2. 方法被修改
            // 出现异常 → 默认视为有权限，不影响使用
            true
        }
    }

    // ========================
    // 3. 开关状态（从本地存储读取）
    // 作用：记录开关是否开启，重启APP不丢失
    // ========================
    val isGranted = remember {
        // 从 Pref 读取上次保存的状态
        val isAndroid9OrLower = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        mutableStateOf(
            if (isAndroid9OrLower) {
                // 9及以下：永远是 true（开启）
                true
            } else {
                // 10+：读取本地存储
                Pref.isBackgroundPopupEnabled()
            }
        )
    }

    // ========================
    // 4. 页面返回时自动刷新权限
    // 作用：从设置页回来后，自动更新开关状态
    // ========================
    LaunchedEffect(Unit) {
        // 仅 Android 10+ 才需要刷新
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 监听页面生命周期：每次页面显示时执行
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                // 重新检查权限
                val granted = hasBackgroundPopupPermission()

                // 更新开关UI
                isGranted.value = granted

                // 保存到本地存储（永久记录）
                Pref.setBackgroundPopupEnabled(granted)
            }
        }
    }

    // ========================
    // 5. 渲染开关组件
    // ========================
    SwitchItem(
        // 左侧图标：使用悬浮窗权限的图标
        icon = {
            MyIcon(painterResource(id = R.drawable.ic_overlay), null)
        },

        // 开关文字
        text = { Text("后台弹出界面") },

        // 开关是否开启
        checked = isGranted.value,

        // ========================
        // 6. 用户点击开关时触发
        // ========================
        onCheckedChange = {
            // ------------------------------
            // 条件：Android 9 及以下
            // 逻辑：不跳设置，提示无需开启
            // ------------------------------
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(context, "Android 9及以下无法管此权限(默认开启)", Toast.LENGTH_SHORT)
                    .show()
                return@SwitchItem
            }

            // ------------------------------
            // 条件：Android 10+
            // 逻辑：跳转到对应手机品牌的权限设置页
            // ------------------------------
            try {
                // 创建意图 Intent
                val intent = Intent()

                // 必须加：跨界面跳转需要新任务栈
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 根据不同品牌，跳转到不同权限页面
                when {
                    // 小米 / 红米
                    Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) -> {
                        intent.component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity"
                        )
                        // 传入当前包名，直接打开本APP的权限页
                        intent.putExtra("extra_pkgname", context.packageName)
                    }

                    // 华为 / 荣耀
                    Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
                            Build.MANUFACTURER.equals("Honor", ignoreCase = true) -> {
                        intent.component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.permissionmanager.ui.MainActivity"
                        )
                    }

                    // OPPO
                    Build.MANUFACTURER.equals("OPPO", ignoreCase = true) -> {
                        intent.component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.singlepage.PermissionSinglePageActivity"
                        )
                        intent.putExtra("packageName", context.packageName)
                    }

                    // VIVO
                    Build.MANUFACTURER.equals("VIVO", ignoreCase = true) -> {
                        intent.component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.PurviewActivity"
                        )
                        intent.putExtra("packagename", context.packageName)
                    }

                    // 其他品牌：直接跳应用详情页
                    else -> {
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.data = Uri.parse("package:${context.packageName}")
                    }
                }

                // 执行跳转
                context.startActivity(intent)

                // 提示用户开启权限
                Toast.makeText(context, "请开启：后台弹出界面 / 后台显示悬浮窗", Toast.LENGTH_LONG)
                    .show()
            } catch (e: Exception) {
                // ------------------------------
                // 跳转失败 → 兜底方案：跳应用详情
                // ------------------------------
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
                Toast.makeText(context, "请手动开启「后台弹出界面」权限", Toast.LENGTH_SHORT).show()
            }
        }
    )
}


@Composable
fun BootAutoStartSwitch() {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(Pref.isBootAutoStartEnabled())
    }

    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_debug),
                null
            )
        },
        text = { Text(text = "开机自启") },
        checked = enabled,
        onCheckedChange = { checked ->
            enabled = checked
            Pref.setBootAutoStartEnabled(checked)

            if (checked) {
                // 你要的完整提示文案
                Toast.makeText(
                    context,
                    "开机自启已打开，同时需要权限：自启动 + 前台服务 + 后台弹出界面",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "已关闭开机自启", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
private fun AppDetailsSettings(context: Context) {
    TextButton(onClick = {
        context.startActivity(PermissionsSettingsUtil.getAppDetailSettingIntent(context.packageName))
    }) {
        Text(text = stringResource(R.string.text_app_detail_settings))
    }
}

@Composable
private fun Feedback(context: Context) {
    TextButton(onClick = {
        IntentUtil.browse(
            context,
            FEEDBACK_ADDRESS
        )
    }) {
        Text(text = stringResource(R.string.text_issue_report))
    }
}

@Composable
private fun DownloadLink(context: Context) {
    TextButton(onClick = {
        IntentUtil.browse(
            context,
            DOWNLOAD_ADDRESS
        )
    }) {
        Text(text = stringResource(R.string.text_app_download_link))
    }
}

@Composable
private fun ProjectAddress(context: Context) {
    TextButton(onClick = {
        IntentUtil.browse(
            context,
            PROJECT_ADDRESS
        )
    }) {
        Text(text = stringResource(R.string.text_project_link))
    }
}

@Composable
private fun CheckForUpdate(model: DrawerViewModel = viewModel()) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var enabled by rememberSaveable {
        mutableStateOf(true)
    }
    model.githubReleaseInfo

    TextButton(
        enabled = enabled,
        onClick = {
            enabled = false
            model.checkUpdate(
                onUpdate = {
                    showDialog = true
                },
                onComplete = {
                    enabled = true
                },
            )
        }
    ) {
        Text(text = stringResource(R.string.text_check_for_updates))
    }
    if (showDialog && model.githubReleaseInfo != null) {
        AlertDialog(onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = stringResource(
                        id = R.string.text_new_version2,
                        model.githubReleaseInfo!!.name
                    )
                )
            },
            text = {
                val date = rememberSaveable {
                    Instant.parse(model.githubReleaseInfo!!.createdAt)
                        .toDateTime(DateTimeZone.getDefault())
                        .toString("yyyy-MM-dd HH:mm:ss")
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = stringResource(id = R.string.text_release_date, date))
                    AndroidView(
                        factory = { context ->
                            TextView(context).apply {
                                val content =
                                    model.githubReleaseInfo!!.body.trim().replace("\r\n", "\n")
                                        .replace("\n", "  \n")
                                val markdwon = Markwon.builder(context).build()
                                markdwon.setMarkdown(this, content)
                            }
                        },
                        update = {

                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.text_cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    model.downloadApk()
                }) {
                    Text(text = stringResource(id = R.string.text_download))
                }
            })
    }
}

@Composable
private fun BottomButtons() {
    val context = LocalContext.current
    var lastBackPressedTime = remember {
        0L
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        SettingsActivity::class.java
                    )
                )
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.onBackground)
        ) {
            MyIcon(imageVector = Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.text_setting))
        }
        TextButton(
            modifier = Modifier.weight(1f), onClick = {
                val currentTime = System.currentTimeMillis()
                val interval = currentTime - lastBackPressedTime
                if (interval > 2000) {
                    lastBackPressedTime = currentTime
                    Toast.makeText(
                        context,
                        context.getString(R.string.text_press_again_to_exit),
                        Toast.LENGTH_SHORT
                    ).show()
                } else exitCompletely(context)
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.onBackground)
        ) {
            MyIcon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.text_exit))
        }
    }
}

fun exitCompletely(context: Context) {
    if (context is Activity) context.finish()
    FloatyWindowManger.hideCircularMenu()
    ForegroundService.stop(context)
    context.stopService(Intent(context, FloatyService::class.java))
    AutoJs.getInstance().scriptEngineService.stopAll()
}

@Composable
fun USBDebugSwitch() {
    val context = LocalContext.current
    var enable by remember {
        mutableStateOf(DevPlugin.isUSBDebugServiceActive)
    }
    val scope = rememberCoroutineScope()
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_debug),
                contentDescription = null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_open_usb_debug)) },
        checked = enable,
        onCheckedChange = {
            if (it) {
                scope.launch {
                    try {
                        DevPlugin.startUSBDebug()
                        enable = true
                    } catch (e: Exception) {
                        enable = false
                        e.printStackTrace()
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.text_start_service_failed,
                                e.localizedMessage
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                scope.launch {
                    DevPlugin.stopUSBDebug()
                    enable = false
                }
            }
        }
    )
}

@Composable
private fun ConnectComputerSwitch() {
    val context = LocalContext.current
    var enable by remember {
        mutableStateOf(DevPlugin.isActive)
    }
    var showDialog by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()
    val scanCodeLauncher =
        rememberLauncherForActivityResult(contract = ScanQRCode(), onResult = { result ->
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                    if (url.matches(Regex("^(ws://|wss://).+$"))) {
                        Pref.saveServerAddress(url)
                        connectServer(url)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.text_unsupported_qr_code),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                QRResult.QRUserCanceled -> {}
                QRResult.QRMissingPermission -> {}
                is QRResult.QRError -> {}
            }
        })
    LaunchedEffect(key1 = Unit, block = {
        DevPlugin.connectState.collect {
            withContext(Dispatchers.Main) {
                when (it.state) {
                    DevPlugin.State.CONNECTED -> enable = true
                    DevPlugin.State.DISCONNECTED -> enable = false
                }
            }
        }
    })
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_debug),
                null
            )
        },
        text = {
            Text(
                text = stringResource(
                    id = if (!enable) R.string.text_connect_computer
                    else R.string.text_connected_to_computer
                )
            )
        },
        checked = enable,
        onCheckedChange = {
            if (it) {
                showDialog = true
            } else {
                scope.launch { DevPlugin.close() }
            }
        }
    )
    if (showDialog) {
        ConnectComputerDialog(
            onDismissRequest = { showDialog = false },
            onScanCode = { scanCodeLauncher.launch(null) }
        )
    }

}

@Composable
private fun ConnectComputerDialog(
    onDismissRequest: () -> Unit,
    onScanCode: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { onDismissRequest() }) {
        var host by remember {
            mutableStateOf(Pref.getServerAddressOrDefault(WifiTool.getRouterIp(context)))
        }
        Surface(shape = RoundedCornerShape(4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(text = stringResource(id = R.string.text_server_address))
                MyTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.padding(vertical = 16.dp),
                    placeholder = {
                        Text(text = host)
                    }
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            IntentUtil.browse(context, URL_DEV_PLUGIN)
                        }
                    ) {
                        Text(text = stringResource(id = R.string.text_help))
                    }
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onScanCode()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.text_scan_qr))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onDismissRequest()
                        Pref.saveServerAddress(host)
                        connectServer(getUrl(host))
                    }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            }
        }

    }
}

@OptIn(DelicateCoroutinesApi::class)
@SuppressLint("HardwareIds")
private fun connectServer(
    url: String,
) {
    GlobalScope.launch { DevPlugin.connect(url) }
}

private fun getUrl(host: String): String {
    var url1 = host
    if (!url1.matches(Regex("^(ws|wss)://.*"))) {
        url1 = "ws://${url1}"
    }
    if (!url1.matches(Regex("^.+://.+?:.+$"))) {
        url1 += ":${DevPlugin.SERVER_PORT}"
    }
    return url1
}

@Composable
private fun AutoBackupSwitch() {
    val context = LocalContext.current
    var enable by remember {
        val default = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.key_auto_backup), false)
        mutableStateOf(default)
    }
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_backup),
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_auto_backup)) },
        checked = enable,
        onCheckedChange = {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.key_auto_backup), it)
                .apply()
            enable = it
        }
    )
}

@Composable
private fun VolumeDownControlSwitch() {
    val context = LocalContext.current
    var enable by remember {
        val default = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.key_use_volume_control_record), false)
        mutableStateOf(default)
    }
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_sound_waves),
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_volume_down_control)) },
        checked = enable,
        onCheckedChange = {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.key_use_volume_control_record), it)
                .apply()
            enable = it
        }
    )
}

@Composable
private fun FloatingWindowSwitch() {
    val context = LocalContext.current

    var isFloatingWindowShowing by remember {
        mutableStateOf(FloatyWindowManger.isCircularMenuShowing())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            if (DrawOverlaysPermission.isCanDrawOverlays(context)) FloatyWindowManger.showCircularMenu()
            isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing()
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                painterResource(id = R.drawable.ic_overlay),
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_floating_window)) },
        checked = isFloatingWindowShowing,
        onCheckedChange = {
            if (isFloatingWindowShowing) {
                FloatyWindowManger.hideCircularMenu()
            } else {
                if (DrawOverlaysPermission.isCanDrawOverlays(context)) FloatyWindowManger.showCircularMenu()
                else launcher.launchCanDrawOverlaysSettings(context.packageName)
            }
            isFloatingWindowShowing = FloatyWindowManger.isCircularMenuShowing()
            Pref.setFloatingMenuShown(isFloatingWindowShowing)
        }
    )
}

@Composable
private fun UsageStatsPermissionSwitch() {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(context.isOpPermissionGranted(AppOpsManager.OPSTR_GET_USAGE_STATS))
    }
    var showDialog by remember {
        mutableStateOf(false)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            enabled = context.isOpPermissionGranted(AppOpsManager.OPSTR_GET_USAGE_STATS)
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Settings,
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_usage_stats_permission)) },
        checked = enabled,
        onCheckedChange = {
            showDialog = true
        }
    )
    if (showDialog) {
        AlertDialog(
            title = { Text(text = stringResource(id = R.string.text_usage_stats_permission)) },
            onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = stringResource(
                        R.string.description_usage_stats_permission
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text(text = stringResource(id = R.string.text_go_to_setting))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.text_cancel))
                }
            },
        )
    }
}

@Composable
private fun ForegroundServiceSwitch() {
    val context = LocalContext.current
    var isOpenForegroundServices by remember {
        val default = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.key_foreground_servie), false)
        mutableStateOf(default)
    }
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Settings,
                contentDescription = null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_foreground_service)) },
        checked = isOpenForegroundServices,
        onCheckedChange = {
            if (it) {
                ForegroundService.start(context)
            } else {
                ForegroundService.stop(context)
            }
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.key_foreground_servie), it)
                .apply()
            isOpenForegroundServices = it
        }
    )
}

@Composable
private fun NotificationUsageRightSwitch() {
    LocalContext.current
    var isNotificationListenerEnable by remember {
        mutableStateOf(notificationListenerEnable())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            isNotificationListenerEnable = notificationListenerEnable()
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Notifications,
                null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_notification_permission)) },
        checked = isNotificationListenerEnable,
        onCheckedChange = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else isNotificationListenerEnable = it
        }
    )
}

private fun notificationListenerEnable(): Boolean = NotificationListenerService.instance != null


@Composable
private fun StableModeSwitch() {
    val context = LocalContext.current
    var showDialog by remember {
        mutableStateOf(false)
    }
    var isStableMode by remember {
        val default = Pref.isStableModeEnabled()
        mutableStateOf(default)
    }
    SwitchItem(
        icon = {
            MyIcon(
                painter = painterResource(id = R.drawable.ic_triangle),
                contentDescription = null
            )
        },
        text = { Text(text = stringResource(id = R.string.text_stable_mode)) },
        checked = isStableMode,
        onCheckedChange = {
            if (it) showDialog = true
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(context.getString(R.string.key_stable_mode), it)
                .apply()
            isStableMode = it
        }
    )
    if (showDialog) {
        AlertDialog(
            title = { Text(text = stringResource(id = R.string.text_stable_mode)) },
            onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = stringResource(
                        R.string.description_stable_mode
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun AccessibilityServiceSwitch() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember {
        mutableStateOf(false)
    }
    var isAccessibilityServiceEnabled by remember {
        mutableStateOf(AccessibilityServiceTool.isAccessibilityServiceEnabled(context))
    }
    val accessibilitySettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (AccessibilityServiceTool.isAccessibilityServiceEnabled(context)) {
                isAccessibilityServiceEnabled = true
            } else {
                isAccessibilityServiceEnabled = false
                Toast.makeText(
                    context,
                    R.string.text_accessibility_service_is_not_enable,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    var editor by remember { mutableStateOf(Pref.getEditor()) }
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Edit,
                contentDescription = null,
            )
        },
        text = { Text(text = "启用新编辑器") },
        checked = editor,
        onCheckedChange = { isChecked ->
            editor = isChecked
            Pref.setEditor(isChecked)
        }
    )
    SwitchItem(
        icon = {
            MyIcon(
                Icons.Default.Settings,
                contentDescription = null,
            )
        },
        text = { Text(text = stringResource(id = R.string.text_accessibility_service)) },
        checked = isAccessibilityServiceEnabled,
        onCheckedChange = {
            if (!isAccessibilityServiceEnabled) {
                if (Pref.shouldEnableAccessibilityServiceByRoot()) {
                    scope.launch {
                        val enabled = withContext(Dispatchers.IO) {
                            AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(2000)
                        }
                        if (enabled) isAccessibilityServiceEnabled = true
                        else showDialog = true
                    }
                } else showDialog = true
            } else {
                isAccessibilityServiceEnabled = !AccessibilityService.disable()
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            title = { Text(text = stringResource(id = R.string.text_need_to_enable_accessibility_service)) },
            onDismissRequest = { showDialog = false },
            text = {
                Text(
                    text = stringResource(
                        R.string.explain_accessibility_permission2,
                        GlobalAppContext.appName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    accessibilitySettingsLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text(text = stringResource(id = R.string.text_go_to_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.text_cancel))
                }
            },
        )
    }
}

@Composable
fun SwitchItem(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            icon()
        }
        Box(modifier = Modifier.weight(1f)) {
            text()
        }
        MySwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SwitchTimedTaskScheduler() {
    var isShowDialog by rememberSaveable {
        mutableStateOf(false)
    }
    TextButton(onClick = { isShowDialog = true }) {
        Text(text = stringResource(id = R.string.text_switch_timed_task_scheduler))
    }
    if (isShowDialog) {
        TimedTaskSchedulerDialog(onDismissRequest = { isShowDialog = false })
    }
}

@Composable
fun TimedTaskSchedulerDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var selected by rememberSaveable {
        mutableStateOf(Pref.getTaskManager())
    }
    MyAlertDialog1(
        onDismissRequest = onDismissRequest,
        onConfirmClick = {
            onDismissRequest()
            Pref.setTaskManager(selected)
            toast(context, R.string.text_set_successfully)
        },
        title = { Text(text = stringResource(id = R.string.text_switch_timed_task_scheduler)) },
        text = {
            Column {
                Spacer(modifier = Modifier.size(16.dp))
                Column() {
                    for (i in 0 until 3) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = i }) {
                            RadioButton(selected = selected == i, onClick = { selected = i })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (i) {
                                    0 -> stringResource(id = R.string.text_work_manager)
                                    1 -> stringResource(id = R.string.text_android_job)
                                    else -> stringResource(id = R.string.text_alarm_manager)
                                }
                            )
                        }
                    }
                }
            }

        }
    )
}