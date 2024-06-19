package butterfly.api;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import butterfly.music.store.Music;

public class ApiClient {
    private static final String BASE_URL = "http://10.0.2.2:3000/";
    private static final String TAG = "ApiClient";

    private static Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

    public static List<Music> searchSongs(String keywords) {
        MusicApiService service = retrofit.create(MusicApiService.class);
        Call<Map<String, Object>> call = service.searchSongs(keywords, 1);
        try {
            Response<Map<String, Object>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                Log.d(TAG, "Response: " + response.body().toString());
                Map<String, Object> body = response.body();
                if (body.containsKey("result")) {
                    Map<String, Object> result = (Map<String, Object>) body.get("result");
                    if (result.containsKey("songs")) {
                        return parseSongs((List<Map<String, Object>>) result.get("songs"));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: ", e);
        }
        return new ArrayList<>();
    }

    public static List<Music> parseSongs(List<Map<String, Object>> songsData) {
        List<Music> songs = new ArrayList<>();
        for (Map<String, Object> songData : songsData) {
            Log.d(TAG, "Parsing song data: " + songData.toString());

// 直接使用字符串形式的ID
            String onlineId = songData.get("id").toString();
            Log.d(TAG, "Parsed online song ID: " + onlineId);
            long id = ((Number) songData.get("id")).longValue();
            String title = (String) songData.get("name");
            String artist = "";
            String album = "";
            String uri = "";
            String iconUri = "";
            int duration = ((Number) songData.get("duration")).intValue();
            long addTime = 0;
            String lyrics = "";

            // 解析 artists
            List<Map<String, Object>> artists = (List<Map<String, Object>>) songData.get("artists");
            if (artists != null && !artists.isEmpty()) {
                artist = (String) artists.get(0).get("name");
            }

            // 解析 album
            Map<String, Object> albumData = (Map<String, Object>) songData.get("album");
            if (albumData != null) {
                album = (String) albumData.get("name");
                iconUri = (String) albumData.get("img1v1Url");
            }

            // 数据库 ID 设为 0，表示尚未存储在本地数据库中
            Music music = new Music(id, title, artist, album, uri, iconUri, duration, addTime, lyrics, onlineId);
            songs.add(music);
        }
        return songs;
    }

    public static void downloadSong(String songId, File targetFile, DownloadCallback callback) {
        MusicApiService service = retrofit.create(MusicApiService.class);
        Log.d(TAG, "Starting download for song ID: " + songId + " to " + targetFile.getPath());
        Call<ResponseBody> call = service.downloadSong(songId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                Log.d(TAG, "Response code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        boolean success = saveToFile(response.body(), targetFile);
                        if (success) {
                            callback.onDownloadSuccess(targetFile);
                        } else {
                            callback.onDownloadFailed(new Exception("Failed to save file"));
                        }
                    } catch (IOException e) {
                        callback.onDownloadFailed(e);
                    }
                } else {
                    callback.onDownloadFailed(new Exception("Download failed with response code " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Download failed", t);
                callback.onDownloadFailed(t);
            }
        });
    }

    private static boolean saveToFile(ResponseBody body, File targetFile) throws IOException, FileNotFoundException {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = body.byteStream();
            outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            return true;
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        }
    }

    public interface DownloadCallback {
        void onDownloadSuccess(File file);
        void onDownloadFailed(Throwable t);
    }
}
