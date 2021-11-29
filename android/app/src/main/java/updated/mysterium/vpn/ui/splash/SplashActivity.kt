package updated.mysterium.vpn.ui.splash

import android.animation.Animator
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import network.mysterium.vpn.R
import network.mysterium.vpn.databinding.ActivitySplashBinding
import network.mysterium.vpn.databinding.PopUpRetryRegistrationBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.App
import updated.mysterium.vpn.analytics.AnalyticEvent
import updated.mysterium.vpn.analytics.mysterium.MysteriumAnalytic
import updated.mysterium.vpn.common.animation.OnAnimationCompletedListener
import updated.mysterium.vpn.common.network.NetworkUtil
import updated.mysterium.vpn.model.manual.connect.ConnectionState
import updated.mysterium.vpn.model.pushy.PushyTopic
import updated.mysterium.vpn.ui.balance.BalanceViewModel
import updated.mysterium.vpn.ui.base.AllNodesViewModel
import updated.mysterium.vpn.ui.base.BaseActivity
import updated.mysterium.vpn.ui.create.account.CreateAccountActivity
import updated.mysterium.vpn.ui.onboarding.OnboardingActivity
import updated.mysterium.vpn.ui.prepare.top.up.PrepareTopUpActivity
import updated.mysterium.vpn.ui.terms.TermsOfUseActivity
import updated.mysterium.vpn.ui.wallet.ExchangeRateViewModel

class SplashActivity : BaseActivity() {

    private companion object {
        const val TAG = "SplashActivity"
    }

    private lateinit var binding: ActivitySplashBinding
    private val balanceViewModel: BalanceViewModel by inject()
    private val viewModel: SplashViewModel by inject()
    private val allNodesViewModel: AllNodesViewModel by inject()
    private val exchangeRateViewModel: ExchangeRateViewModel by inject()
    private val analytic: MysteriumAnalytic by inject()
    private var isVpnPermissionGranted = false
    private var isLoadingStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDarkMode()
        ensureVpnServicePermission()
        configure()
        subscribeViewModel()
        setUpPushyNotifications()
    }

    override fun retryLoading() {
        if (isVpnPermissionGranted) {
            startLoading()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isVpnPermissionGranted) {
            startLoading()
        }
    }

    private fun configure() {
        binding.onceAnimationView.addAnimatorListener(object : OnAnimationCompletedListener() {

            override fun onAnimationEnd(animation: Animator?) {
                viewModel.animationLoaded()
                binding.onceAnimationView.visibility = View.GONE
                binding.onceAnimationView.cancelAnimation()
                binding.loopAnimationView.visibility = View.VISIBLE
                binding.loopAnimationView.playAnimation()
            }
        })
    }

    private fun subscribeViewModel() {
        viewModel.navigateForward.observe(this, {
            allNodesViewModel.launchProposalsPeriodically()
            exchangeRateViewModel.launchPeriodicallyExchangeRate()
            balanceViewModel.requestBalanceChange()
            establishConnectionListeners()
            analytic.trackEvent(AnalyticEvent.STARTUP.eventName)
        })
        viewModel.preloadFinished.observe(this, {
            viewModel.initRepository()
        })
        viewModel.nodeStartingError.observe(this, {
            wifiNetworkErrorPopUp()
        })

        lifecycleScope.launchWhenStarted {
            analytic.eventTracked.collect { event ->
                if (event == AnalyticEvent.STARTUP.eventName) {
                    navigateForward()
                }
            }
        }
    }

    private fun applyDarkMode() {
        when (viewModel.getUserSavedMode()) {
            null -> { // default system theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                delegate.applyDayNight()
            }
            true -> { // user choose dark theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                delegate.applyDayNight()
            }
            else -> { // user choose light theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                delegate.applyDayNight()
            }
        }
    }

    private fun setUpPushyNotifications() {
        pushyNotifications.register {
            val lastCurrency = viewModel.getLastCryptoCurrency()
            if (lastCurrency == null) {
                pushyNotifications.subscribe(PushyTopic.PAYMENT_FALSE)
            } else {
                pushyNotifications.unsubscribe(PushyTopic.PAYMENT_FALSE)
                pushyNotifications.subscribe(PushyTopic.PAYMENT_TRUE)
                pushyNotifications.subscribe(lastCurrency)
            }
        }
        pushyNotifications.listen()
    }

    private fun navigateForward() {
        when {
            !viewModel.isUserAlreadyLogin() -> {
                navigateToOnboarding()
            }
            !viewModel.isTermsAccepted() -> {
                navigateToTerms()
            }
            viewModel.isTopUpFlowShown() -> {
                checkRegistrationStatus()
            }
            viewModel.isAccountCreated() -> {
                navigateToTopUp()
            }
            viewModel.isTermsAccepted() -> {
                navigateToCreateAccount()
            }
        }
    }

    private fun checkRegistrationStatus() {
        viewModel.getIdentity().observe(this) {
            it.onSuccess { identity ->
                if (identity.registered) {
                    navigateToConnectionOrHome(isBackTransition = false)
                } else {
                    checkUpdatedBalance()
                }
            }
            it.onFailure { error ->
                val errorMessage = error.localizedMessage ?: error.toString()
                Log.e(TAG, errorMessage)
                detailedErrorPopUp(errorMessage) {
                    checkRegistrationStatus()
                }
            }
        }
    }

    private fun checkUpdatedBalance() {
        viewModel.forceBalanceUpdate().observe(this) {
            it.onSuccess { balanceResponse ->
                if (balanceResponse.balance > 0) {
                    registerAccount()
                } else {
                    checkFreeRegistration()
                }
            }
            it.onFailure { error ->
                val errorMessage = error.localizedMessage ?: error.toString()
                Log.e(TAG, errorMessage)
                detailedErrorPopUp(errorMessage) {
                    checkUpdatedBalance()
                }
            }
        }
    }

    private fun checkFreeRegistration() {
        viewModel.checkFreeRegistration().observe(this) {
            it.onSuccess { freeRegistration ->
                if (freeRegistration) {
                    registerAccount()
                } else {
                    navigateToTopUp()
                }
            }
            it.onFailure { error ->
                val errorMessage = error.localizedMessage ?: error.toString()
                Log.e(TAG, errorMessage)
                detailedErrorPopUp(errorMessage) {
                    checkFreeRegistration()
                }
            }
        }
    }

    private fun registerAccount() {
        viewModel.registerAccount().observe(this) {
            it.onSuccess {
                navigateToConnectionOrHome(isBackTransition = false)
            }
            it.onFailure { error ->
                Log.e(TAG, error.localizedMessage ?: error.toString())
                showRegistrationErrorPopUp()
            }
        }
    }

    private fun showRegistrationErrorPopUp() {
        val bindingPopUp = PopUpRetryRegistrationBinding.inflate(layoutInflater)
        val dialog = createPopUp(bindingPopUp.root, true)
        bindingPopUp.tryAgainButton.setOnClickListener {
            dialog.dismiss()
            viewModel.registerAccount()
        }
        bindingPopUp.cancelButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun ensureVpnServicePermission() {
        val vpnServiceIntent = VpnService.prepare(this)
        if (vpnServiceIntent == null) {
            isVpnPermissionGranted = true
            startLoading()
        } else {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    isVpnPermissionGranted = true
                    startLoading()
                } else {
                    showPermissionErrorToast()
                    finish()
                }
            }.launch(vpnServiceIntent)
        }
    }

    private fun showPermissionErrorToast() {
        Toast.makeText(
            this,
            getString(R.string.error_vpn_permission),
            Toast.LENGTH_LONG
        ).apply {
            setGravity(Gravity.CENTER, 0, 0)
        }.show()
    }

    private fun startLoading() {
        if (NetworkUtil.isNetworkAvailable(this)) {
            init()
        } else {
            if (
                connectionState == ConnectionState.CONNECTED ||
                connectionState == ConnectionState.ON_HOLD ||
                connectionState == ConnectionState.CONNECTING ||
                connectionState == ConnectionState.IP_NOT_CHANGED
            ) {
                init()
            } else {
                wifiNetworkErrorPopUp()
            }
        }
    }

    private fun init() {
        if (!isLoadingStarted) {
            isLoadingStarted = true
            val deferredMysteriumCoreService = App.getInstance(this).deferredMysteriumCoreService
            balanceViewModel.initDeferredNode(deferredMysteriumCoreService)
            viewModel.startLoading(deferredMysteriumCoreService)
        }
    }

    private fun navigateToTopUp() {
        val intent = Intent(this, PrepareTopUpActivity::class.java).apply {
            putExtra(PrepareTopUpActivity.IS_NEW_USER_KEY, viewModel.isNewUser())
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun navigateToTerms() {
        val intent = Intent(this, TermsOfUseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun navigateToCreateAccount() {
        val intent = Intent(this, CreateAccountActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
