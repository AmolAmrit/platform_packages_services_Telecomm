/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 Ca* See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.telecomm.testcallservice;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.telecomm.CallAudioState;
import android.telecomm.CallInfo;
import android.telecomm.CallService;
import android.telecomm.CallServiceAdapter;
import android.telecomm.CallState;
import android.telephony.DisconnectCause;
import android.util.Log;

import com.android.telecomm.tests.R;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Service which provides fake calls to test the ICallService interface.
 * TODO(santoscordon): Rename all classes in the directory to Dummy* (e.g., DummyCallService).
 */
public class TestCallService extends CallService {
    private static final String TAG = TestCallService.class.getSimpleName();

    /**
     * Set of call IDs for live (active, ringing, dialing) calls.
     * TODO(santoscordon): Reference CallState javadoc when available for the different call states.
     */
    private Set<String> mLiveCallIds;

    /**
     * Used to play an audio tone during a call.
     */
    private MediaPlayer mMediaPlayer;

    /** {@inheritDoc} */
    @Override
    public void onAdapterAttached(CallServiceAdapter callServiceAdapter) {
        Log.i(TAG, "setCallServiceAdapter()");

        mLiveCallIds = Sets.newHashSet();

        mMediaPlayer = createMediaPlayer();
    }

    /**
     * Responds as compatible for all calls except those starting with the number 7 (arbitrarily
     * chosen for testing purposes).
     *
     * {@inheritDoc}
     */
    @Override
    public void isCompatibleWith(CallInfo callInfo) {
        Log.i(TAG, "isCompatibleWith(" + callInfo + ")");
        Preconditions.checkNotNull(callInfo.getHandle());

        // Is compatible if the handle doesn't start with 7.
        boolean isCompatible = !callInfo.getHandle().getSchemeSpecificPart().startsWith("7");

        // Tell CallsManager whether this call service can place the call (is compatible).
        // Returning positively on setCompatibleWith() doesn't guarantee that we will be chosen
        // to place the call. If we *are* chosen then CallsManager will execute the call()
        // method below.
        getAdapter().setIsCompatibleWith(callInfo.getId(), isCompatible);
    }

    /**
     * Starts a call by calling into the adapter. For testing purposes this methods acts as if a
     * call was successfully connected every time.
     *
     * {@inheritDoc}
     */
    @Override
    public void call(CallInfo callInfo) {
        String number = callInfo.getHandle().getSchemeSpecificPart();
        Log.i(TAG, "call(" + number + ")");

        // Crash on 555-DEAD to test call service crashing.
        if ("5550340".equals(number)) {
            throw new RuntimeException("Goodbye, cruel world.");
        }

        createCall(callInfo.getId());
        getAdapter().handleSuccessfulOutgoingCall(callInfo.getId());
    }

    /** {@inheritDoc} */
    @Override
    public void abort(String callId) {
        Log.i(TAG, "abort(" + callId + ")");
        destroyCall(callId);
    }

    /** {@inheritDoc} */
    @Override
    public void setIncomingCallId(String callId, Bundle extras) {
        Log.i(TAG, "setIncomingCallId(" + callId + ", " + extras + ")");

        // Use dummy number for testing incoming calls.
        Uri handle = Uri.fromParts("tel", "5551234", null);

        CallInfo callInfo = new CallInfo(callId, CallState.RINGING, handle);
        getAdapter().notifyIncomingCall(callInfo);
    }

    /** {@inheritDoc} */
    @Override
    public void answer(String callId) {
        getAdapter().setActive(callId);
        createCall(callId);
    }

    /** {@inheritDoc} */
    @Override
    public void reject(String callId) {
        getAdapter().setDisconnected(callId, DisconnectCause.INCOMING_REJECTED, null);
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect(String callId) {
        Log.i(TAG, "disconnect(" + callId + ")");

        destroyCall(callId);
        getAdapter().setDisconnected(callId, DisconnectCause.LOCAL, null);
    }

    /** {@inheritDoc} */
    @Override
    public void hold(String callId) {
        Log.i(TAG, "hold(" + callId + ")");
        getAdapter().setOnHold(callId);
    }

    /** {@inheritDoc} */
    @Override
    public void unhold(String callId) {
        Log.i(TAG, "unhold(" + callId + ")");
        getAdapter().setActive(callId);
    }

    /** {@inheritDoc} */
    @Override
    public void playDtmfTone(String callId, char digit) {
        Log.i(TAG, "playDtmfTone(" + callId + "," + digit + ")");
        // TODO(ihab): Implement
    }

    /** {@inheritDoc} */
    @Override
    public void stopDtmfTone(String callId) {
        Log.i(TAG, "stopDtmfTone(" + callId + ")");
        // TODO(ihab): Implement
    }

    /** {@inheritDoc} */
    @Override
    public void onAudioStateChanged(String callId, CallAudioState audioState) {
    }

    /** {@inheritDoc} */
    @Override
    public boolean onUnbind(Intent intent) {
        mMediaPlayer = null;

        return super.onUnbind(intent);
    }

    /**
     * Adds the specified call ID to the set of live call IDs and starts playing audio on the
     * voice-call stream.
     *
     * @param callId The identifier of the call to create.
     */
    private void createCall(String callId) {
        Preconditions.checkState(!Strings.isNullOrEmpty(callId));
        mLiveCallIds.add(callId);

        // Starts audio if not already started.
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    /**
     * Removes the specified call ID from the set of live call IDs and stops playing audio if
     * there exist no more live calls.
     *
     * @param callId The identifier of the call to destroy.
     */
    private void destroyCall(String callId) {
        Preconditions.checkState(!Strings.isNullOrEmpty(callId));
        mLiveCallIds.remove(callId);

        // Stops audio if there are no more calls.
        if (mLiveCallIds.isEmpty() && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = createMediaPlayer();
        }
    }

    private MediaPlayer createMediaPlayer() {
        // Prepare the media player to play a tone when there is a call.
        MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.beep_boop);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

}
