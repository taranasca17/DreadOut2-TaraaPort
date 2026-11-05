#include <jni.h>
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <elf.h>
#include <fstream>
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

    if (!file.is_open()) {
        return env->NewStringUTF("ERR|Gagal membuka file binari .so untuk inspeksi ELF");
    }

    std::streamsize fileSize = file.tellg();
    if (fileSize < static_cast<std::streamsize>(sizeof(Elf64_Ehdr))) {
        return env->NewStringUTF("ERR|Ukuran file .so terlalu kecil atau korup");
    }

    file.seekg(0, std::ios::beg);
    Elf64_Ehdr ehdr{};
    file.read(reinterpret_cast<char*>(&ehdr), sizeof(Elf64_Ehdr));

    // 1. Periksa Magic Number ELF (\x7F 'E' 'L' 'F')
    if (std::memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0) {
        return env->NewStringUTF("ERR|Bukan file binari ELF Linux/Android yang valid");
    }

    // 2. Pastikan 64-bit (ELFCLASS64) dan arsitektur ARM64 (EM_AARCH64 = 183)
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64 || ehdr.e_machine != EM_AARCH64) {
        return env->NewStringUTF("ERR|Driver bukan arsitektur ARM64 64-bit (aarch64)");
    }

    // 3. Pastikan bertipe Shared Object (.so / ET_DYN)
    if (ehdr.e_type != ET_DYN) {
        return env->NewStringUTF("ERR|File ELF bukan pustaka Shared Object (.so)");
    }

    // 4. Pindai tabel string simbol (.dynstr) untuk memverifikasi titik masuk driver Vulkan
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