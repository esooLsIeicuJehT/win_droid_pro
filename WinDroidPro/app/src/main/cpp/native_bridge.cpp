#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <strings.h>
#include <cerrno>
#include <memory>
#include <vector>
#include <cstdlib>
#include <malloc.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <map>
#include <mutex>
#include <sys/types.h>
#include <fcntl.h>
#include <sstream>

#define LOG_TAG "WinDroidPro-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {

std::vector<std::string> tokenize_args(const char* args) {
    std::vector<std::string> tokens;
    if (args == nullptr || *args == '\0') return tokens;

    const std::string str(args);
    std::string current_token;
    bool in_quote = false;
    char quote_char = 0;

    for (char c : str) {
        if (in_quote) {
            if (c == quote_char) {
                in_quote = false;
            } else {
                current_token += c;
            }
        } else if (c == '"' || c == '\'') {
            in_quote = true;
            quote_char = c;
        } else if (c == ' ' || c == '\t') {
            if (!current_token.empty()) {
                tokens.push_back(current_token);
                current_token.clear();
            }
        } else {
            current_token += c;
        }
    }

    if (in_quote) {
        LOGE("Unterminated quote in command arguments");
        return {};
    }

    if (!current_token.empty()) tokens.push_back(current_token);
    return tokens;
}

std::string find_executable_in_path(const char* executable) {
    if (executable == nullptr || *executable == '\0') return {};

    if (strchr(executable, '/') != nullptr) {
        return access(executable, X_OK) == 0 ? std::string(executable) : std::string();
    }

    const char* path_value = getenv("PATH");
    if (path_value == nullptr) return {};

    std::stringstream path_stream(path_value);
    std::string directory;
    while (std::getline(path_stream, directory, ':')) {
        if (directory.empty()) continue;
        std::string candidate = directory + "/" + executable;
        if (access(candidate.c_str(), X_OK) == 0) return candidate;
    }
    return {};
}

struct UsbDevice {
    int vendor_id;
    int product_id;
    int fd;
};

std::map<int, UsbDevice> g_usb_devices;
std::mutex g_usb_mutex;
int g_next_device_id = 1;
void* g_box64_handle = nullptr;

int wine_init(const char* wine_prefix, const char* wine_arch) {
    if (wine_prefix == nullptr || wine_arch == nullptr) return -EINVAL;
    if (strcmp(wine_arch, "win32") != 0 && strcmp(wine_arch, "win64") != 0) {
        LOGE("Unsupported Wine architecture: %s", wine_arch);
        return -EINVAL;
    }

    struct stat prefix_stat{};
    if (stat(wine_prefix, &prefix_stat) != 0 || !S_ISDIR(prefix_stat.st_mode)) {
        LOGE("Wine prefix is not an existing directory: %s", wine_prefix);
        return -ENOENT;
    }

    const std::string wine_executable = find_executable_in_path("wine");
    if (wine_executable.empty()) {
        LOGE("No executable Wine runtime is available on PATH");
        return -ENOENT;
    }

    if (setenv("WINEPREFIX", wine_prefix, 1) != 0 || setenv("WINEARCH", wine_arch, 1) != 0) {
        LOGE("Failed to configure Wine environment: %s", strerror(errno));
        return -errno;
    }

    LOGI("Wine runtime verified: %s", wine_executable.c_str());
    return 0;
}

int wine_execute(const char* exe_path, const char* args, const char* working_dir) {
    if (exe_path == nullptr || *exe_path == '\0') return -EINVAL;

    const std::string wine_executable = find_executable_in_path("wine");
    if (wine_executable.empty()) {
        LOGE("Cannot execute Windows application: Wine is not available on PATH");
        return 127;
    }

    const std::vector<std::string> tokens = tokenize_args(args);
    if (args != nullptr && *args != '\0' && tokens.empty()) {
        LOGE("Unable to parse command arguments");
        return -EINVAL;
    }

    std::vector<std::string> owned_arguments;
    owned_arguments.reserve(tokens.size() + 2);
    owned_arguments.push_back(wine_executable);
    owned_arguments.push_back(exe_path);
    owned_arguments.insert(owned_arguments.end(), tokens.begin(), tokens.end());

    std::vector<char*> argv;
    argv.reserve(owned_arguments.size() + 1);
    for (std::string& argument : owned_arguments) {
        argv.push_back(argument.data());
    }
    argv.push_back(nullptr);

    const pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        return -errno;
    }

    if (pid == 0) {
        if (working_dir != nullptr && *working_dir != '\0' && chdir(working_dir) != 0) {
            LOGE("Failed to change directory to %s: %s", working_dir, strerror(errno));
            _exit(126);
        }

        execv(wine_executable.c_str(), argv.data());
        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    }

    int status = 0;
    pid_t wait_result;
    do {
        wait_result = waitpid(pid, &status, 0);
    } while (wait_result < 0 && errno == EINTR);

    if (wait_result < 0) {
        LOGE("waitpid failed: %s", strerror(errno));
        return -errno;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -ECHILD;
}

void wine_cleanup() {
    unsetenv("WINEPREFIX");
    unsetenv("WINEARCH");
}

int box64_init(const char* lib_path) {
    if (lib_path == nullptr || *lib_path == '\0') return -EINVAL;
    if (g_box64_handle != nullptr) return 0;

    struct stat library_stat{};
    if (stat(lib_path, &library_stat) != 0 || !S_ISREG(library_stat.st_mode)) {
        LOGE("Box64 runtime library does not exist: %s", lib_path);
        return -ENOENT;
    }

    g_box64_handle = dlopen(lib_path, RTLD_NOW | RTLD_LOCAL);
    if (g_box64_handle == nullptr) {
        LOGE("Failed to load Box64 runtime library: %s", dlerror());
        return -ELIBBAD;
    }
    return 0;
}

void box64_cleanup() {
    if (g_box64_handle != nullptr) {
        dlclose(g_box64_handle);
        g_box64_handle = nullptr;
    }
}

int usb_init() {
    std::lock_guard<std::mutex> lock(g_usb_mutex);
    g_usb_devices.clear();
    g_next_device_id = 1;
    return 0;
}

int usb_attach_device(int vendor_id, int product_id, int fd) {
    if (vendor_id < 0 || vendor_id > 0xffff || product_id < 0 || product_id > 0xffff || fd < 0) {
        return -EINVAL;
    }

    const int owned_fd = dup(fd);
    if (owned_fd < 0) {
        LOGE("Failed to duplicate USB file descriptor: %s", strerror(errno));
        return -errno;
    }

    std::lock_guard<std::mutex> lock(g_usb_mutex);
    const int device_id = g_next_device_id++;
    g_usb_devices[device_id] = {vendor_id, product_id, owned_fd};
    return device_id;
}

int usb_detach_device(int device_id) {
    std::lock_guard<std::mutex> lock(g_usb_mutex);
    const auto it = g_usb_devices.find(device_id);
    if (it == g_usb_devices.end()) return -ENOENT;

    close(it->second.fd);
    g_usb_devices.erase(it);
    return 0;
}

void usb_cleanup() {
    std::lock_guard<std::mutex> lock(g_usb_mutex);
    for (const auto& item : g_usb_devices) {
        close(item.second.fd);
    }
    g_usb_devices.clear();
    g_next_device_id = 1;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeGetVersion(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("WinDroid Pro v1.0.0 - Native Bridge");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeInitializeWine(
    JNIEnv* env, jobject /* this */, jstring wine_prefix, jstring wine_arch) {
    if (wine_prefix == nullptr || wine_arch == nullptr) return JNI_FALSE;

    const char* prefix = env->GetStringUTFChars(wine_prefix, nullptr);
    const char* arch = env->GetStringUTFChars(wine_arch, nullptr);
    if (prefix == nullptr || arch == nullptr) {
        if (prefix != nullptr) env->ReleaseStringUTFChars(wine_prefix, prefix);
        if (arch != nullptr) env->ReleaseStringUTFChars(wine_arch, arch);
        return JNI_FALSE;
    }

    const int result = wine_init(prefix, arch);
    env->ReleaseStringUTFChars(wine_prefix, prefix);
    env->ReleaseStringUTFChars(wine_arch, arch);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeExecuteWineApp(
    JNIEnv* env, jobject /* this */, jstring exe_path, jstring args, jstring working_dir) {
    if (exe_path == nullptr || args == nullptr || working_dir == nullptr) return -EINVAL;

    const char* exe = env->GetStringUTFChars(exe_path, nullptr);
    const char* arguments = env->GetStringUTFChars(args, nullptr);
    const char* work_dir = env->GetStringUTFChars(working_dir, nullptr);
    if (exe == nullptr || arguments == nullptr || work_dir == nullptr) {
        if (exe != nullptr) env->ReleaseStringUTFChars(exe_path, exe);
        if (arguments != nullptr) env->ReleaseStringUTFChars(args, arguments);
        if (work_dir != nullptr) env->ReleaseStringUTFChars(working_dir, work_dir);
        return -ENOMEM;
    }

    const int result = wine_execute(exe, arguments, work_dir);
    env->ReleaseStringUTFChars(exe_path, exe);
    env->ReleaseStringUTFChars(args, arguments);
    env->ReleaseStringUTFChars(working_dir, work_dir);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeInitializeBox64(
    JNIEnv* env, jobject /* this */, jstring lib_path) {
    if (lib_path == nullptr) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(lib_path, nullptr);
    if (path == nullptr) return JNI_FALSE;
    const int result = box64_init(path);
    env->ReleaseStringUTFChars(lib_path, path);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeInitializeUSB(
    JNIEnv* /* env */, jobject /* this */) {
    return usb_init() == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeAttachUSBDevice(
    JNIEnv* /* env */, jobject /* this */, jint vendor_id, jint product_id, jint fd) {
    return usb_attach_device(vendor_id, product_id, fd) >= 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeDetachUSBDevice(
    JNIEnv* /* env */, jobject /* this */, jint device_id) {
    return usb_detach_device(device_id) == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeCleanup(
    JNIEnv* /* env */, jobject /* this */) {
    wine_cleanup();
    box64_cleanup();
    usb_cleanup();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeSetBox64Config(
    JNIEnv* env, jobject /* this */, jstring preset) {
    if (preset == nullptr) return JNI_FALSE;
    const char* preset_str = env->GetStringUTFChars(preset, nullptr);
    if (preset_str == nullptr) return JNI_FALSE;

    bool valid = true;
    if (strcasecmp(preset_str, "performance") == 0) {
        setenv("BOX64_DYNAREC", "1", 1);
        setenv("BOX64_DYNAREC_STRONGMEM", "1", 1);
        setenv("BOX64_DYNAREC_BIGBLOCK", "1", 1);
        setenv("BOX64_DYNAREC_FORWARD", "1024", 1);
        unsetenv("BOX64_DYNAREC_SAFE");
    } else if (strcasecmp(preset_str, "stability") == 0) {
        setenv("BOX64_DYNAREC", "1", 1);
        setenv("BOX64_DYNAREC_SAFE", "1", 1);
        setenv("BOX64_DYNAREC_BIGBLOCK", "0", 1);
        unsetenv("BOX64_DYNAREC_STRONGMEM");
        unsetenv("BOX64_DYNAREC_FORWARD");
    } else if (strcasecmp(preset_str, "balanced") == 0) {
        setenv("BOX64_DYNAREC", "1", 1);
        setenv("BOX64_DYNAREC_BIGBLOCK", "1", 1);
        unsetenv("BOX64_DYNAREC_SAFE");
        unsetenv("BOX64_DYNAREC_STRONGMEM");
        unsetenv("BOX64_DYNAREC_FORWARD");
    } else {
        valid = false;
    }

    env->ReleaseStringUTFChars(preset, preset_str);
    return valid ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_windroidpro_native_1bridge_NativeBridge_nativeOptimizeMemory(
    JNIEnv* /* env */, jobject /* this */) {
#ifdef M_TRIM_THRESHOLD
    mallopt(M_TRIM_THRESHOLD, -1);
    mallopt(M_MMAP_THRESHOLD, 128 * 1024);
#endif
    return JNI_TRUE;
}
