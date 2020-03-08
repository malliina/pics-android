package com.skogberglabs.pics

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.skogberglabs.pics.auth.Google
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    companion object {
        const val requestCodeSignIn = 111
    }

    private lateinit var viewModel: MainActivityViewModel
    val app: PicsApp get() = application as PicsApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)
        val isPrivate = app.settings.isPrivate
        Timber.i("onCreate privately $isPrivate")
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            setupActionBarWithNavController(navController())
        }
        viewModel.signInSilently(applicationContext)
        viewModel.mode.observe(this, Observer { mode ->
            Timber.i("Mode $mode")
            val colors = when (mode) {
                AppMode.Public -> AppColors(
                    statusBar = getColor(R.color.colorLightStatusBar),
                    navigationBar = getColor(R.color.colorLightNavigationBar),
                    background = getColor(R.color.colorLightBackground),
                    actionBar = getColor(R.color.colorLightActionBar)
                )
                AppMode.Private -> AppColors(
                    getColor(R.color.colorDarkStatusBar),
                    getColor(R.color.colorDarkNavigationBar),
                    getColor(R.color.colorDarkBackground),
                    getColor(R.color.colorDarkActionBar)
                )
            }
            window.statusBarColor = colors.statusBar
            window.navigationBarColor = colors.navigationBar
            supportActionBar?.setBackgroundDrawable(ColorDrawable(colors.actionBar))
            findViewById<View>(R.id.main_view).setBackgroundColor(colors.background)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Timber.i("Got activity result of request $requestCode. Result code $resultCode.")
        if (requestCode == requestCodeSignIn) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val user = account?.let { a -> Google.readUser(a) }
            Timber.i("Sign in success.")
            app.settings.isPrivate = true
            viewModel.updateUser(user)
        } catch (e: ApiException) {
            val str = CommonStatusCodes.getStatusCodeString(e.statusCode)
            Timber.w(e, "Sign in failed. Code ${e.statusCode}. $str.")
            viewModel.updateUser(null)
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
