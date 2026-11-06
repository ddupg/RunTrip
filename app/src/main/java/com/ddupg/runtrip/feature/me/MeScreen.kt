package com.ddupg.runtrip.feature.me

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddupg.runtrip.data.source.DataSourceConnector
import com.ddupg.runtrip.data.source.DataSourceId
import com.ddupg.runtrip.data.source.SourceConnection

@Composable
fun MeRoute(connectors: List<DataSourceConnector>, onTrainingPreferences: () -> Unit) {
    val model: MeViewModel = viewModel(factory = MeViewModel.Factory(connectors))
    val state by model.uiState.collectAsStateWithLifecycle()
    val authorizationUrl by model.authorizationUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(authorizationUrl) {
        model.takeAuthorizationUrl()?.let { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                model.browserUnavailable(url)
            }
        }
    }
    MeScreen(state, model::openSource, model::retry, onTrainingPreferences)
    state.selectedSource?.let { id ->
        val connection = state.sources.single { it.id == id }.connection
        when (connection) {
            is SourceConnection.Bound -> AlertDialog(
                onDismissRequest = model::dismissSource,
                title = { Text("解除${sourceLabel(id)}绑定？") },
                text = { Text("将清除此手机上 ${accountLabel(connection.account)} 的${sourceLabel(id)}授权信息。") },
                confirmButton = { TextButton(onClick = { model.disconnect(id) }) { Text("解除绑定") } },
                dismissButton = { TextButton(onClick = model::dismissSource) { Text("取消") } },
            )
            is SourceConnection.Authorizing -> AlertDialog(
                onDismissRequest = model::dismissSource,
                title = { Text("完成${sourceLabel(id)}授权") },
                text = {
                    Text(if (connection.browserUrl == null) "授权已完成，账号信息尚未读取成功，请重试。"
                         else "请在浏览器中完成${sourceLabel(id)}登录，随后返回 RunTrip。")
                },
                confirmButton = {
                    Column {
                        TextButton(onClick = model::checkAuthorization) {
                            Text(if (connection.browserUrl == null) "重试" else "检查授权结果")
                        }
                        if (connection.browserUrl != null) {
                            TextButton(onClick = { model.authorize(id) }) { Text("继续前往授权") }
                        }
                    }
                },
                dismissButton = { TextButton(onClick = { model.disconnect(id) }) { Text("取消连接") } },
            )
            SourceConnection.AuthorizationRestartRequired -> AlertDialog(
                onDismissRequest = model::dismissSource,
                title = { Text("需要重新连接${sourceLabel(id)}") },
                text = { Text("授权结果未能确认。请先取消连接，再重新发起授权。") },
                confirmButton = { TextButton(onClick = { model.disconnect(id) }) { Text("取消连接") } },
                dismissButton = { TextButton(onClick = model::dismissSource) { Text("稍后") } },
            )
            SourceConnection.StorageError -> AlertDialog(
                onDismissRequest = model::dismissSource,
                title = { Text("重置${sourceLabel(id)}连接？") },
                text = { Text("连接信息无法读取。重置将清除此手机上的${sourceLabel(id)}授权信息，保留本地训练记录。") },
                confirmButton = { TextButton(onClick = { model.disconnect(id) }) { Text("重置连接") } },
                dismissButton = { TextButton(onClick = model::dismissSource) { Text("取消") } },
            )
            SourceConnection.Unbound -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(state: MeUiState, onOpenSource: (DataSourceId) -> Unit, onRetry: () -> Unit,
    onTrainingPreferences: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("我", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("数据来源", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.sources.forEach { source ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .clickable(enabled = !state.isWorking) { onOpenSource(source.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(sourceLabel(source.id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (state.isWorking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        connectionLabel(source.connection), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
                        modifier = Modifier.weight(2f),
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry, enabled = !state.isWorking) { Text("重试") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp).heightIn(min = 60.dp)
                .clickable(onClick = onTrainingPreferences), verticalAlignment = Alignment.CenterVertically) {
                Text("训练偏好", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
