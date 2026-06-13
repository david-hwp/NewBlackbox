package top.niunaijun.blackbox.core.system.pm;

public class DouyinLaikeShopIdExtractor extends JsonSnippetShopIdExtractor {

    private static final String TARGET_PACKAGE = "com.bytedance.ls.merchant";
    private static final String TAG = "DouyinLaikeShopIdExtractor";

    public DouyinLaikeShopIdExtractor() {
        super(
                TARGET_PACKAGE,
                "dylk",
                TAG,
                new String[]{
                        "files/keva/repo/lsm_account/lsm_account.blk",
                        "files/keva/repo/user_role_and_partner_account/user_role_and_partner_account.blk"
                },
                new String[]{
                        "life_account_id",
                        "root_life_account_id",
                        "account_id",
                        "key_account_id",
                        "poi_id"
                },
                new String[]{
                        "life_account_name",
                        "account_name",
                        "poiName",
                        "poi_name",
                        "shopName"
                }
        );
    }
}
