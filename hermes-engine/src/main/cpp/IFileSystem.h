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
 *
 */
#ifndef ADBLOCK_PLUS_IFILE_SYSTEM_H
#define ADBLOCK_PLUS_IFILE_SYSTEM_H

#include <cstdint>
#include <functional>
#include <istream>
#include <memory>
#include <string>
#include <vector>

namespace AdblockPlus
{
  /**
   * File system interface.
   */
  class IFileSystem
  {
  public:
    /**
     * Result of a stat operation, i.e. information about a file.
     */
    struct StatResult
    {
      StatResult()
      {
        exists = false;
        lastModified = 0;
      }

      /**
       * File exists.
       */
      bool exists;

      /**
       * POSIX time of the last modification.
       */
      int64_t lastModified;
    };

    virtual ~IFileSystem()
    {
    }

    /** Type for the buffer used for IO */
    typedef std::vector<uint8_t> IOBuffer;

    /**
     * Default callback type for asynchronous filesystem calls.
     * @param An error string. Empty is success.
     */
    typedef std::function<void(const std::string&)> Callback;

    /**
     * Callback type for the asynchronous Read call.
     * @param Output char array with file content.
     */
    typedef std::function<void(IOBuffer&&)> ReadCallback;

    /**
     * Reads from a file.
     * @param fileName File name.
     * @param doneCallback The function called on completion with the input
     *   data. If this function throws then the implementation should call
     *   `errorCallback`.
     * @param errorCallback The function called if an error occurred.
     */
    virtual void Read(const std::string& fileName,
                      const ReadCallback& doneCallback,
                      const Callback& errorCallback) const = 0;

    /**
     * Writes to a file.
     * @param fileName File name.
     * @param data The data to write.
     * @param callback The function called on completion.
     */
    virtual void
    Write(const std::string& fileName, const IOBuffer& data, const Callback& callback) = 0;

    /**
     * Moves a file (i.e. renames it).
     * @param fromFileName Current file name.
     * @param toFileName New file name.
     * @param callback The function called on completion.
     */
    virtual void Move(const std::string& fromFileName,
                      const std::string& toFileName,
                      const Callback& callback) = 0;

    /**
     * Removes a file.
     * @param fileName File name.
     * @param callback The function called on completion.
     */
    virtual void Remove(const std::string& fileName, const Callback& callback) = 0;

    /**
     * Callback type for the asynchronous Stat call.
     * @param the StatResult data.
     * @param an error string. Empty if no error.
     */
    typedef std::function<void(const StatResult&, const std::string&)> StatCallback;

    /**
     * Retrieves information about a file.
     * @param fileName File name.
     * @param callback The function called on completion.
     */
    virtual void Stat(const std::string& fileName, const StatCallback& callback) const = 0;
  };

  /**
   * Unique smart pointer to to a `IFileSystem` instance.
   */
  typedef std::unique_ptr<IFileSystem> FileSystemPtr;
}

#endif
