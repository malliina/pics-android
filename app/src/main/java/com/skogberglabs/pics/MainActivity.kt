package com.skogberglabs.pics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.amazonaws.mobile.client.AWSMobileClient
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private val client: AWSMobileClient get() = AWSMobileClient.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        if (savedInstanceState == null) {
            setupActionBarWithNavController(navController())
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
