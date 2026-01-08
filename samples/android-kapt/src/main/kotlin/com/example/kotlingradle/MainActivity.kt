package com.example.kotlingradle.kapt

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.databinding.ObservableField
import android.databinding.DataBindingUtil
import com.example.kotlingradle.kapt.databinding.ActivityMainBinding
import dagger.Component
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject
    internal lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerMainComponent.create().inject(this)
        super.onCreate(savedInstanceState)

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.model = viewModel
    }
}

class MainViewModel @Inject constructor() {
    private var counter = 0

    val title = ObservableField(formatTitle(counter))

    fun increment() {
        counter++
        title.set(formatTitle(counter))
    }

    private fun formatTitle(counter: Int) = "$counter clicks"
}

@Component
interface MainComponent {
    fun inject(activity: MainActivity)
}
