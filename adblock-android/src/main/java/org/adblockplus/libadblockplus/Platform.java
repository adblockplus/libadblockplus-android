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

package org.adblockplus.libadblockplus;

import org.adblockplus.AppInfo;

public class Platform implements Disposable
{
  private final Disposer disposer = null;
  protected final long ptr = 0L;


  /**
   * If an interface parameter value is null then a default implementation is
   * chosen.
   * If basePath is null then paths are not resolved to a full path, thus
   * current working directory is used.
   *
   * @param logSystem  LogSystem concrete implementation
   * @param fileSystem FileSystem concrete implementation
   * @param httpClient HttpClient concrete implementation
   * @param basePath   base path for FileSystem (default C++ FileSystem implementation used)
   */
  public Platform(final LogSystem logSystem,
                  final FileSystem fileSystem,
                  final HttpClient httpClient,
                  final String basePath)
  {

  }

  protected Platform(final long ptr)
  {
  }

  public void setUpJsEngine(final AppInfo appInfo, final long v8IsolateProviderPtr)
  {
  }

  public void setUpJsEngine(final AppInfo appInfo)
  {
    setUpJsEngine(appInfo, 0L);
  }

  public JsEngine getJsEngine()
  {
    return null;
  }

  public void setUpFilterEngine(final IsAllowedConnectionCallback isSubscriptionDownloadAllowedCallback,
                                final boolean isFilterEngineEnabled)
  {
  }

  public FilterEngine getFilterEngine()
  {
    // Initially FilterEngine is not constructed when Platform is instantiated
    // and in addition FilterEngine is being created asynchronously, the call
    // of `ensureFilterEngine` causes a construction of FilterEngine if it's
    // not created yet and waits for it.
    return new FilterEngine(this.ptr);
  }

  @Override
  public void dispose()
  {
    this.disposer.dispose();
  }

  private static final class DisposeWrapper implements Disposable
  {
    private final long ptr;

    public DisposeWrapper(final long ptr)
    {
      this.ptr = ptr;
    }

    @Override
    public void dispose()
    {
    }
  }


}
