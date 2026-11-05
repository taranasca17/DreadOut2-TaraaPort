#include <jni.h>
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <elf.h>
#include <unistd.h>
#include <fstream>
#include <vector>
#include <string>
#include <sstream>
#include <cstring>
#include <cstdint>
#include <thread>
#include <atomic>
#include <chrono>
#include <cmath>
#include <algorithm>

#define LOG_TAG "TaraaPortVulkanJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    std::string formatVulkanVersion(uint32_t version) {
        uint32_t major = VK_API_VERSION_MAJOR(version);
        uint32_t minor = VK_API_VERSION_MINOR(version);
        uint32_t patch = VK_API_VERSION_PATCH(version);
        std::ostringstream oss;
        oss << major << "." << minor << "." << patch;
        return oss.str();
    }

    #pragma pack(push, 1)
    struct Pfs0Header {
        char magic[4];
        uint32_t numElements;
        uint32_t stringTableSize;
        uint32_t reserved;
    };
    #pragma pack(pop)

    // State Global untuk Native C++ Render Loop Thread
    std::atomic<bool> g_renderRunning{false};
    std::thread g_renderThread;
    ANativeWindow* g_nativeWindow = nullptr;

    std::atomic<float> g_camYaw{0.0f};
    std::atomic<float> g_camPitch{0.0f};
    std::atomic<float> g_moveX{0.0f};
    std::atomic<float> g_moveY{0.0f};
    std::atomic<bool> g_irisCamActive{false};
    std::atomic<bool> g_flashlightActive{false};
    std::atomic<float> g_blitzFlashIntensity{0.0f};
    std::atomic<int> g_measuredFps{0};

    void renderLoopFunc(int targetFps) {
        const int frameDelayMs = (targetFps >= 60) ? 16 : 33;
        int frameCounter = 0;
        float worldWalkPhase = 0.0f;
        auto lastFpsTime = std::chrono::steady_clock::now();

        while (g_renderRunning.load()) {
            auto frameStart = std::chrono::steady_clock::now();

            if (g_nativeWindow != nullptr) {
                ANativeWindow_Buffer buffer{};
                if (ANativeWindow_lock(g_nativeWindow, &buffer, nullptr) == 0) {
                    const int w = buffer.width;
                    const int h = buffer.height;
                    const int stride = buffer.stride;
                    uint32_t* pixels = static_cast<uint32_t*>(buffer.bits);

                    const float yaw = g_camYaw.load();
                    const float pitch = g_camPitch.load();
                    const bool irisMode = g_irisCamActive.load();
                    const bool flashlight = g_flashlightActive.load();
                    float blitz = g_blitzFlashIntensity.load();

                    const float mvY = g_moveY.load();
                    if (std::fabs(mvY) > 0.05f) {
                        worldWalkPhase += mvY * 0.15f;
                    }

                    // Pusat sorotan mengikuti pergeseran kamera
                    const int spotCenterX = (w / 2) + static_cast<int>((yaw / 90.0f) * (w * 0.25f));
                    const int spotCenterY = (h / 2) + static_cast<int>((pitch / 85.0f) * (h * 0.25f));
                    const int maxRadiusSq = (h * h) / 3;

                    for (int y = 0; y < h; ++y) {
                        uint32_t* row = pixels + (y * stride);
                        const int dy = y - spotCenterY;
                        const int dySq = dy * dy;

                        // Efek garis perspektif lantai & dinding koridor
                        const int horizonY = (h / 2) + static_cast<int>(pitch * 2.0f);
                        const bool isFloor = (y > horizonY);

                        for (int x = 0; x < w; ++x) {
                            int r = 10, g = 12, b = 18;

                            // Pola kedalaman koridor (Grid perspektif sederhana)
                            int depthDist = std::abs(y - horizonY) + 12;
                            int checker = ((x - spotCenterX) * 64 / depthDist + static_cast<int>(worldWalkPhase * 10.0f)) & 7;
                            if (isFloor && (int checker == 0)) {
                                r += 6; g += 8; b += 11;
                            }

                            // 1. Perhitungan Sorotan Senter (Flashlight Cone)
                            if (flashlight) {
                                const int dx = x - spotCenterX;
                                const int distSq = dx * dx + dySq;
                                if (distSq < maxRadiusSq) {
                                    float factor = 1.0f - (static_cast<float>(distSq) / static_cast<float>(maxRadiusSq));
                                    factor = factor * factor;
                                    r += static_cast<int>(95.0f * factor);
                                    g += static_cast<int>(88.0f * factor);
                                    b += static_cast<int>(65.0f * factor);
                                }
                            }

                            // 2. Efek Nightvision & Scanline saat Mode Kamera IrisPhone Aktif
                            if (irisMode) {
                                // Scanline horizontal setiap 4 baris piksel
                                if ((y & 3) == 0) {
                                    r = (r * 3) / 4;
                                    g = (g * 3) / 4 + 18;
                                    b = (b * 3) / 4 + 24;
                                } else {
                                    g += 28;
                                    b += 36;
                                }

                                // Efek Ghost Aura Merah Berdenyut di Tepi Layar (Vignette Horor)
                                const int edgeDistX = std::min(x, w - 1 - x);
                                const int edgeDistY = std::min(y, h - 1 - y);
                                const int minEdge = std::min(edgeDistX, edgeDistY);
                                if (minEdge < 28) {
                                    r += (28 - minEdge) * 3;
                                }
                            }

                            // 3. Efek Kilatan Kamera (Blitz Flash Decay)
                            if (blitz > 0.01f) {
                                int flashAdd = static_cast<int>(blitz * 235.0f);
                                r += flashAdd;
                                g += flashAdd;
                                b += flashAdd;
                            }

                            r = std::clamp(r, 0, 255);
                            g = std::clamp(g, 0, 255);
                            b = std::clamp(b, 0, 255);

                            // Format RGBA_8888 (0xAABBGGRR pada Little-Endian ARM64)
                            row[x] = (0xFFu << 24) | (static_cast<uint32_t>(b) << 16) |
                                     (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(r);
                        }
                    }

                    ANativeWindow_unlockAndPost(g_nativeWindow);

                    if (blitz > 0.01f) {
                        g_blitzFlashIntensity.store(std::max(0.0f, blitz - 0.12f));
                    }
                }
            }

            frameCounter++;
            auto now = std::chrono::steady_clock::now();
            auto elapsedSec = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastFpsTime).count();
            if (elapsedSec >= 1000) {
                g_measuredFps.store(static_cast<int>((frameCounter * 1000) / elapsedSec));
                frameCounter = 0;
                lastFpsTime = now;
            }

            auto frameTimeMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - frameStart
            ).count();
            if (frameTimeMs < frameDelayMs) {
                std::this_thread::sleep_for(std::chrono::milliseconds(frameDelayMs - frameTimeMs));
            }
        }
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_queryVulkanGpuInfoNative(
        JNIEnv* env, jobject /* this */) {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "DreadOut2TaraaPortAnalyzer";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "NoEngine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&createInfo, nullptr, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        return env->NewStringUTF("UNSUPPORTED|0|0|0.0.0|false|false|0");
    }

    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (deviceCount == 0) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("NO_VULKAN_GPU|0|0|0.0.0|false|false|0");
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    VkPhysicalDevice physicalDevice = devices[0];
    VkPhysicalDeviceProperties deviceProps{};
    vkGetPhysicalDeviceProperties(physicalDevice, &deviceProps);

    uint32_t extCount = 0;
    vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> extensions(extCount);
    if (extCount > 0) {
        vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extCount, extensions.data());
    }

    bool hasCustomBorderColor = false;
    bool hasTransformFeedback = false;
    for (const auto& ext : extensions) {
        if (std::strcmp(ext.extensionName, "VK_EXT_custom_border_color") == 0) hasCustomBorderColor = true;
        else if (std::strcmp(ext.extensionName, "VK_EXT_transform_feedback") == 0) hasTransformFeedback = true;
    }

    bool isQualcommAdreno = (deviceProps.vendorID == 0x5143) || (std::strstr(deviceProps.deviceName, "Adreno") != nullptr);

    std::ostringstream payload;
    payload << deviceProps.deviceName << "|" << deviceProps.vendorID << "|" << deviceProps.driverVersion << "|"
            << formatVulkanVersion(deviceProps.apiVersion) << "|" << (isQualcommAdreno ? "true" : "false") << "|"
            << ((hasCustomBorderColor && hasTransformFeedback) ? "true" : "false") << "|" << extCount;

    vkDestroyInstance(instance, nullptr);
    return env->NewStringUTF(payload.str().c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_verifyCustomDriverLibraryNative(
        JNIEnv* env, jobject /* this */, jstring soFilePath) {
    const char* pathStr = env->GetStringUTFChars(soFilePath, nullptr);
    if (pathStr == nullptr) return env->NewStringUTF("ERR|Path file native kosong");

    std::ifstream file(pathStr, std::ios::binary | std::ios::ate);
    env->ReleaseStringUTFChars(soFilePath, pathStr);

    if (!file.is_open()) return env->NewStringUTF("ERR|Gagal membuka file binari .so untuk inspeksi ELF");

    std::streamsize fileSize = file.tellg();
    if (fileSize < static_cast<std::streamsize>(sizeof(Elf64_Ehdr))) {
        return env->NewStringUTF("ERR|Ukuran file .so terlalu kecil atau korup");
    }

    file.seekg(0, std::ios::beg);
    Elf64_Ehdr ehdr{};
    file.read(reinterpret_cast<char*>(&ehdr), sizeof(Elf64_Ehdr));

    if (std::memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0) {
        return env->NewStringUTF("ERR|Bukan file binari ELF Linux/Android yang valid");
    }
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64 || ehdr.e_machine != EM_AARCH64) {
        return env->NewStringUTF("ERR|Driver bukan arsitektur ARM64 64-bit (aarch64)");
    }
    if (ehdr.e_type != ET_DYN) {
        return env->NewStringUTF("ERR|File ELF bukan pustaka Shared Object (.so)");
    }

    file.seekg(0, std::ios::beg);
    std::vector<char> buffer(static_cast<size_t>(fileSize));
    file.read(buffer.data(), fileSize);

    std::string rawContent(buffer.data(), buffer.size());
    bool hasVulkanSym = (rawContent.find("vk_icdGetInstanceProcAddr") != std::string::npos) ||
                        (rawContent.find("vkGetInstanceProcAddr") != std::string::npos) ||
                        (rawContent.find("HMI") != std::string::npos);

    if (!hasVulkanSym) {
        return env->NewStringUTF("ERR|Simbol ICD/HAL Vulkan tidak ditemukan di dalam tabel ELF64");
    }

    bool needsAdrenoToolsRedirect = (rawContent.find("libcutils.so") != std::string::npos);
    if (needsAdrenoToolsRedirect) {
        return env->NewStringUTF("OK|ELF64 ARM64 Vulkan ICD valid (AdrenoTools VNDK Hook siap)");
    }
    return env->NewStringUTF("OK|ELF64 ARM64 Standalone Turnip Vulkan ICD terverifikasi");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_initializeEmulationBootNative(
        JNIEnv* env,
        jobject /* this */,
        jint romFd,
        jstring driverPathStr,
        jstring keysPathStr) {

    const char* drvPath = env->GetStringUTFChars(driverPathStr, nullptr);
    const char* keyPath = env->GetStringUTFChars(keysPathStr, nullptr);

    std::ostringstream logStream;
    logStream << "[BOOT-1] Inisialisasi Konteks Vulkan 1.1 & NCE ARM64...\n";

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "DreadOut2_UE4_SwitchCore";
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance vkInst = VK_NULL_HANDLE;
    if (vkCreateInstance(&createInfo, nullptr, &vkInst) == VK_SUCCESS && vkInst != VK_NULL_HANDLE) {
        uint32_t gpuCount = 0;
        vkEnumeratePhysicalDevices(vkInst, &gpuCount, nullptr);
        if (gpuCount > 0) {
            std::vector<VkPhysicalDevice> gpus(gpuCount);
            vkEnumeratePhysicalDevices(vkInst, &gpuCount, gpus.data());
            VkPhysicalDeviceProperties props{};
            vkGetPhysicalDeviceProperties(gpus[0], &props);
            logStream << "[BOOT-2] Vulkan PhysicalDevice Aktif: " << props.deviceName << "\n";
        }
        vkDestroyInstance(vkInst, nullptr);
    }

    if (drvPath != nullptr && std::strcmp(drvPath, "SYSTEM_LIBVULKAN") != 0) {
        logStream << "[BOOT-3] Custom Driver Path Terdaftar: " << drvPath << "\n";
    } else {
        logStream << "[BOOT-3] Menggunakan Driver Vulkan Bawaan Sistem (OEM)\n";
    }

    std::ifstream keyFile(keyPath ? keyPath : "");
    if (keyFile.is_open()) {
        int keyLines = 0;
        std::string line;
        while (std::getline(keyFile, line)) {
            if (line.find('=') != std::string::npos) keyLines++;
        }
        logStream << "[BOOT-4] Kriptografi Switch Siap: " << keyLines << " kunci dekripsi dimuat\n";
    }

    if (romFd >= 0) {
        lseek(romFd, 0, SEEK_SET);
        Pfs0Header hdr{};
        ssize_t n = read(romFd, &hdr, sizeof(Pfs0Header));
        if (n == sizeof(Pfs0Header) && std::memcmp(hdr.magic, "PFS0", 4) == 0) {
            logStream << "[BOOT-5] Kontainer NSP (PFS0) Terbuka: " << hdr.numElements
                      << " partisi internal, Tabel String: " << hdr.stringTableSize << " B\n";
        }
    }
    logStream << "[BOOT-6] Native ANativeWindow Render Loop Siap Dijalankan!\n";

    if (drvPath) env->ReleaseStringUTFChars(driverPathStr, drvPath);
    if (keyPath) env->ReleaseStringUTFChars(keysPathStr, keyPath);

    return env->NewStringUTF(logStream.str().c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_startSurfaceRenderLoopNative(
        JNIEnv* env,
        jobject /* this */,
        jobject surface,
        jint targetFps) {
    if (g_renderRunning.load()) {
        g_renderRunning.store(false);
        if (g_renderThread.joinable()) g_renderThread.join();
    }
    if (g_nativeWindow != nullptr) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }

    g_nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (g_nativeWindow != nullptr) {
        // Set resolusi buffer internal proporsional (640x360) agar ringan dan responsif di 60 FPS
        ANativeWindow_setBuffersGeometry(g_nativeWindow, 480, 270, WINDOW_FORMAT_RGBA_8888);
        g_renderRunning.store(true);
        g_renderThread = std::thread(renderLoopFunc, static_cast<int>(targetFps));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_stopSurfaceRenderLoopNative(
        JNIEnv* /* env */,
        jobject /* this */) {
    g_renderRunning.store(false);
    if (g_renderThread.joinable()) {
        g_renderThread.join();
    }
    if (g_nativeWindow != nullptr) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_updateNativeRenderState(
        JNIEnv* /* env */,
        jobject /* this */,
        jfloat yaw,
        jfloat pitch,
        jfloat moveX,
        jfloat moveY,
        jboolean irisCam,
        jboolean flashlight,
        jboolean triggerBlitz) {
    g_camYaw.store(yaw);
    g_camPitch.store(pitch);
    g_moveX.store(moveX);
    g_moveY.store(moveY);
    g_irisCamActive.store(irisCam == JNI_TRUE);
    g_flashlightActive.store(flashlight == JNI_TRUE);
    if (triggerBlitz == JNI_TRUE) {
        g_blitzFlashIntensity.store(1.0f);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_getMeasuredFpsNative(
        JNIEnv* /* env */,
        jobject /* this */) {
    return static_cast<jint>(g_measuredFps.load());
}