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
/*
 * --= TAKEN FROM LIBADBLOCKPLUS =--
 *
 * Commit: 8a490f9fc37e6724010a500801be359c46606a81
 * Changes:
 * * Removed Scheduler
 *
 */
#ifndef ADBLOCK_PLUS_DEFAULT_FILE_SYSTEM_H
#define ADBLOCK_PLUS_DEFAULT_FILE_SYSTEM_H

#include "IFileSystem.h"

#ifdef _WIN32
#define PATH_SEPARATOR '\\'
#else
#define PATH_SEPARATOR '/'
#endif

namespace AdblockPlus
{
  typedef IFileSystem::IOBuffer StringIoBuffer;

  /**
   * File system implementation that interacts directly with the operating
   * system's file system.
   * All paths are considered relative to the base path, or to the current
   * working directory if no base path is set (see `SetBasePath()`).
   */
  class DefaultFileSystemSync
  {
  public:
    explicit DefaultFileSystemSync(std::string  basePath);
    static IFileSystem::IOBuffer Read(const std::string& path) ;
    static void Write(const std::string& path, const IFileSystem::IOBuffer& data);
    static void Move(const std::string& fromPath, const std::string& toPath);
    static void Remove(const std::string& path);
    static IFileSystem::StatResult Stat(const std::string& path) ;
    std::string Resolve(const std::string& fileName) const;

  protected:
    std::string basePath;
  };

  class DefaultFileSystem : public IFileSystem
  {
  public:
    explicit DefaultFileSystem(std::unique_ptr<DefaultFileSystemSync> syncImpl);
    void Read(const std::string& fileName,
              const ReadCallback& doneCallback,
              const Callback& errorCallback) const override;
    void
    Write(const std::string& fileName, const IOBuffer& data, const Callback& callback) override;
    void Move(const std::string& fromFileName,
              const std::string& toFileName,
              const Callback& callback) override;
    void Remove(const std::string& fileName, const Callback& callback) override;
    void Stat(const std::string& fileName, const StatCallback& callback) const override;

  private:
    // Returns the absolute path to a file.
    std::string Resolve(const std::string& fileName) const;
    std::unique_ptr<DefaultFileSystemSync> syncImpl;
  };
}

#endif
