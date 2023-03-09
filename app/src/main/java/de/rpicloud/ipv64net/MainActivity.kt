package de.rpicloud.ipv64net

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    var listOfDomains: DomainResult = DomainResult()
    lateinit var domainAdapter: DomainAdapter

    override fun onResume() {
        super.onResume()
        val isRestart = getSharedBool("THEME_RESTART", "THEME_RESTART")
        if (isRestart) {
            navigation.menu.findItem(R.id.menu_domain).isChecked = true
            setSharedBool("THEME_RESTART", "THEME_RESTART", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val isPortrait = resources.getBoolean(R.bool.portrait_only)
        Functions().setOrientaiton(this, isPortrait)

        topAppBar.title = "Domain"//resources.getString(R.string.dashboard)

        val domainFragment = DomainFragment()
        supportFragmentManager.beginTransaction().replace(R.id.main_container, domainFragment, "domain").commit()

        val lastVersionCode = getSharedInt("LASTBUILDNUMBER", "LASTBUILDNUMBER")
        if (BuildConfig.VERSION_CODE != lastVersionCode) {
            val newFragment = WhatsNewDialogFragment()
            newFragment.show(supportFragmentManager, "whatsnewDialog")
            supportFragmentManager.executePendingTransactions()
            newFragment.setOnDismissListener {
                setSharedInt("LASTBUILDNUMBER", "LASTBUILDNUMBER", BuildConfig.VERSION_CODE)
            }
        }

        if (isPortrait) {
            navigation.setOnItemSelectedListener { item ->
                setSelectedView(item.itemId)
            }
        } else {
            /*navigation_rail.setOnItemSelectedListener { item ->
                setSelectedView(item.itemId)
            }*/
        }
        setSupportActionBar(topAppBar)
    }

    private fun setSelectedView(itemId: Int) = when (itemId) {
        R.id.menu_domain -> {
            val domainFragment = DomainFragment()
            topAppBar.title = "Domain"//resources.getString(R.string.dashboard)
            topAppBar.menu.clear()
            val isPortrait = resources.getBoolean(R.bool.portrait_only)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, domainFragment, "domain").commit()
            true
        }
        R.id.menu_account -> {
            val accountFragment = AccountFragment()
            topAppBar.title = "Account" //resources.getString(R.string.dashboard)
            topAppBar.menu.clear()
            /*when (calendarTypes) {
                CalendarTypes.Alle -> topAppBar.title = resources.getString(R.string.alle_termine)
                CalendarTypes.Training -> topAppBar.title = resources.getString(R.string.trainings_termine)
                CalendarTypes.Event -> topAppBar.title = resources.getString(R.string.event_termine)
                else -> topAppBar.title = resources.getString(R.string.wettkampf_termine)
            }
            topAppBar.inflateMenu(R.menu.filter_menu);*/
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, accountFragment, "account")
                .commit()
            // Respond to navigation item 2 click
            true
        }
        R.id.menu_healthcheck -> {
            val healthcheckFragment = HealthcheckFragment()
            topAppBar.title = "Healthcheck" //resources.getString(R.string.dashboard)
            topAppBar.menu.clear()
            /*when (calendarTypes) {
                CalendarTypes.Alle -> topAppBar.title = resources.getString(R.string.alle_termine)
                CalendarTypes.Training -> topAppBar.title = resources.getString(R.string.trainings_termine)
                CalendarTypes.Event -> topAppBar.title = resources.getString(R.string.event_termine)
                else -> topAppBar.title = resources.getString(R.string.wettkampf_termine)
            }
            topAppBar.inflateMenu(R.menu.filter_menu);*/
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, healthcheckFragment, "healthcheck")
                .commit()
            // Respond to navigation item 2 click
            true
        }
        R.id.menu_integrations -> {
            val integrationFragment = IntegrationFragment()
            topAppBar.title = "Integrationen" //resources.getString(R.string.dashboard)
            topAppBar.menu.clear()
            /*when (calendarTypes) {
                CalendarTypes.Alle -> topAppBar.title = resources.getString(R.string.alle_termine)
                CalendarTypes.Training -> topAppBar.title = resources.getString(R.string.trainings_termine)
                CalendarTypes.Event -> topAppBar.title = resources.getString(R.string.event_termine)
                else -> topAppBar.title = resources.getString(R.string.wettkampf_termine)
            }
            topAppBar.inflateMenu(R.menu.filter_menu);*/
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_container, integrationFragment, "integrations")
                .commit()
            // Respond to navigation item 2 click
            true
        }
        else -> false
    }
}