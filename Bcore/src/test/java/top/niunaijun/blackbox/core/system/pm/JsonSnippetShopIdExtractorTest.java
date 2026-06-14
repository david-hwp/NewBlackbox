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

public class JsonSnippetShopIdExtractorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractsXmlEscapedNestedJsonFromEleRetailSettings() throws Exception {
        File settings = temporaryFolder.newFile("settings.xml");
        write(settings,
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"shop_info\">" +
                        "{&quot;merchantId&quot;:&quot;1343115377&quot;,&quot;shopId&quot;:&quot;1343115377&quot;,&quot;shopName&quot;:&quot;花果山水果&quot;}" +
                        "</string>\n" +
                        "</map>");

        ShopInfo info = new TestExtractor(
                new String[]{"settings.xml"},
                new String[]{"shopId", "merchantId"},
                new String[]{"shopName", "merchantName"}
        ).extractFromDirectory(temporaryFolder.getRoot());

        assertNotNull(info);
        assertEquals("1343115377", info.shopId);
        assertEquals("花果山水果", info.shopName);
        assertEquals("test", info.platform);
    }

    @Test
    public void extractsCipsKeyValuePairFromMeituanMerchantShopInfo() throws Exception {
        File kv = temporaryFolder.newFile("kv");
        write(kv,
                "\u0000s\ndp_shop_id\u0012s\u00101018925781348473" +
                        "s\tshop_name\u0011s\u000f启程台球厅" +
                        "s\u0010shop_branch_name\u0002s\u0000");

        ShopInfo info = new TestExtractor(
                new String[]{"kv"},
                new String[]{"mt_shop_id", "dp_shop_id", "shopId"},
                new String[]{"shop_name", "shopName", "showName"}
        ).extractFromDirectory(temporaryFolder.getRoot());

        assertNotNull(info);
        assertEquals("1018925781348473", info.shopId);
        assertEquals("启程台球厅", info.shopName);
        assertEquals("test", info.platform);
    }

    private void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class TestExtractor extends JsonSnippetShopIdExtractor {
        TestExtractor(String[] relativeFiles, String[] idKeys, String[] nameKeys) {
            super("test.package", "test", "TestExtractor", relativeFiles, idKeys, nameKeys);
            this.relativeFiles = relativeFiles;
        }

        ShopInfo extractFromDirectory(File directory) {
            for (String relativeFile : relativeFiles) {
                ShopInfo info = extractFromFile(new File(directory, relativeFile), relativeFile);
                if (info != null) {
                    return info;
                }
            }
            return null;
        }

        private final String[] relativeFiles;
    }
}
