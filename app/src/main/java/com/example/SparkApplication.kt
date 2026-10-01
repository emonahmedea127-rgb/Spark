package com.example

import android.app.Application
import com.example.sociva.push.SparkPush

class SparkApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    SparkPush.initialize(this)
    SparkPush.refresh(this)
  }
}
