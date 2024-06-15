package snow.api;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;
import snow.music.R;
import snow.music.store.Music;
import snow.music.store.MusicStore;

public class SearchOnlineActivity extends AppCompatActivity {

    private static final String TAG = "SearchOnlineActivity";

    private EditText etSearchQuery;
    private RecyclerView rvSongs;
    private MusicAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_online);

        etSearchQuery = findViewById(R.id.etSearchQuery);
        rvSongs = findViewById(R.id.rvSongs);
        rvSongs.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MusicAdapter();
        rvSongs.setAdapter(adapter);

        findViewById(R.id.btnSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchSongs(etSearchQuery.getText().toString().trim());
            }
        });

        adapter.setOnItemClickListener(new MusicAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Music song) {
                File targetFile = new File(getFilesDir(), song.getTitle() + ".mp3");
                downloadSong(song.getTitle(), song.getOnlineId(), targetFile);
            }
        });
    }

        private void searchSongs(String query) {
        new SearchSongsTask().execute(query);
    }

    private class SearchSongsTask extends AsyncTask<String, Void, List<Music>> {
        @Override
        protected List<Music> doInBackground(String... strings) {
            String query = strings[0];
            return ApiClient.searchSongs(query);
        }

        @Override
        protected void onPostExecute(List<Music> result) {
            if (result.isEmpty()) {
                Toast.makeText(SearchOnlineActivity.this, "No results found", Toast.LENGTH_LONG).show();
            } else {
                adapter.setSongs(result);
            }
        }
    }

    private void downloadSong(String songName, String songId, File targetFile) {
        Log.d(TAG, "Starting download for song: " + songName);
        Toast.makeText(SearchOnlineActivity.this, songName + " 开始下载", Toast.LENGTH_SHORT).show();
        ApiClient.downloadSong(songId, targetFile, new ApiClient.DownloadCallback() {
            @Override
            public void onDownloadSuccess(File file) {
                Log.d(TAG, "Download successful: " + file.getPath());
                // 延迟10秒后显示Toast消息
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SearchOnlineActivity.this, songName + " 已下载完成", Toast.LENGTH_SHORT).show();
                    }
                }, 8000); // 延迟10秒
            }

            @Override
            public void onDownloadFailed(Throwable t) {
                Log.e(TAG, "Download failed", t);
                Toast.makeText(SearchOnlineActivity.this, "下载失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void updateMusicInDatabase(String filePath, long songId) {
        MusicStore musicStore = MusicStore.getInstance();
        Music music = musicStore.getMusic(songId);
        if (music != null) {
            music.setUri(filePath);
            musicStore.putMusic(music);
            Log.d(TAG, "Music database updated with new file path: " + filePath);
        } else {
            Log.e(TAG, "Failed to find music in database for ID: " + songId);
        }
    }
}
