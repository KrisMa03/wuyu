package wuyu.player;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import wuyu.player.audio.MusicItemTest;
import wuyu.player.playlist.PlaylistTest;

@Suite.SuiteClasses({
        // snow.player
        PlayerStateTest.class,
        PersistentPlayerStateTest.class,
        PlayerConfigTest.class,
        // snow.player.media
        MusicItemTest.class,
        // snow.player.playlist
        PlaylistTest.class
})
@RunWith(Suite.class)
public class RunAllTest {
}
