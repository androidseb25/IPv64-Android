package de.rpicloud.ipv64net.main.views

import android.annotation.SuppressLint
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.PreferencesManager
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.main.views.common.ConfirmDialog
import de.rpicloud.ipv64net.main.views.common.SectionHeader
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import de.rpicloud.ipv64net.models.User

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditView(navController: NavHostController, mainPadding: PaddingValues) {

    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showLoadingDialog by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var information by remember { mutableStateOf("") }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogText by remember { mutableStateOf("") }
    var errorDialogButtonText by remember { mutableIntStateOf(android.R.string.ok) }
    var userToRemove by remember { mutableStateOf<User?>(null) }

    val accountDetailsRoute = Tabs.getRoute(Tab.account_details)
    val accountDetailsBackStackEntry = remember(accountDetailsRoute) {
        navController.getBackStackEntry(accountDetailsRoute)
    }

    val apikey by accountDetailsBackStackEntry.savedStateHandle.getStateFlow<String>("SELECTED_USER_APIKEY", "").collectAsState()

    fun onSave() {
        keyboardController?.hide()
        if (username.isEmpty()) {
            errorDialogTitle = "Empty field"
            errorDialogText = "Please fill the Username field!"
            errorDialogButtonText = R.string.retry
            showDialog = true
            return
        }
        if (information.isEmpty()) {
            information = "No Information"
        }

        val user = User.list.firstOrNull { it.ApiKey == apikey }
        if (user != null) {
            user.Username = username
            user.Information = information
            user.update()
        }
        navController.popBackStack()
    }

    // Eintrag nur lokal entfernen (keine Server-API); bei Logout direkt zum Login, sonst zurück zur Liste
    fun onRemove(user: User) {
        removeAccount(ctx, user) { message ->
            if (message != null) {
                accountDetailsBackStackEntry.savedStateHandle[ACCOUNT_REMOVED_MESSAGE] = message
            }
            navController.popBackStack()
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
                },
                title = {
                    Text("Edit User")
                }, modifier = Modifier.statusBarsPadding(),
                actions = {
                    IconButton(
                        onClick = { userToRemove = User.list.firstOrNull { it.ApiKey == apikey } },
                        enabled = apikey.isNotEmpty()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete_24px),
                            contentDescription = "Remove account from this app"
                        )
                    }
                    IconButton(onClick = { onSave() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.save_24px),
                            contentDescription = "Save"
                        )
                    }
                }
            )
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
                item(key = "details_header") { SectionHeader("Account") }
                item(key = "username") {
                    OutlinedTextField(
                        value = username,
                        singleLine = true,
                        label = { Text("Username") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { newText -> username = newText },
                    )
                }
                item(key = "information") {
                    OutlinedTextField(
                        value = information,
                        singleLine = true,
                        label = { Text("Information") },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { newText -> information = newText },
                    )
                }
                item(key = "remove_hint") {
                    Text(
                        "Removes this entry from this device only. The account and its API key stay valid on ipv64.net.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Wenn kein Scrollen mehr stattfindet, wieder einblenden
    LaunchedEffect(Unit) {
        val user = User.list.firstOrNull { it.ApiKey == apikey }
        if (user != null) {
            username = user.Username
            information = user.Information
        }
    }

    userToRemove?.let { user ->
        ConfirmDialog(
            title = "Remove account from this app?",
            text = accountRemovalConfirmText(
                user,
                isActive = PreferencesManager.loadString(ctx, "APIKEY") == user.ApiKey,
                remaining = User.list
            ),
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                userToRemove = null
                onRemove(user)
            },
            onDismiss = { userToRemove = null }
        )
    }

    if (showDialog) {
        ErrorDialog(
            onDismissRequest = { showDialog = false },
            onConfirmation = { showDialog = false; },
            dialogTitle = errorDialogTitle,
            dialogText = errorDialogText,
            dialogConfirmText = errorDialogButtonText
        )
    }
}
