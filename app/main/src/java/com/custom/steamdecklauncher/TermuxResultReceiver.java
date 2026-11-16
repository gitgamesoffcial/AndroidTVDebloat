package com.custom.steamdeckphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class TermuxResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = intent == null ? null : intent.getBundleExtra(MainActivity.TERMUX_RESULT_BUNDLE);
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        if (result == null) {
            prefs.edit()
                    .putString(MainActivity.KEY_TERMUX_TOOLS_ERROR, "Termux returned no diagnostic result.")
                    .putString(MainActivity.KEY_TERMUX_TOOLS_OUTPUT, "")
                    .apply();
        } else {
            String outputValue = result.getString(MainActivity.TERMUX_RESULT_STDOUT);
            String errorOutputValue = result.getString(MainActivity.TERMUX_RESULT_STDERR);
            String errorMessageValue = result.getString(MainActivity.TERMUX_RESULT_ERROR_MESSAGE);
            String output = outputValue == null ? "" : outputValue.trim();
            String errorOutput = errorOutputValue == null ? "" : errorOutputValue.trim();
            String errorMessage = errorMessageValue == null ? "" : errorMessageValue.trim();
            int internalError = result.getInt(MainActivity.TERMUX_RESULT_ERROR, -1);
            int exitCode = result.getInt(MainActivity.TERMUX_RESULT_EXIT_CODE, -1);

            if (internalError != -1 || exitCode != 0 || !errorOutput.isEmpty() || !errorMessage.isEmpty()) {
                StringBuilder error = new StringBuilder();
                if (!errorMessage.isEmpty()) {
                    error.append(errorMessage);
                } else if (!errorOutput.isEmpty()) {
                    error.append(errorOutput);
                } else {
                    error.append("Diagnostic command failed (exit ").append(exitCode).append(").");
                }
                prefs.edit()
                        .putString(MainActivity.KEY_TERMUX_TOOLS_ERROR, error.toString())
                        .putString(MainActivity.KEY_TERMUX_TOOLS_OUTPUT, "")
                        .apply();
            } else {
                String resultText = output.isEmpty() ? "No allowlisted tools found." : output;
                prefs.edit()
                        .putString(MainActivity.KEY_TERMUX_TOOLS_ERROR, "")
                        .putString(MainActivity.KEY_TERMUX_TOOLS_OUTPUT, resultText)
                        .apply();
            }
        }

        context.sendBroadcast(new Intent(MainActivity.ACTION_TERMUX_RESULT)
                .setPackage(context.getPackageName()));
    }
}
