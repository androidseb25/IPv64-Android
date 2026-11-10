package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User
import kotlinx.coroutines.launch

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var fabVisible by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Liste und aktiven Key im State halten: User.list liest die Prefs und löst keine Recomposition aus.
    // remember (nicht saveable) lädt beim Zurückkehren von Login/Bearbeiten neu.
    var users by remember { mutableStateOf(User.list.toList()) }
    var activeKey by remember { mutableStateOf(PreferencesManager.loadString(ctx, "APIKEY")) }
    var userToRemove by remember { mutableStateOf<User?>(null) }

    fun reload() {
        users = User.list.toList()
        activeKey = PreferencesManager.loadString(ctx, "APIKEY")
    }

    fun removeUser(user: User) {
        removeAccount(ctx, user) { message ->
            reload()
            if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // Meldung vom Bearbeiten-Screen (Account dort entfernt) einmalig anzeigen
    val ownEntry = remember { navController.getBackStackEntry(Tabs.getRoute(Tab.account_details)) }
    LaunchedEffect(ownEntry) {
        ownEntry.savedStateHandle.getStateFlow(ACCOUNT_REMOVED_MESSAGE, "").collect { message ->
            if (message.isNotEmpty()) {
                ownEntry.savedStateHandle[ACCOUNT_REMOVED_MESSAGE] = ""
                reload()
                launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
                        )
                    }
                }, title = {
                    Text("Users")
                }, modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(onClick = {
                    navController.navigate(Tabs.getRoute(Tab.login))
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.person_add_24px),
                        contentDescription = "icon"
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(mainPadding)
            .consumeWindowInsets(mainPadding)  // avoids double insets
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxHeight()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "accounts") {
                    ListGroup {
                        users.forEachIndexed { i, user ->
                            if (i > 0) GroupDivider(56.dp)
                            AccountItemView(
                                user,
                                isActive = user.ApiKey == activeKey,
                                onClick = {
                                    PreferencesManager.saveString(ctx, "APIKEY", user.ApiKey)
                                    navController.popBackStack()
                                },
                                onLongClick = {
                                    navController.currentBackStackEntry?.savedStateHandle?.set(
                                        "SELECTED_USER_APIKEY",
                                        it.ApiKey
                                    )
                                    navController.navigate(Tabs.getRoute(Tab.account_edit))
                                },
                                onRemove = { userToRemove = it }
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Tap to switch accounts. For editing the name and information field, make a long press. " +
                                "The delete icon only removes an account from this app.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    userToRemove?.let { user ->
        ConfirmDialog(
            title = "Remove account from this app?",
            text = accountRemovalConfirmText(user, isActive = user.ApiKey == activeKey, remaining = users),
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                userToRemove = null
                removeUser(user)
            },
            onDismiss = { userToRemove = null }
        )
    }
}
