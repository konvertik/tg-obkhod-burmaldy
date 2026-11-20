package ru.mtproxymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ru.mtproxymanager.ui.AppViewModel
import ru.mtproxymanager.ui.MtProxyApp

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MtProxyApp(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}
