package com.example.ui.connectors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.ConnectorItemUi
import com.example.ui.ConnectorsUi
import com.example.ui.components.OrchBanner
import com.example.ui.components.OrchCard
import com.example.ui.components.OrchIconBadge
import com.example.ui.components.OrchPrimaryButton
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSuccess

@Composable
fun ConnectorsScreen(
    connectors: ConnectorsUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (id: String, accountId: String) -> Unit
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .height(OrchDimens.TopBarHeight)
                .padding(horizontal = OrchDimens.TightGap)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("connectors_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to chat",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Connectors",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            if (connectors.loading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(OrchDimens.TouchTarget)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("connectors_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh connectors",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            contentPadding = PaddingValues(OrchDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                val activeCount = connectors.activeIds.size
                val connectedCount = connectors.items.count { it.connected }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when {
                            activeCount > 0 -> "$activeCount enabled for chat"
                            connectedCount > 0 -> "$connectedCount connected"
                            else -> "Connect external apps"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connect your tools once, then toggle which ones Orch can use during conversations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            connectors.error?.let { err ->
                item(key = "error") {
                    OrchBanner(text = err, isError = true)
                }
            }

            items(connectors.items, key = { it.definition.id }) { item ->
                ConnectorCard(
                    item = item,
                    onToggle = { onToggle(item.definition.id) },
                    onConnect = { onConnect(item.definition.id) },
                    onDisconnect = {
                        val accountId = item.accountId ?: return@ConnectorCard
                        onDisconnect(item.definition.id, accountId)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectorCard(
    item: ConnectorItemUi,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val def = item.definition
    OrchCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OrchIconBadge(
                size = 44.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                ConnectorLogo(id = def.id)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = def.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = def.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            when {
                item.busy -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                item.connected -> Switch(
                    checked = item.active,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("connector_switch_${def.id}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = OrchSuccess,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = def.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        if (item.connected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (item.active) "Enabled in chat" else "Connected · Disabled",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.active) OrchSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onDisconnect,
                    enabled = !item.busy,
                    modifier = Modifier.testTag("connector_disconnect_${def.id}")
                ) {
                    Text(
                        text = "Disconnect",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            OrchPrimaryButton(
                text = "Connect",
                onClick = onConnect,
                enabled = !item.busy,
                loading = item.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connector_connect_${def.id}")
            )
        }
    }
}

@Composable
private fun ConnectorLogo(id: String) {
    when (id) {
        "github" -> Icon(
            painter = painterResource(R.drawable.ic_github_logo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        else -> {
            val logoRes = when (id) {
                "googledrive" -> R.drawable.ic_connector_googledrive
                "gmail" -> R.drawable.ic_connector_gmail
                "notion" -> R.drawable.ic_connector_notion
                "slack" -> R.drawable.ic_connector_slack
                "jira" -> R.drawable.ic_connector_jira
                else -> null
            }
            if (logoRes != null) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
