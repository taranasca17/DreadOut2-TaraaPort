#include <jni.h>
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <dlfcn.h>
#include <vector>
#include <string>
#include <sstream>
#include <cstring>

#define LOG_TAG "TaraaPortVulkanJNI"
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
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_queryVulkanGpuInfoNative(
        JNIEnv* env,
        jobject /* this */) {

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
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        LOGE("Gagal menginisialisasi VkInstance: %d", result);
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
        if (std::strcmp(ext.extensionName, "VK_EXT_custom_border_color") == 0) {
            hasCustomBorderColor = true;
        } else if (std::strcmp(ext.extensionName, "VK_EXT_transform_feedback") == 0) {
            hasTransformFeedback = true;
        }
    }

    bool isQualcommAdreno = (deviceProps.vendorID == 0x5143) ||
                            (std::strstr(deviceProps.deviceName, "Adreno") != nullptr);

    std::ostringstream payload;
    payload << deviceProps.deviceName << "|"
            << deviceProps.vendorID << "|"
            << deviceProps.driverVersion << "|"
            << formatVulkanVersion(deviceProps.apiVersion) << "|"
            << (isQualcommAdreno ? "true" : "false") << "|"
            << ((hasCustomBorderColor && hasTransformFeedback) ? "true" : "false") << "|"
            << extCount;

    vkDestroyInstance(instance, nullptr);
    return env->NewStringUTF(payload.str().c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_taraaport_dreadout2_feature_analyzer_nativebridge_VulkanNativeBridge_verifyCustomDriverLibraryNative(
        JNIEnv* env,
        jobject /* this */,
        jstring soFilePath) {

    const char* pathStr = env->GetStringUTFChars(soFilePath, nullptr);
    if (pathStr == nullptr) {
        return env->NewStringUTF("ERR|Path file native kosong");
    }

    // Bersihkan error dlopen sebelumnya
    dlerror();

    // Coba muat library driver secara lokal untuk memvalidasi format ELF ARM64
    void* handle = dlopen(pathStr, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(soFilePath, pathStr);

    if (handle == nullptr) {
        const char* err = dlerror();
        std::string errMsg = err ? err : "Gagal memuat library .so (Bukan ELF ARM64 yang valid)";
        return env->NewStringUTF(("ERR|" + errMsg).c_str());
    }

    // Cari titik masuk standar driver Vulkan Android / ICD
    void* symStandard = dlsym(handle, "vkGetInstanceProcAddr");
    void* symIcd = dlsym(handle, "vk_icdGetInstanceProcAddr");
    void* symHal = dlsym(handle, "HMI"); // Simbol HAL modul Android (vulkan.adreno.so)

    dlclose(handle);

    if (symStandard != nullptr || symIcd != nullptr || symHal != nullptr) {
        return env->NewStringUTF("OK|Simbol ICD/HAL Vulkan ARM64 terverifikasi");
    } else {
        return env->NewStringUTF("ERR|File .so bukan driver Vulkan yang valid (Simbol ICD tidak ditemukan)");
    }
}