package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.example.data.database.AppDatabase
import com.example.data.repository.PhotoRepository
import com.example.ui.screens.SnapJudgeNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SnapJudgeViewModel
import com.example.ui.viewmodel.SnapJudgeViewModelFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val repository = PhotoRepository(
            context = applicationContext,
            analysisDao = database.analysisDao(),
            userProgressDao = database.userProgressDao(),
            moshi = moshi
        )
        val viewModelFactory = SnapJudgeViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[SnapJudgeViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SnapJudgeNavigation(navController = navController, viewModel = viewModel)
                }
            }
        }
    }
}
