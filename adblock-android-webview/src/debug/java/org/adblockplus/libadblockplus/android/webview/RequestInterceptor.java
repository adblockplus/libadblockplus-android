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

package org.adblockplus.libadblockplus.android.webview;

import android.net.Uri;
import android.webkit.WebView;

import org.adblockplus.libadblockplus.Filter;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.AndroidBase64Processor;
import org.adblockplus.libadblockplus.util.Base64Exception;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import timber.log.Timber;

public class RequestInterceptor
{

  public static final String COMMAND_STRING_CLEAR = "CLEAR";
  public static final String COMMAND_STRING_REMOVE = "REMOVE";
  public static final String COMMAND_STRING_ADD = "ADD";
  public static final String COMMAND_STRING_INVALID_COMMAND = "INVALID_COMMAND";
  public static final String COMMAND_STRING_INVALID_PAYLOAD = "INVALID_PAYLOAD";
  public static final String COMMAND_STRING_ERROR_NO_ENGINE = "ERROR_NO_ENGINE";
  public static final String COMMAND_STRING_ERROR = "ERROR";
  public static final String COMMAND_STRING_OK = "OK";
  public static final String DEBUG_URL_HOSTNAME = "abp_filters";
  public static final String RESPONSE_ENCODING = "UTF-8";
  public static final String RESPONSE_MIME_TYPE = "text/plain";
  public static final String PAYLOAD_QUERY_PARAMETER_KEY = "base64";

  enum Command
  {
    ADD,
    REMOVE,
    CLEAR,
    INVALID_COMMAND,
    INVALID_PAYLOAD
  }


  static Boolean isBlockedByHandlingDebugURLQuery(final WebView view, final AdblockEngineProvider provider,
                                                  final Uri url)
  {
    Timber.d("debug handleURLQuery %s", url);
    if (url == null || url.getHost() == null)
    {
      return false;
    }

    if (url.getHost() == null || !url.getHost().equals(DEBUG_URL_HOSTNAME))
    {
      return false;
    }

    final String textCommandWithoutLeadingSlash = url.getPath().replace("/", "");
    Timber.d("debug handleURLQuery path %s", textCommandWithoutLeadingSlash);
    final String decodedPayload = decodePayload(url.getQueryParameter(PAYLOAD_QUERY_PARAMETER_KEY));
    final Command command = parseCommand(textCommandWithoutLeadingSlash, decodedPayload);
    final String response;
    switch (command)
    {
      case INVALID_COMMAND:
        response = COMMAND_STRING_INVALID_COMMAND;
        break;
      case INVALID_PAYLOAD:
        response = COMMAND_STRING_INVALID_PAYLOAD;
        break;
      default:
        response = executeCommand(provider, command, decodedPayload);
    }
    sendResponse(view, response);
    return true;
  }

  private static Command parseCommand(final String textCommand, final String payload)
  {
    switch (textCommand)
    {
      case COMMAND_STRING_CLEAR:
        return Command.CLEAR;
      case COMMAND_STRING_REMOVE:
        return payload == null || payload.isEmpty() ? Command.INVALID_PAYLOAD : Command.REMOVE;
      case COMMAND_STRING_ADD:
        return payload == null || payload.isEmpty() ? Command.INVALID_PAYLOAD : Command.ADD;
      default:
        return Command.INVALID_COMMAND;
    }
  }

  private static String decodePayload(final String payload)
  {
    if (payload != null && !payload.isEmpty())
    {
      final AndroidBase64Processor base64 = new AndroidBase64Processor();
      try
      {
        return new String(base64.decode(payload.getBytes()));
      }
      catch (final Base64Exception e)
      {
        return "";
      }
    }
    return "";
  }

  private static String executeCommand(final AdblockEngineProvider provider,
                                       final Command command, final String payload)
  {
    final Lock lock = provider.getReadEngineLock();
    lock.lock();

    try
    {
      // if dispose() was invoke, but the page is still loading then just let it go
      boolean isDisposed = false;
      if (provider.getCounter() == 0)
      {
        isDisposed = true;
      }
      else
      {
        lock.unlock();
        provider.waitForReady();
        lock.lock();
        if (provider.getCounter() == 0)
        {
          isDisposed = true;
        }
      }

      final AdblockEngine engine = provider.getEngine();
      if (isDisposed || engine == null)
      {
        return COMMAND_STRING_ERROR_NO_ENGINE;
      }

      return modifyFilters(engine.getFilterEngine(), command, payload);
    }
    finally
    {
      lock.unlock();
    }
  }

  private static String modifyFilters(final FilterEngine filterEngine, final Command command, final String payload)
  {
    if (command == Command.ADD || command == Command.REMOVE)
    {
      final boolean shouldBePresent = command == Command.ADD;
      final List<Filter> filtersToModify = new LinkedList<>();
      for (final String filter : payload.split("\\r?\\n"))
      {
        filtersToModify.add(filterEngine.getFilterFromText(filter));
      }

      for (final Filter filter : filtersToModify)
      {
        if (command == Command.ADD)
        {
          filterEngine.addFilter(filter);
        }
        else
        {
          filterEngine.removeFilter(filter);
        }
      }

      if (!checkModifications(filterEngine.getListedFilters(), filtersToModify, shouldBePresent))
      {
        return COMMAND_STRING_ERROR;
      }
    }

    if (command == Command.CLEAR)
    {
      for (final Filter filter : filterEngine.getListedFilters())
      {
        filterEngine.removeFilter(filter);
      }
      if (!filterEngine.getListedFilters().isEmpty())
      {
        return COMMAND_STRING_ERROR;
      }
    }
    return COMMAND_STRING_OK;
  }

  private static void sendResponse(final WebView view, final String text)
  {
    view.post(new Runnable()
    {
      @Override
      public void run()
      {
        view.loadData(text, RESPONSE_MIME_TYPE, RESPONSE_ENCODING);
      }
    });
  }

  private static boolean checkModifications(final List<Filter> modifiedList,
                                            final List<Filter> modifications, final Boolean expectedValue)
  {
    for (final Filter filter : modifiedList)
    {
      if (modifications.contains(filter) != expectedValue)
      {
        return false;
      }
    }
    return true;
  }
}
