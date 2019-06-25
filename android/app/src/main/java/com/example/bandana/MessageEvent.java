/*
 * Copyright (C) IBR, TU Braunschweig & Ambient Intelligence, Aalto University
 * All Rights Reserved
 * Written by Caglar Yuce Kaya, Koirala Janaki, Dominik Schürmann
 */
package com.example.bandana;

public class MessageEvent {

    public final ProtocolState state;
    public final String value;

    public MessageEvent(ProtocolState state, String value) {
        this.state = state;
        this.value = value;
    }

    enum ProtocolState {
        BLUETOOTH_DISCOVER(R.string.state_bluetooth_discover, R.drawable.ic_bluetooth_black_48dp, R.drawable.ic_bluetooth_connect_black_48dp),
        BLUETOOTH_CONNECTED(R.string.state_connected, R.drawable.ic_walk_black_48dp, R.drawable.ic_walk2_black_48dp),
        WALKING(R.string.state_walking, R.drawable.ic_walk_black_48dp, R.drawable.ic_walk2_black_48dp),
        SECURE(R.string.state_secure, R.drawable.ic_lock_black_48dp, R.drawable.ic_lock_black_48dp),
        BLOCK(R.string.state_block, R.drawable.ic_block_helper_black_48dp, R.drawable.ic_block_helper_black_48dp),
        FAIL(R.string.state_fail, R.drawable.ic_block_helper_black_48dp, R.drawable.ic_block_helper_black_48dp),
        ERROR(R.string.state_error, R.drawable.ic_block_helper_black_48dp, R.drawable.ic_block_helper_black_48dp);

        public final int stringId;
        public final int drawableId1;
        public final int drawableId2;

        ProtocolState(int stringId, int drawableId1, int drawableId2) {
            this.stringId = stringId;
            this.drawableId1 = drawableId1;
            this.drawableId2 = drawableId2;
        }
    }
}