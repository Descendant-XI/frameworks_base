/*
 * Copyright (C) 2019 Descendant
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.util.Log;


public class ThumbUIManager {

    public static final String TAG = "ThumbUIManager";

    private static final String[] THUMBUI = {
        "org.descendant.UI.ThumbUI.common",
        /*"org.descendant.UI.ThumbUI.GContacts",
        "org.descendant.UI.ThumbUI.GDialer",
        "org.descendant.UI.ThumbUI.GMessages",
        "org.descendant.UI.ThumbUI.Youtube",*/
    };

    public static void control(IOverlayManager om, int userId, int setting) {
        switch (setting) {
            case 0: for (int i = 0; i < THUMBUI.length; i++) {
                        try {
                            om.setEnabled(THUMBUI[i], false, userId);
                        } catch(RemoteException e) {
                            Log.e(TAG,"control routine has failed: " + e);
                        }
                    }
                    break;
            case 1: for (int i = 0; i < THUMBUI.length; i++) {
                        try {
                            om.setEnabled(THUMBUI[i], true, userId);
                        } catch(RemoteException e) {
                            Log.e(TAG,"control routine has failed: " + e);
                        }
                    }
                    break;
        }
    }

}
