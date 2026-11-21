package com.kurdish.tv;

import android.app.Application;
import android.content.SharedPreferences;

public class CrashLogger extends Application {
  @Override public void onCreate() {
    super.onCreate();
    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
      try {
        StringBuilder report = new StringBuilder();
        report.append("Thread: ").append(thread.getName()).append("\n");
        report.append("Error: ").append(error.toString()).append("\n\n");
        for (StackTraceElement element : error.getStackTrace()) {
          report.append(" at ").append(element.toString()).append("\n");
        }
        Throwable cause = error.getCause();
        while (cause != null) {
          report.append("\nCaused by: ").append(cause.toString()).append("\n");
          for (StackTraceElement element : cause.getStackTrace()) {
            report.append(" at ").append(element.toString()).append("\n");
          }
          cause = cause.getCause();
        }
        getSharedPreferences("crash_report", MODE_PRIVATE).edit()
          .putString("last_crash", report.toString()).commit();
      } catch (Throwable ignored) {}
      android.os.Process.killProcess(android.os.Process.myPid());
      System.exit(10);
    });
  }
}