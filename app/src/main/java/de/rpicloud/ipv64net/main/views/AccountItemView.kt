package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.rpicloud.ipv64net.models.User
import de.rpicloud.ipv64net.ui.theme.AppTheme

@Composable
fun AccountItemView(
    user: User,
    onClick: (selectedUser: User) -> Unit,
    onLongClick: (selectedUser: User) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,         // oder `LocalIndication.current` für Ripple
                onLongClick = { onLongClick(user) },
                onClick = { onClick(user) }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    user.Username,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    user.Information.ifEmpty { "No Information" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            RadioButton(
                selected = User.current!!.ApiKey == user.ApiKey,
                onClick = { onClick(user) }
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
fun AccountItemViewPreview() {
    AppTheme {
        val user = User.empty
        user.Information = "No Informations"
        user.Username = "Default User"
        AccountItemView(
            user,
            onClick = {
                println(it)
            },
            onLongClick = {
                println(it)
            }
        )
    }
}