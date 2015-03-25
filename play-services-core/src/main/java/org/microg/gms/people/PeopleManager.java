/*
 * Copyright 2013-2015 µg Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.people;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.microg.gms.auth.AuthManager;
import org.microg.gms.auth.AuthRequest;
import org.microg.gms.auth.AuthResponse;
import org.microg.gms.common.Constants;
import org.microg.gms.common.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class PeopleManager {
    private static final String TAG = "GmsPeopleManager";
    public static final String USERINFO_SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.profile";
    public static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo";
    public static final String REGEX_SEARCH_USER_PHOTO = "https?\\:\\/\\/lh([0-9]*)\\.googleusercontent\\.com/";

    public static File getOwnerAvaterFile(Context context, String accountName, boolean network) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        Cursor cursor = databaseHelper.getOwner(accountName);
        String url = null;
        if (cursor.moveToNext()) {
            int idx = cursor.getColumnIndex("avatar");
            if (!cursor.isNull(idx)) url = cursor.getString(idx);
        }
        cursor.close();
        databaseHelper.close();
        if (url == null) return null;
        String urlLastPart = url.substring(3);
        File file = new File(context.getCacheDir(), urlLastPart);
        if (!file.getParentFile().mkdirs() && file.exists()) {
            return file;
        }
        if (!network) return null;
        url = "https://lh" + url.toCharArray()[1] + ".googleusercontent.com/" + urlLastPart;
        try {
            URLConnection conn = new URL(url).openConnection();
            conn.setDoInput(true);
            byte[] bytes = Utils.readStreamToEnd(conn.getInputStream());
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(bytes);
            outputStream.close();
            return file;
        } catch (Exception e) {
            Log.w(TAG, e);
            return null;
        }

    }

    public static Bitmap getOwnerAvatarBitmap(Context context, String accountName, boolean network) {
        File avaterFile = getOwnerAvaterFile(context, accountName, network);
        if (avaterFile == null) return null;
        return BitmapFactory.decodeFile(avaterFile.getPath());
    }

    public static void loadUserInfo(Context context, Account account) {
        try {
            URLConnection conn = new URL(USERINFO_URL).openConnection();
            conn.addRequestProperty("Authorization", "Bearer " + getUserInfoAuthKey(context, account));
            conn.setDoInput(true);
            byte[] bytes = Utils.readStreamToEnd(conn.getInputStream());
            JSONObject info = new JSONObject(new String(bytes));
            ContentValues contentValues = new ContentValues();
            contentValues.put("account_name", account.name);
            if (info.has("id")) contentValues.put("gaia_id", info.getString("id"));
            if (info.has("picture"))
                contentValues.put("avatar", info.getString("picture").replaceFirst(REGEX_SEARCH_USER_PHOTO, "~$1/"));
            if (info.has("name")) contentValues.put("display_name", info.getString("name"));
            DatabaseHelper databaseHelper = new DatabaseHelper(context);
            databaseHelper.putOwner(contentValues);
            databaseHelper.close();
        } catch (JSONException | IOException e) {
            Log.w(TAG, e);
        }
    }

    public static String getUserInfoAuthKey(Context context, Account account) {
        AuthManager authManager = new AuthManager(context, account.name, Constants.GMS_PACKAGE_NAME, USERINFO_SCOPE);
        authManager.setPermitted(true);
        String result = authManager.getAuthToken();
        if (result == null) {
            try {
                AuthResponse response = authManager.requestAuth(false);
                result = response.auth;
            } catch (IOException e) {
                Log.w(TAG, e);
                return null;
            }
        }
        return result;
    }
}
