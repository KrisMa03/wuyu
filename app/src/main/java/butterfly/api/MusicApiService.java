package butterfly.api;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

// 定义接口
public interface MusicApiService {
    @GET("playlist/hot")
    Call<Object> getDailyHotSongs();

    @GET("search")  // 添加新的搜索接口
    Call<Map<String, Object>> searchSongs(@Query("keywords") String keywords, @Query("type") int type);

    @GET("song/download/url")  // 添加下载歌曲的接口
    Call<ResponseBody> downloadSong(@Query("id") String songId);
}
