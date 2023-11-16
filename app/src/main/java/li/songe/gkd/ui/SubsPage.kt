package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.PageScaffold
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.subsIdToRawFlow


@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<SubsVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val appAndConfigs by vm.filterAppAndConfigsFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val subsRaw = subsIdToRaw[subsItem?.id]

    // 本地订阅
    val editable = subsItem?.id.let { it != null && it < 0 }

    var showAddDlg by remember {
        mutableStateOf(false)
    }

    var menuAppRaw by remember {
        mutableStateOf<SubscriptionRaw.AppRaw?>(null)
    }
    var editAppRaw by remember {
        mutableStateOf<SubscriptionRaw.AppRaw?>(null)
    }

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (showSearchBar && searchStr.isEmpty()) {
            focusRequester.requestFocus()
        }
    })
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    PageScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = {
                if (showSearchBar) {
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = "请输入应用名称",
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                } else {
                    Text(text = subsRaw?.name ?: subsItem?.id.toString())
                }
            }, actions = {
                if (showSearchBar) {
                    IconButton(onClick = {
                        if (vm.searchStrFlow.value.isEmpty()) {
                            showSearchBar = false
                        } else {
                            vm.searchStrFlow.value = ""
                        }
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = {
                        showSearchBar = true
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                }
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
        content =  { padding ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(padding)
            ) {
                itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.t0.id }) { _, a ->
                    val (appRaw, subsConfig, enableSize) = a
                    SubsAppCard(appRaw = appRaw,
                        appInfo = appInfoCache[appRaw.id],
                        subsConfig = subsConfig,
                        enableSize = enableSize,
                        onClick = {
                            navController.navigate(AppItemPageDestination(subsItemId, appRaw.id))
                        },
                        onValueChange = scope.launchAsFn { enable ->
                            val newItem = subsConfig?.copy(
                                enable = enable
                            ) ?: SubsConfig(
                                enable = enable,
                                type = SubsConfig.AppType,
                                subsItemId = subsItemId,
                                appId = appRaw.id,
                            )
                            DbSet.subsConfigDao.insert(newItem)
                        },
                        showMenu = editable,
                        onMenuClick = {
                            menuAppRaw = appRaw
                        })
                }
                item {
                    if (appAndConfigs.isEmpty()) {
                        Spacer(modifier = Modifier.height(40.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (searchStr.isNotEmpty()) {
                                Text(text = "暂无搜索结果")
                            } else {
                                Text(text = "此订阅暂无规则")
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }

            }
        },
    )

    val subsItemVal = subsItem

    if (showAddDlg && subsRaw != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = "添加APP规则") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入规则\n若APP规则已经存在则追加") },
            )
        }, onDismissRequest = { showAddDlg = false }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    SubscriptionRaw.parseAppRaw(source)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    ToastUtils.showShort("非法规则${e.message}")
                    return@TextButton
                }
                if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                    ToastUtils.showShort("不允许添加空白名规则组,请先命名")
                    return@TextButton
                }
                val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                val oldAppRaw = subsRaw.apps.getOrNull(oldAppRawIndex)
                if (oldAppRaw != null) {
                    // check same group name
                    newAppRaw.groups.forEach { g ->
                        if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                            ToastUtils.showShort("已经存在同名规则[${g.name}]\n请修改名称后再添加")
                            return@TextButton
                        }
                    }
                }
                val newApps = if (oldAppRaw != null) {
                    val oldKey = oldAppRaw.groups.maxBy { g -> g.key }.key + 1
                    val finalAppRaw =
                        newAppRaw.copy(groups = oldAppRaw.groups + newAppRaw.groups.mapIndexed { i, g ->
                            g.copy(
                                key = oldKey + i
                            )
                        })
                    subsRaw.apps.toMutableList().apply {
                        set(oldAppRawIndex, finalAppRaw)
                    }
                } else {
                    subsRaw.apps.toMutableList().apply {
                        add(newAppRaw)
                    }
                }
                vm.viewModelScope.launchTry {
                    subsItemVal.subsFile.writeText(
                        SubscriptionRaw.stringify(
                            subsRaw.copy(
                                apps = newApps, version = subsRaw.version + 1
                            )
                        )
                    )
                    DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                    showAddDlg = false
                    ToastUtils.showShort("添加成功")
                }
            }, enabled = source.isNotEmpty()) {
                Text(text = "添加")
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = "取消")
            }
        })
    }

    val editAppRawVal = editAppRaw
    if (editAppRawVal != null && subsItemVal != null && subsRaw != null) {
        var source by remember {
            mutableStateOf(Singleton.json.encodeToString(editAppRawVal))
        }
        AlertDialog(title = { Text(text = "编辑本地APP规则") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入规则") },
            )
        }, onDismissRequest = { editAppRaw = null }, confirmButton = {
            TextButton(onClick = {
                try {
                    val newAppRaw = SubscriptionRaw.parseAppRaw(source)
                    if (newAppRaw.id != editAppRawVal.id) {
                        ToastUtils.showShort("不允许修改规则id")
                        return@TextButton
                    }
                    val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == editAppRawVal.id }
                    vm.viewModelScope.launchTry {
                        subsItemVal.subsFile.writeText(
                            SubscriptionRaw.stringify(
                                subsRaw.copy(
                                    apps = subsRaw.apps.toMutableList().apply {
                                        set(oldAppRawIndex, newAppRaw)
                                    }, version = subsRaw.version + 1
                                )
                            )
                        )
                        DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                        editAppRaw = null
                        ToastUtils.showShort("更新成功")
                    }
                } catch (e: Exception) {
                    LogUtils.d(e)
                    ToastUtils.showShort("非法规则${e.message}")
                }
            }, enabled = source.isNotEmpty()) {
                Text(text = "添加")
            }
        }, dismissButton = {
            TextButton(onClick = { editAppRaw = null }) {
                Text(text = "取消")
            }
        })
    }


    val menuAppRawVal = menuAppRaw
    if (menuAppRawVal != null && subsItemVal != null && subsRaw != null) {
        Dialog(onDismissRequest = { menuAppRaw = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    Text(text = "复制", modifier = Modifier
                        .clickable {
                            ClipboardUtils.copyText(
                                Singleton.json.encodeToString(
                                    menuAppRawVal
                                )
                            )
                            ToastUtils.showShort("复制成功")
                            menuAppRaw = null
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    Text(text = "删除", modifier = Modifier
                        .clickable {
                            // 也许需要二次确认
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                subsItemVal.subsFile.writeText(
                                    Singleton.json.encodeToString(
                                        subsRaw.copy(apps = subsRaw.apps.filter { a -> a.id != menuAppRawVal.id })
                                    )
                                )
                                DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                                DbSet.subsConfigDao.delete(subsItemVal.id, menuAppRawVal.id)
                                ToastUtils.showShort("删除成功")
                            }
                            menuAppRaw = null
                        }
                        .fillMaxWidth()
                        .padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}