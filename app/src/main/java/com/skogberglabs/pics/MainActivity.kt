package com.skogberglabs.pics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.amazonaws.mobile.client.AWSMobileClient
import com.skogberglabs.pics.auth.Cognito
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private val client: AWSMobileClient get() = AWSMobileClient.getInstance()

    private lateinit var viewModel: MainActivityViewModel
    val app: PicsApp get() = application as PicsApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)
        Timber.i("onCreate ${app.settings.isPrivate}")
        val theme = if (app.settings.isPrivate) R.style.PrivateTheme else R.style.PublicTheme
        setTheme(theme)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            setupActionBarWithNavController(navController())
        }
        if (UserSettings.load(applicationContext).isPrivate) {
            Cognito.instance.signIn(this)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.i("onResume MainActivity")
        intent.data?.let { uri ->
            if ("myapp" == uri.scheme) {
                Timber.i("Handling auth intent response for uri $uri")
                client.handleAuthResponse(intent)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        setupActionBarWithNavController(navController())
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController().navigateUp()
    }

    private fun navController() = findNavController(R.id.nav_host_fragment)

}
