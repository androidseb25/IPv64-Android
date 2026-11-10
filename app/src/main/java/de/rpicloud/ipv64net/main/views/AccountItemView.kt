package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.StatusChip
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.ui.theme.AppTheme

/**
 * Zeile eines gespeicherten Accounts (in einer [ListGroup]): Antippen wechselt den Account, langes Drücken
 * öffnet die Bearbeitung. Der aktive Account trägt den Chip "Active".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItemView(
    user: User,
    isActive: Boolean,
    onClick: (selectedUser: User) -> Unit,
    onLongClick: (selectedUser: User) -> Unit,
    onRemove: ((selectedUser: User) -> Unit)? = null
) {
    ListRow(
        title = user.Username,
        subtitle = user.Information.ifEmpty { "No Information" },
        subtitleMaxLines = 1,
        icon = R.drawable.account_circle_24px,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) StatusChip("Active", colorResource(R.color.ipv64_green))
                // Eintrag nur aus der App entfernen (öffnet einen Bestätigungsdialog)
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(user) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete_24px),
                            contentDescription = "Remove ${user.Username} from this app",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        showChevron = false,
        modifier = Modifier.combinedClickable(onLongClick = { onLongClick(user) }, onClick = { onClick(user) })
    )
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun AccountItemViewPreview() {
    AppTheme {
        val user = User.empty
        user.Information = "No Informations"
        user.Username = "Default User"
        ListGroup {
            AccountItemView(
                user,
                isActive = true,
                onClick = { },
                onLongClick = { },
                onRemove = { }
            )
        }
    }
}
