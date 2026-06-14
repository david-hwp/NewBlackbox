



#include "IO.h"
#include "Log.h"

jmethodID getAbsolutePathMethodId;

list<IO::RelocateInfo> relocate_rule;
static int s_deep_webview_redirect_log_count = 0;

char *replace(const char *str, const char *src, const char *dst) {
    const char *pos = str;
    int count = 0;
    while ((pos = strstr(pos, src))) {
        count++;
        pos += strlen(src);
    }

    size_t result_len = strlen(str) + (strlen(dst) - strlen(src)) * count + 1;
    char *result = (char *) malloc(result_len);
    memset(result, 0, result_len);

    const char *left = str;
    const char *right = nullptr;

    while ((right = strstr(left, src))) {
        strncat(result, left, right - left);
        strcat(result, dst);
        right += strlen(src);
        left = right;
    }
    strcat(result, left);
    return result;
}

static bool is_path_prefix_match(const char *path, const char *prefix) {
    if (path == nullptr || prefix == nullptr) {
        return false;
    }
    size_t prefix_len = strlen(prefix);
    if (prefix_len == 0 || strncmp(path, prefix, prefix_len) != 0) {
        return false;
    }
    char next = path[prefix_len];
    return next == '\0' || next == '/';
}

static char *replace_path_prefix(const char *path, const char *prefix, const char *replacement) {
    size_t prefix_len = strlen(prefix);
    size_t replacement_len = strlen(replacement);
    size_t suffix_len = strlen(path + prefix_len);
    size_t result_len = replacement_len + suffix_len + 1;
    char *result = (char *) malloc(result_len);
    memset(result, 0, result_len);
    strcat(result, replacement);
    strcat(result, path + prefix_len);
    return result;
}

static char *build_path_from_prefix_and_suffix(const char *path, size_t prefix_len,
                                               const char *suffix) {
    size_t suffix_len = strlen(suffix);
    size_t result_len = prefix_len + suffix_len + 1;
    char *result = (char *) malloc(result_len);
    memset(result, 0, result_len);
    strncat(result, path, prefix_len);
    strcat(result, suffix);
    return result;
}

static char *build_cache_path_from_prefix_and_suffix(const char *path, size_t prefix_len,
                                                     const char *suffix) {
    static const char *cache = "/cache";
    size_t cache_len = strlen(cache);
    size_t suffix_len = strlen(suffix);
    size_t result_len = prefix_len + cache_len + suffix_len + 1;
    char *result = (char *) malloc(result_len);
    memset(result, 0, result_len);
    strncat(result, path, prefix_len);
    strcat(result, cache);
    strcat(result, suffix);
    return result;
}

static const char *find_webview_cache_suffix(const char *path) {
    const char *patterns[] = {
            "/cache/webview_",
            "/cache/WebView_",
            "/cache/org.chromium.android_webview"
    };
    for (const char *pattern: patterns) {
        const char *match = strstr(path, pattern);
        if (match != nullptr) {
            return match;
        }
    }
    return nullptr;
}

static char *redirect_virtual_webview_path(const char *path) {
    if (path == nullptr) {
        return nullptr;
    }
    const char *blackbox = strstr(path, "/blackbox/");
    if (blackbox == nullptr || blackbox == path) {
        return nullptr;
    }
    size_t host_root_len = blackbox - path;
    const char *app_webview = strstr(blackbox, "/app_webview");
    if (app_webview != nullptr) {
        return build_path_from_prefix_and_suffix(path, host_root_len, app_webview);
    }
    const char *chromium_cache = strstr(blackbox, "/org.chromium.android_webview");
    if (chromium_cache != nullptr) {
        return build_cache_path_from_prefix_and_suffix(path, host_root_len, chromium_cache);
    }
    const char *cache_webview = find_webview_cache_suffix(blackbox);
    if (cache_webview != nullptr) {
        return build_path_from_prefix_and_suffix(path, host_root_len, cache_webview);
    }
    return nullptr;
}

const char *IO::redirectPath(const char *__path) {
    if (__path == nullptr) {
        return __path;
    }
    
    if (strstr(__path, "resource-cache")) {
        ALOGD("Blocking resource-cache path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "@idmap")) {
        ALOGD("Blocking idmap path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui") && (strstr(__path, ".frro") || strstr(__path, "-accent-") || strstr(__path, "-dynamic-") || strstr(__path, "-neutral-"))) {
        ALOGD("Blocking systemui problematic path: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "data@resource-cache@")) {
        ALOGD("Blocking data@resource-cache@ pattern: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, ".frro")) {
        ALOGD("Blocking .frro file: %s", __path);
        return "/dev/null";
    }
    
    
    if (strstr(__path, "systemui")) {
        ALOGD("Blocking systemui path: %s", __path);
        return "/dev/null";
    }

    char *webview_redirect = redirect_virtual_webview_path(__path);
    if (webview_redirect != nullptr) {
        if (s_deep_webview_redirect_log_count < 30) {
            ALOGD("WebView deep path redirected: %s -> %s", __path, webview_redirect);
            s_deep_webview_redirect_log_count++;
        }
        return webview_redirect;
    }

    list<IO::RelocateInfo>::iterator iterator;
    for (iterator = relocate_rule.begin(); iterator != relocate_rule.end(); ++iterator) {
        IO::RelocateInfo info = *iterator;
        if (is_path_prefix_match(__path, info.targetPath) && !strstr(__path, "/blackbox/")) {
            char *ret = replace_path_prefix(__path, info.targetPath, info.relocatePath);
            
            return ret;
        }
    }
    return __path;
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {




    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {






    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    IO::RelocateInfo info{};
    info.targetPath = targetPath;
    info.relocatePath = relocatePath;
    relocate_rule.push_back(info);
}

void IO::init(JNIEnv *env) {
    jclass tmpFile = env->FindClass("java/io/File");
    getAbsolutePathMethodId = env->GetMethodID(tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
}
