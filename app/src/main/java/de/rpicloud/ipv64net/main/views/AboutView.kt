package de.rpicloud.ipv64net.main.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import de.rpicloud.ipv64net.R
import de.rpicloud.ipv64net.helper.NetworkService
import de.rpicloud.ipv64net.helper.parseDbDateTime
import de.rpicloud.ipv64net.helper.views.ErrorDialog
import de.rpicloud.ipv64net.helper.views.SpinnerDialog
import de.rpicloud.ipv64net.models.Logs
import de.rpicloud.ipv64net.models.Tab
import de.rpicloud.ipv64net.models.Tabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutView(navController: NavHostController, mainPadding: PaddingValues) {
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(Tabs.getLabel(Tab.about))
                }, modifier = Modifier.statusBarsPadding(), navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24px),
                            contentDescription = "Close"
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Help",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Column(
                                modifier = Modifier
                                    .padding(top = 19.dp, bottom = 16.dp, start = 16.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.circle_24px),
                                    contentDescription = "icon",
                                    tint = Color.Red,
                                    modifier = Modifier.height(14.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "A-Record or AAAA-Record didn't match",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row {
                            Column(
                                modifier = Modifier
                                    .padding(top = 19.dp, bottom = 16.dp, start = 16.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.circle_24px),
                                    contentDescription = "icon",
                                    tint = Color.Green,
                                    modifier = Modifier.height(14.dp)
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "A-Record or AAAA-Record match",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "What is IPv64.net?",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "IPv64 is of course not a new Internet Protocol (64), but simply a deduplicated short form of IPv6 and IPv4. On the IPv64 site you will find a Dynamic DNS service (DynDNS) and many other useful tools for your daily internet experience.\n\nWith the dynamic DNS service of IPv64 you can register and use free subdomains. The update of the domain is done automatically by your own router or alternative hardware / software. Besides updating IP addresses, simple Let's Encrypt DNS challenges are also possible.\n\nOwn domains can be added and benefit from all IPv64.net features like DynDNS services, GEO load balancing, DDoS protection, DynDNS2 and SSL encryption.",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "Contact",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "IPv64.net - A product of Prox IT UG (limited liability)\n\n" +
                                        "Information according to Section § 5 DDG\n" +
                                        "Prox IT UG (limited liability)\n" +
                                        "Am Eisenstein 10\n" +
                                        "45470 Mülheim an der Ruhr\n\n" +
                                        "Represented by\n" +
                                        "Dennis Schröder (CEO)\n\n" +
                                        "Registry court: Amtsgericht Duisburg\n" +
                                        "Register number: HRB 35106\n" +
                                        "Ust-IdNr.: DE350434683",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                stickyHeader {
                    Surface( // nimmt Theme-Hintergrund, hebt Text hervor
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = "About the App",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "This app was created with the help of the Raspberry Pi Cloud community. All rights reserved by Dennis Schröder.",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}