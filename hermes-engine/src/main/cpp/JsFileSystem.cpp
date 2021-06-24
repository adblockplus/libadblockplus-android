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

#include <hermes/hermes.h>

#include <memory>
#include "JsFileSystem.h"

using namespace facebook::hermes;
using namespace AdblockPlus;

#include "DefaultFileSystem.h"

namespace
{
  void throwJsIfNotAString(Runtime &rt, const Value *pArg, const char *message)
  {
    if (!pArg->isString())
    {
      throw JSError(rt, message);
    }
  }

  void throwJsIfNotAFunction(Runtime &rt, const Value *pArg, const char *message)
  {
    if (!pArg->isObject() || !pArg->asObject(rt).isFunction(rt))
    {
      throw JSError(rt, message);
    }
  }
}

namespace ReadFromFileCallback
{
  inline bool IsEndOfLine(char c)
  {
    return c == 10 || c == 13;
  }

  inline StringIoBuffer::const_iterator SkipEndOfLine(StringIoBuffer::const_iterator ii,
                                                      StringIoBuffer::const_iterator end)
  {
    while (ii != end && IsEndOfLine(*ii))
    {
      ++ii;
    }
    return ii;
  }

  inline StringIoBuffer::const_iterator AdvanceToEndOfLine(StringIoBuffer::const_iterator ii,
                                                           StringIoBuffer::const_iterator end)
  {
    while (ii != end && !IsEndOfLine(*ii))
    {
      ++ii;
    }
    return ii;
  }

  bool ReadCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
  {
    if (count != 4)
    {
      throw JSError(rt, "__fileSystem_readFromFile requires 4 parameters");
    }

    throwJsIfNotAString(rt, &args[0], "First argument to __fileSystem_readFromFile"
                                      " must be a string (file path)");
    throwJsIfNotAFunction(rt, &args[1], "Second argument to __fileSystem_readFromFile"
                                        " must be a function (listener callback)");
    throwJsIfNotAFunction(rt, &args[2], "Third argument to __fileSystem_readFromFile"
                                        " must be a function (done callback)");
    throwJsIfNotAFunction(rt, &args[3], "Forth argument to __fileSystem_readFromFile"
                                        " must be a function (error callback)");

    const std::string filename = args[0].getString(rt).utf8(rt);
    auto listenerFunc = args[1].asObject(rt).asFunction(rt);
    auto resolveFunc = args[2].asObject(rt).asFunction(rt);
    auto rejectFunc = args[3].asObject(rt).asFunction(rt);

    // we create an instance of FileSystem every single time just for the sake of simplicity
    // later we'd like to store it in the Engine instance
    auto fs = DefaultFileSystem(std::make_unique<DefaultFileSystemSync>(""));
    fs.Read(filename,
            [&rt, &listenerFunc, &resolveFunc](IFileSystem::IOBuffer &&content)
            {
              const auto contentEnd = content.cend();
              auto stringBegin = SkipEndOfLine(content.begin(), contentEnd);
              do
              {
                auto stringEnd = AdvanceToEndOfLine(stringBegin, contentEnd);
                const IFileSystem::IOBuffer &buffer = StringIoBuffer(stringBegin, stringEnd);
                const auto jsLine = String::createFromUtf8(rt, buffer.data(), buffer.size());

                listenerFunc.call(rt, jsLine);

                stringBegin = SkipEndOfLine(stringEnd, contentEnd);
              } while (stringBegin != contentEnd);
              resolveFunc.call(rt);
            },
            [&rt, &rejectFunc](const std::string &error)
            {
              if (!error.empty())
              {
                rejectFunc.call(rt, String::createFromUtf8(rt, error));
              }
            });
    return true;
  } // ReadCallback
}   // namespace ReadFromFileCallback

bool WriteCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
{
  if (count != 3)
  {
    throw JSError(rt, "__fileSystem_writeToFile requires 3 parameters");
  }

  throwJsIfNotAString(rt, &args[0], "First argument to __fileSystem_writeToFile"
                                    " must be a string (file path)");
  throwJsIfNotAString(rt, &args[1], "Second argument to __fileSystem_writeToFile"
                                    " must be a string (file content)");
  throwJsIfNotAFunction(rt, &args[2], "Third argument to __fileSystem_writeToFile"
                                      " must be a function (error callback)");

  const std::string filename = args[0].getString(rt).utf8(rt);
  const std::string content = args[1].getString(rt).utf8(rt);
  auto contentBuffer = IFileSystem::IOBuffer(content.begin(), content.begin() + content.length());

  auto callback = args[2].asObject(rt).asFunction(rt);

  // we create an instance of FileSystem every single time just for the sake of simplicity
  // later we'd like to store it in the Engine instance
  auto fs = DefaultFileSystem(std::make_unique<DefaultFileSystemSync>(""));
  fs.Write(filename, contentBuffer,
           [&rt, &callback](const std::string &error)
           {
             if (!error.empty())
             {
               callback.call(rt, String::createFromUtf8(rt, error));
               return;
             }
             // we must call the callback anyways, just with error empty
             callback.call(rt);
           });
  return true;
} // WriteCallback

bool MoveCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
{
  if (count != 3)
  {
    throw JSError(rt, "__fileSystem_moveFile requires 3 parameters");
  }

  throwJsIfNotAString(rt, &args[0], "First argument to __fileSystem_writeToFile"
                                    " must be a string (from file path)");
  throwJsIfNotAString(rt, &args[1], "Second argument to __fileSystem_writeToFile"
                                    " must be a string (to file path)");
  throwJsIfNotAFunction(rt, &args[2], "Third argument to __fileSystem_moveFile"
                                      " must be a function");

  const std::string from = args[0].getString(rt).utf8(rt);
  const std::string to = args[1].getString(rt).utf8(rt);
  auto callback = args[2].asObject(rt).asFunction(rt);

  // we create an instance of FileSystem every single time just for the sake of simplicity
  // later we'd like to store it in the Engine instance
  // passing `basePath` empty for the same reason
  auto fs = DefaultFileSystem(std::make_unique<DefaultFileSystemSync>(""));
  fs.Move(
          from, to, [&rt, &callback](const std::string &error)
          {
            if (!error.empty())
            {
              callback.call(rt, String::createFromUtf8(rt, error));
              return;
            }
            // we must call the callback anyways, just with error empty
            callback.call(rt);
          });
  return true;
} // MoveCallback

bool StatCallback(Runtime &rt, const Value &thisVal, const Value *args, size_t count)
{
  if (count != 2)
  {
    throw JSError(rt, "__fileSystem_statFile requires 2 parameters");
  }

  throwJsIfNotAString(rt, &args[0], "First argument to __fileSystem_statFile"
                                    " must be a string (file path)");
  throwJsIfNotAFunction(rt, &args[1], "Second argument to __fileSystem_statFile"
                                      " must be a function");

  const std::string filename = args[0].getString(rt).utf8(rt);
  auto callback = args[1].asObject(rt).asFunction(rt);

  auto fs = DefaultFileSystem(std::make_unique<DefaultFileSystemSync>(""));
  fs.Stat(
          filename,
          [&rt, &callback](const IFileSystem::StatResult &statResult,
                           const std::string &error)
          {
            auto result = Object(rt);

            result.setProperty(rt, "exists", statResult.exists);
            result.setProperty(rt, "lastModified",
                    // hermes does not know how to chew `uint64`,
                    // can take `double` instead
                               static_cast<double>(statResult.lastModified));
            if (!error.empty())
              result.setProperty(rt, "error", error);

            callback.call(rt, result);
          });
  return true;
}

void JsFileSystem::Setup(Runtime *pRuntime)
{
  const std::string fileSystemPrefix = "__fileSystem_";

  // ----------------------------------- readFromFile ---------------------------------------
  const PropNameID readFromFileId = PropNameID::forAscii(*pRuntime,
                                                         fileSystemPrefix + "readFromFile");
  auto jsReadFromFile = Function::createFromHostFunction(*pRuntime,
                                                         readFromFileId,
                                                         4,
                                                         ReadFromFileCallback::ReadCallback);
  pRuntime->global().setProperty(*pRuntime, readFromFileId, jsReadFromFile);

  // ----------------------------------- writeToFile ---------------------------------------
  const PropNameID writeToFileId = PropNameID::forAscii(*pRuntime,
                                                        fileSystemPrefix + "writeToFile");
  auto jsWriteToFile = Function::createFromHostFunction(*pRuntime,
                                                        writeToFileId,
                                                        3,
                                                        WriteCallback);
  pRuntime->global().setProperty(*pRuntime, writeToFileId, jsWriteToFile);

  // ----------------------------------- moveFile ----------------------------------------
  const PropNameID moveFileId = PropNameID::forAscii(*pRuntime,
                                                     fileSystemPrefix + "moveFile");
  auto jsMoveFile = Function::createFromHostFunction(*pRuntime,
                                                     moveFileId,
                                                     3,
                                                     MoveCallback);
  pRuntime->global().setProperty(*pRuntime, moveFileId, jsMoveFile);

  // ------------------------------------ statFile -----------------------------------------
  const PropNameID statFileId = PropNameID::forAscii(*pRuntime,
                                                     fileSystemPrefix + "statFile");
  auto jsStatFile = Function::createFromHostFunction(*pRuntime,
                                                     statFileId,
                                                     2,
                                                     StatCallback);
  pRuntime->global().setProperty(*pRuntime, statFileId, jsStatFile);
}
