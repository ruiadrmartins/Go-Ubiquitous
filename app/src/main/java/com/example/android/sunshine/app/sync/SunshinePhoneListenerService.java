package com.example.android.sunshine.app.sync;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshinePhoneListenerService extends WearableListenerService {

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    GoogleApiClient mApiClient;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if (messageEvent.getPath().equalsIgnoreCase("/init")) {
            notifyWatchFace();
        }
    }

    private void notifyWatchFace() {

        final int weatherId;
        final double high;
        final double low;

        final Context context = getBaseContext();

        String locationQuery = Utility.getPreferredLocation(context);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor!= null && cursor.moveToFirst()) {
            weatherId = cursor.getInt(INDEX_WEATHER_ID);
            high = cursor.getDouble(INDEX_MAX_TEMP);
            low = cursor.getDouble(INDEX_MIN_TEMP);

            mApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {

                        @Override
                        public void onConnected(@Nullable Bundle bundle) {
                            final String message = String.valueOf(weatherId) + ":" + Utility.formatTemperature(context, high) + ":" + Utility.formatTemperature(context, low);

                            new Thread( new Runnable() {
                                @Override
                                public void run() {
                                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mApiClient).await();
                                    for (Node node : nodes.getNodes()) {
                                        Wearable.MessageApi.sendMessage(mApiClient, node.getId(),
                                                "/weather-message", message.getBytes())
                                                .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                                                    @Override
                                                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {

                                                    }
                                                });
                                    }
                                }
                            }).start();
                        }

                        @Override
                        public void onConnectionSuspended(int i) {

                        }
                    })
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                        }
                    })
                    .build();

            mApiClient.connect();
            cursor.close();
        }
    }
}
