package top.niunaijun.blackbox.core.system.pm;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import top.niunaijun.blackbox.entity.pm.ShopInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class EleNaposShopIdExtractorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractsRealShopNameFromDdShopInsteadOfLoginUsername() throws Exception {
        File sharedPrefs = temporaryFolder.newFolder("shared_prefs");
        write(sharedPrefs, "NAPOS_LTRACKER_SP.xml",
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"shopId\">1184657317</string>\n" +
                        "    <string name=\"user_id\">5329558971</string>\n" +
                        "    <string name=\"user_name\">luojia6688</string>\n" +
                        "</map>");
        write(sharedPrefs, "app_sp_config.xml",
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"switch_login_user_info\">" +
                        "[{&quot;shopName&quot;:&quot;luojia6688&quot;,&quot;username&quot;:&quot;luojia6688&quot;,&quot;userId&quot;:5329558971}]" +
                        "</string>\n" +
                        "    <string name=\"DD_SHOP\">" +
                        "{&quot;id&quot;:1184657317,&quot;name&quot;:&quot;罗家臭豆腐·长沙一绝(东瓜山店)&quot;}" +
                        "</string>\n" +
                        "</map>");

        ShopInfo info = new EleNaposShopIdExtractor().extractFromSharedPrefsDir(sharedPrefs);

        assertNotNull(info);
        assertEquals("1184657317", info.shopId);
        assertEquals("罗家臭豆腐·长沙一绝(东瓜山店)", info.shopName);
        assertEquals("ele", info.platform);
    }

    @Test
    public void ignoresAccountUsernameWhenNoTrustedShopObjectExists() throws Exception {
        File sharedPrefs = temporaryFolder.newFolder("shared_prefs");
        write(sharedPrefs, "NAPOS_LTRACKER_SP.xml",
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"shopId\">1184657317</string>\n" +
                        "    <string name=\"user_name\">luojia6688</string>\n" +
                        "</map>");
        write(sharedPrefs, "app_sp_config.xml",
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"switch_login_user_info\">" +
                        "[{&quot;shopName&quot;:&quot;luojia6688&quot;,&quot;username&quot;:&quot;luojia6688&quot;,&quot;userId&quot;:5329558971}]" +
                        "</string>\n" +
                        "</map>");
        write(sharedPrefs, "user_5329558971_rest_1184657317_sp_config.xml",
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map />");

        ShopInfo info = new EleNaposShopIdExtractor().extractFromSharedPrefsDir(sharedPrefs);

        assertNull(info);
    }

    private void write(File dir, String name, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(dir, name))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
