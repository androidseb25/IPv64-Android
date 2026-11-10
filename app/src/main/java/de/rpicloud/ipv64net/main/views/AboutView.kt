package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.main.views.common.GroupDivider
import de.rpicloud.ipv64net.main.views.common.ListGroup
import de.rpicloud.ipv64net.main.views.common.ListRow
import de.rpicloud.ipv64net.main.views.common.ScreenScaffold
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs

@Composable
fun AboutView(navController: NavHostController, mainPadding: PaddingValues) {
    ScreenScaffold(title = Tabs.getLabel(Tab.about), navController = navController, mainPadding = mainPadding) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ListGroup(title = "Help") {
                    ListRow(
                        title = "IP differs",
                        subtitle = "The A or AAAA record doesn't match your current IP.",
                        leading = { StatusDot(R.color.ipv64_red) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "IP matches",
                        subtitle = "The A and AAAA records match your current IP.",
                        leading = { StatusDot(R.color.ipv64_green) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "IP unknown",
                        subtitle = "Your current IP couldn't be determined.",
                        leading = { StatusDot(R.color.ipv64_gray) }
                    )
                    GroupDivider()
                    ListRow(
                        title = "No IP",
                        subtitle = "The domain has no A or AAAA record yet.",
                        leading = { StatusDot(R.color.ipv64_gray) }
                    )
                }
            }
            item {
                ListGroup(title = "What is IPv64.net?") {
                    TextBlock(
                        "IPv64 is of course not a new Internet Protocol (64), but simply a deduplicated short form of IPv6 and IPv4. On the IPv64 site you will find a Dynamic DNS service (DynDNS) and many other useful tools for your daily internet experience.\n\nWith the dynamic DNS service of IPv64 you can register and use free subdomains. The update of the domain is done automatically by your own router or alternative hardware / software. Besides updating IP addresses, simple Let's Encrypt DNS challenges are also possible.\n\nOwn domains can be added and benefit from all IPv64.net features like DynDNS services, GEO load balancing, DDoS protection, DynDNS2 and SSL encryption."
                    )
                }
            }
            item {
                ListGroup(title = "Contact") {
                    TextBlock(
                        "IPv64.net - A product of Prox IT UG (limited liability)\n\n" +
                                "Information according to Section § 5 DDG\n" +
                                "Prox IT UG (limited liability)\n" +
                                "Am Eisenstein 10\n" +
                                "45470 Mülheim an der Ruhr\n\n" +
                                "Represented by\n" +
                                "Dennis Schröder (CEO)\n\n" +
                                "Registry court: Amtsgericht Duisburg\n" +
                                "Register number: HRB 35106\n" +
                                "Ust-IdNr.: DE350434683"
                    )
                }
            }
            item {
                ListGroup(title = "About the App") {
                    TextBlock("This app was created with the help of the Raspberry Pi Cloud community. All rights reserved by Dennis Schröder.")
                }
            }
        }
    }
}

// Status wird zusätzlich im Text der Zeile genannt (Farbe allein trägt keine Bedeutung)
@Composable
private fun StatusDot(colorRes: Int) {
    Icon(
        painter = painterResource(id = R.drawable.circle_24px),
        contentDescription = null,
        tint = colorResource(colorRes),
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun TextBlock(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}
