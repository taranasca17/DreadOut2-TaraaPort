#pragma once
#include <string>
#include <cstdint>
namespace YuzuCore {
    struct RomPartitionInfo {
        bool isValid = false;
        std::string containerType;
        uint32_t fileCount = 0;
        std::string firstEntryName;
        uint64_t firstEntrySize = 0;
        std::string statusLog;
    };
    RomPartitionInfo InitializeAndInspectRom(int romFd, const std::string& keysPath, const std::string& driverPath);
}
