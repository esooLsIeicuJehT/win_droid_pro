#include <jni.h>
#include <string>
#include <cstring>
#include <cerrno>
#include <android/log.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>

#define LOG_TAG "UsbManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

constexpr int USB_WRITE_TIMEOUT_MS = 2000;
constexpr int SMALL_WRITE_THRESHOLD = 4096;
constexpr int SMALL_TRANSFER_THRESHOLD = 16384;

namespace {

bool validate_buffer(JNIEnv* env, jbyteArray buffer, jint length, bool allow_null_for_zero = false) {
    if (length < 0) {
        LOGE("Negative USB transfer length: %d", length);
        return false;
    }

    if (buffer == nullptr) {
        if (allow_null_for_zero && length == 0) return true;
        LOGE("USB transfer buffer is null");
        return false;
    }

    const jsize array_length = env->GetArrayLength(buffer);
    if (length > array_length) {
        LOGE("USB transfer length %d exceeds Java buffer length %d", length, array_length);
        return false;
    }
    return true;
}

bool validate_timeout(jint timeout) {
    if (timeout < 0) {
        LOGE("Negative USB timeout: %d", timeout);
        return false;
    }
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeOpenDevice(
        JNIEnv *env,
        jobject /* this */,
        jstring devicePath) {
    if (devicePath == nullptr) {
        LOGE("USB device path is null");
        return -1;
    }

    const char *path = env->GetStringUTFChars(devicePath, nullptr);
    if (path == nullptr) {
        LOGE("Unable to obtain USB device path");
        return -1;
    }

    LOGI("Opening USB device: %s", path);

    int fd = open(path, O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        LOGE("Failed to open USB device: %s", strerror(errno));
        env->ReleaseStringUTFChars(devicePath, path);
        return -1;
    }

    const int flags = fcntl(fd, F_GETFL);
    if (flags < 0 || fcntl(fd, F_SETFL, flags & ~O_NONBLOCK) < 0) {
        LOGE("Failed to configure USB device descriptor: %s", strerror(errno));
        close(fd);
        env->ReleaseStringUTFChars(devicePath, path);
        return -1;
    }

    env->ReleaseStringUTFChars(devicePath, path);
    LOGI("USB device opened successfully, fd: %d", fd);
    return fd;
}

JNIEXPORT void JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeCloseDevice(
        JNIEnv * /* env */,
        jobject /* this */,
        jint fd) {
    if (fd >= 0) {
        if (close(fd) != 0) {
            LOGE("Failed to close USB device fd %d: %s", fd, strerror(errno));
            return;
        }
        LOGI("USB device closed, fd: %d", fd);
    }
}

JNIEXPORT jint JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeReadDevice(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jbyteArray buffer,
        jint length) {
    if (fd < 0 || !validate_buffer(env, buffer, length)) {
        LOGE("Invalid USB read arguments");
        return -1;
    }
    if (length == 0) return 0;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLIN;

    int poll_result;
    do {
        poll_result = poll(&pfd, 1, -1);
    } while (poll_result < 0 && errno == EINTR);

    if (poll_result < 0) {
        LOGE("Poll failed: %s", strerror(errno));
        return -1;
    }
    if ((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
        LOGE("USB read poll returned error flags: 0x%x", pfd.revents);
        return -1;
    }

    jbyte *buf = env->GetByteArrayElements(buffer, nullptr);
    if (buf == nullptr) return -1;

    const ssize_t bytes_read = read(fd, buf, static_cast<size_t>(length));
    if (bytes_read < 0) {
        LOGE("Failed to read from USB device: %s", strerror(errno));
    }

    env->ReleaseByteArrayElements(buffer, buf, 0);
    return static_cast<jint>(bytes_read);
}

JNIEXPORT jint JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeWriteDevice(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jbyteArray buffer,
        jint length) {
    if (fd < 0 || !validate_buffer(env, buffer, length)) {
        LOGE("Invalid USB write arguments");
        return -1;
    }
    if (length == 0) return 0;

    struct pollfd pfd{};
    pfd.fd = fd;
    pfd.events = POLLOUT;

    int poll_ret;
    do {
        poll_ret = poll(&pfd, 1, USB_WRITE_TIMEOUT_MS);
    } while (poll_ret < 0 && errno == EINTR);

    if (poll_ret <= 0) {
        if (poll_ret == 0) {
            LOGE("Write timed out - device not ready");
        } else {
            LOGE("Poll failed: %s", strerror(errno));
        }
        return -1;
    }
    if ((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
        LOGE("USB write poll returned error flags: 0x%x", pfd.revents);
        return -1;
    }

    if (length <= SMALL_WRITE_THRESHOLD) {
        jbyte buf[SMALL_WRITE_THRESHOLD];
        env->GetByteArrayRegion(buffer, 0, length, buf);
        if (env->ExceptionCheck()) return -1;

        const ssize_t bytes_written = write(fd, buf, static_cast<size_t>(length));
        if (bytes_written < 0) {
            LOGE("Failed to write to USB device: %s", strerror(errno));
        }
        return static_cast<jint>(bytes_written);
    }

    jbyte *buf = env->GetByteArrayElements(buffer, nullptr);
    if (buf == nullptr) return -1;

    const ssize_t bytes_written = write(fd, buf, static_cast<size_t>(length));
    if (bytes_written < 0) {
        LOGE("Failed to write to USB device: %s", strerror(errno));
    }

    env->ReleaseByteArrayElements(buffer, buf, JNI_ABORT);
    return static_cast<jint>(bytes_written);
}

JNIEXPORT jint JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeControlTransfer(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jint requestType,
        jint request,
        jint value,
        jint index,
        jbyteArray buffer,
        jint length,
        jint timeout) {
    if (fd < 0 ||
        !validate_buffer(env, buffer, length, true) ||
        !validate_timeout(timeout)) {
        LOGE("Invalid USB control transfer arguments");
        return -1;
    }

    struct usbdevfs_ctrltransfer ctrl{};
    ctrl.bRequestType = static_cast<__u8>(requestType);
    ctrl.bRequest = static_cast<__u8>(request);
    ctrl.wValue = static_cast<__u16>(value);
    ctrl.wIndex = static_cast<__u16>(index);
    ctrl.wLength = static_cast<__u16>(length);
    ctrl.timeout = static_cast<unsigned int>(timeout);

    jbyte *buf = nullptr;
    if (buffer != nullptr) {
        buf = env->GetByteArrayElements(buffer, nullptr);
        if (buf == nullptr) return -1;
        ctrl.data = buf;
    }

    const int result = ioctl(fd, USBDEVFS_CONTROL, &ctrl);
    if (result < 0) {
        LOGE("Control transfer failed: %s", strerror(errno));
    }

    if (buf != nullptr) {
        const bool direction_in = (requestType & 0x80) != 0;
        env->ReleaseByteArrayElements(buffer, buf, direction_in ? 0 : JNI_ABORT);
    }

    return result;
}

JNIEXPORT jint JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeBulkTransfer(
        JNIEnv *env,
        jobject /* this */,
        jint fd,
        jint endpoint,
        jbyteArray buffer,
        jint length,
        jint timeout) {
    if (fd < 0 ||
        endpoint < 0 || endpoint > 0xff ||
        !validate_buffer(env, buffer, length) ||
        !validate_timeout(timeout)) {
        LOGE("Invalid USB bulk transfer arguments");
        return -1;
    }
    if (length == 0) return 0;

    struct usbdevfs_bulktransfer bulk{};
    bulk.ep = static_cast<unsigned int>(endpoint);
    bulk.len = static_cast<unsigned int>(length);
    bulk.timeout = static_cast<unsigned int>(timeout);

    if (length <= SMALL_TRANSFER_THRESHOLD) {
        jbyte buf[SMALL_TRANSFER_THRESHOLD];

        if ((endpoint & 0x80) == 0) {
            env->GetByteArrayRegion(buffer, 0, length, buf);
            if (env->ExceptionCheck()) return -1;
        }

        bulk.data = buf;
        const int result = ioctl(fd, USBDEVFS_BULK, &bulk);

        if (result < 0) {
            LOGE("Bulk transfer failed: %s", strerror(errno));
        } else if ((endpoint & 0x80) != 0) {
            if (result > length) {
                LOGE("Kernel returned invalid bulk transfer length: %d > %d", result, length);
                return -1;
            }
            env->SetByteArrayRegion(buffer, 0, result, buf);
            if (env->ExceptionCheck()) return -1;
        }
        return result;
    }

    jbyte *buf = env->GetByteArrayElements(buffer, nullptr);
    if (buf == nullptr) return -1;
    bulk.data = buf;

    const int result = ioctl(fd, USBDEVFS_BULK, &bulk);
    if (result < 0) {
        LOGE("Bulk transfer failed: %s", strerror(errno));
    }

    const int mode = ((endpoint & 0x80) == 0) ? JNI_ABORT : 0;
    env->ReleaseByteArrayElements(buffer, buf, mode);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeClaimInterface(
        JNIEnv * /* env */,
        jobject /* this */,
        jint fd,
        jint interfaceNumber) {
    if (fd < 0 || interfaceNumber < 0) {
        LOGE("Invalid interface claim arguments");
        return JNI_FALSE;
    }

    int interface_number = interfaceNumber;
    const int result = ioctl(fd, USBDEVFS_CLAIMINTERFACE, &interface_number);
    if (result < 0) {
        LOGE("Failed to claim interface %d: %s", interfaceNumber, strerror(errno));
        return JNI_FALSE;
    }

    LOGI("Interface %d claimed successfully", interfaceNumber);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_windroidpro_usb_NativeUsbManager_nativeReleaseInterface(
        JNIEnv * /* env */,
        jobject /* this */,
        jint fd,
        jint interfaceNumber) {
    if (fd < 0 || interfaceNumber < 0) {
        LOGE("Invalid interface release arguments");
        return JNI_FALSE;
    }

    int interface_number = interfaceNumber;
    const int result = ioctl(fd, USBDEVFS_RELEASEINTERFACE, &interface_number);
    if (result < 0) {
        LOGE("Failed to release interface %d: %s", interfaceNumber, strerror(errno));
        return JNI_FALSE;
    }

    LOGI("Interface %d released successfully", interfaceNumber);
    return JNI_TRUE;
}

} // extern "C"
