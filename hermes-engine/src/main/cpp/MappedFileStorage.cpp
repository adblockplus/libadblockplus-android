/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "MappedFileStorage.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <memory>
#include <system_error>

namespace AdblockPlus
{

  MappedFileStorage::MappedFileStorage(const std::string& fileName)
  {
    fileDescriptor = open(fileName.c_str(), O_RDONLY);
    if (fileDescriptor < 0)
    {
      throw std::system_error(errno, std::system_category(), "Mapped file open failed");
    }

    struct stat buf;
    if (fstat(fileDescriptor, &buf) < 0)
    {
      throw std::system_error(errno, std::system_category(), "Mapped file fstat failed");
    }
    fileSize = buf.st_size;

    fileData = static_cast<uint8_t*>(
        mmap(
            /*address*/ nullptr,
                        fileSize,
                        PROT_READ,
                        MAP_PRIVATE,
                        fileDescriptor,
            /*offset*/ 0
        )
    );
    if (fileData == MAP_FAILED)
    {
      throw std::system_error(errno, std::system_category(), "Mapped file mmap failed");
    }
  }

  MappedFileStorage::~MappedFileStorage()
  {
    if (munmap(fileData, fileSize) < 0)
    {
      assert(false && "Failed to munmap MappedFileStorage");
    }
    if (close(fileDescriptor) < 0)
    {
      assert(false && "Failed to close MappedFileStorage");
    }
  }

} // namespace AdblockPlus
