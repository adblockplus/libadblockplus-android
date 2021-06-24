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

function writeFileAsync(fileName, content)
{
  return new Promise((resolve, reject) =>
  {
    __fileSystem_writeToFile(fileName, content, (error) =>
    {
      if (error)
        return reject(error);
      resolve();
    });
  });
}

export let IO =
{
  /**
   * Reads text lines from a file.
   * @param {string} fileName
   *    Name of the file to be read
   * @param {TextSink} listener
   *    Function that will be called for each line in the file
   */
  readFromFile(fileName, listener)
  {
    return new Promise((resolve, reject) =>
    {
      __fileSystem_readFromFile(fileName, listener, resolve, reject);
    });
  },

  /**
   * Writes text lines to a file.
   * @param {string} fileName
   *    Name of the file to be written
   * @param {Iterable.<string>} data
   *    An array-like or iterable object containing the lines (without line
   *    endings)
   */
  writeToFile(fileName, data)
  {
    let content = Array.from(data).join(this.lineBreak) + this.lineBreak;
    return writeFileAsync(fileName, content);
  },

  /**
   * Renames a file.
   * @param {string} fromFile
   *    Name of the file to be renamed
   * @param {string} newName
   *    New file name, will be overwritten if exists
   */
  renameFile(fromFile, newName)
  {
    return new Promise((resolve, reject) =>
    {
      __fileSystem_moveFile(fromFileName, newNameFile, (error) =>
      {
        if (error)
          return reject(error);
        resolve();
      });
    });
  },

  /**
   * Retrieves file metadata.
   * @param {string} fileName
   *    Name of the file to be looked up
   * @return {StatData} file metadata
   */
  statFile(fileName)
  {
    return new Promise((resolve, reject) =>
    {
      __fileSystem_statFile(fileName, (result) =>
      {
        if (result.error)
          return reject(result.error);
        resolve(result);
      });
    });
  }
};
